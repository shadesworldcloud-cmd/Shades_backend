package com.sunglassstore.service;

import com.sunglassstore.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

@Service
public class LocalImageStorageService {
    private static final int MAX_DIMENSION = 8_000;
    private static final long MAX_PIXELS = 40_000_000L;
    /**
     * The formats this pipeline can actually decode, which is the same list it will accept.
     *
     * WebP is deliberately NOT here. Every upload is validated by decoding its bytes and checking
     * that the real format matches the declared Content-Type — that check is the control that stops
     * an HTML or script file arriving as "image/png". This JDK registers no WebP reader
     * (ImageIO.getImageReadersByMIMEType("image/webp") is empty; readable suffixes are
     * tif/jpg/tiff/bmp/gif/png/wbmp/jpeg), so a WebP could be accepted by MIME type but never
     * validated by content. Listing it would mean either advertising a format that always fails
     * validation, or waiving the content check for exactly one format. Adding a decoder — e.g.
     * com.twelvemonkeys.imageio:imageio-webp — would make WebP genuinely supported, and is the one
     * change needed to enable it.
     *
     * SVG is absent for a different reason: it is a script-bearing document the browser executes in
     * this origin's context, and nothing here sanitises one.
     */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif");
    private final Path uploadRoot;
    private final String publicBaseUrl;

    public LocalImageStorageService(@Value("${app.upload.dir:uploads/products}") String uploadDir,
                                    @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public String store(Long productId, Long variantId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("An image file is required");
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) throw new BadRequestException("Only JPEG, PNG and GIF images are supported");
        try (InputStream validationStream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(validationStream)) {
            if (imageInput == null) throw new BadRequestException("The uploaded file is not a valid image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new BadRequestException("The uploaded file is not a valid image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException("Image dimensions are too large");
                }
                // The declared Content-Type must agree with what the decoder actually found. This is
                // the check that stops an HTML or script file being uploaded as "image/png": the
                // browser's claim is untrusted, the bytes are not.
                String format = reader.getFormatName().toLowerCase();
                boolean typeMatches = ("image/jpeg".equals(contentType) && ("jpeg".equals(format) || "jpg".equals(format)))
                        || ("image/png".equals(contentType) && "png".equals(format))
                        || ("image/gif".equals(contentType) && "gif".equals(format));
                if (!typeMatches || reader.read(0) == null) {
                    throw new BadRequestException("The uploaded file type does not match its image content");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded image could not be read");
        }
        String owner = variantId == null ? "product" : "variants/" + variantId;
        Path directory = uploadRoot.resolve(String.valueOf(productId)).resolve(owner).normalize();
        if (!directory.startsWith(uploadRoot)) throw new BadRequestException("Invalid upload path");
        String filename = UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BadRequestException("The image could not be stored");
        }
        return publicBaseUrl + "/uploads/products/" + productId + "/" + owner.replace('\\', '/') + "/" + filename;
    }

    /**
     * SHA-256 of an upload's bytes, for detecting that the same photograph is being stored twice.
     *
     * Content, not filename: every stored file is named with a fresh UUID, so two uploads of the
     * same picture have nothing in common except their bytes. This is what makes "is this photo
     * already on the product" answerable at all.
     */
    public String hashOf(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return hash(input);
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded image could not be read");
        }
    }

    /**
     * SHA-256 of an already-stored image, addressed by its public URL. Returns null when the file
     * is missing or the URL is not one of ours — a missing file must not fail an unrelated upload,
     * it just means we cannot prove a duplicate.
     */
    public String hashOfStored(String imageUrl) {
        String marker = "/uploads/products/";
        int index = imageUrl == null ? -1 : imageUrl.indexOf(marker);
        if (index < 0) return null;
        Path target = uploadRoot.resolve(imageUrl.substring(index + marker.length())).normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) return null;
        try (InputStream input = Files.newInputStream(target)) {
            return hash(input);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String hash(InputStream input) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read = input.read(buffer); read > 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS; unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public void delete(String imageUrl) {
        String marker = "/uploads/products/";
        int index = imageUrl == null ? -1 : imageUrl.indexOf(marker);
        if (index < 0) return;
        Path target = uploadRoot.resolve(imageUrl.substring(index + marker.length())).normalize();
        if (!target.startsWith(uploadRoot)) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { /* DB record is authoritative. */ }
    }

    public Path getUploadRoot() { return uploadRoot; }
}
