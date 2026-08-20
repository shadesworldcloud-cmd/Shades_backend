package com.sunglassstore.service;

import com.sunglassstore.exception.BadRequestException;
import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import io.imagekit.models.files.FileUploadParams;
import io.imagekit.models.files.FileUploadResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Stores product images on ImageKit CDN instead of the local filesystem.
 *
 * Drop-in replacement for {@link LocalImageStorageService}: the controller calls
 * {@link #store} and gets back a public CDN URL; {@link #delete} removes the file
 * from ImageKit's media library. The validation pipeline (content-type sniffing,
 * dimension limits, format-vs-header agreement) is identical to the local version.
 *
 * ImageKit file IDs are encoded into the returned URL after a {@code #} fragment so
 * that {@link #delete} can recover them without a separate column. The fragment is
 * invisible to browsers loading the image.
 */
@Service
public class ImageKitStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageKitStorageService.class);

    private static final int MAX_DIMENSION = 8_000;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif");

    @Value("${imagekit.private-key}")
    private String privateKey;

    @Value("${imagekit.public-key}")
    private String publicKey;

    @Value("${imagekit.url-endpoint}")
    private String urlEndpoint;

    private ImageKitClient client;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        client = ImageKitOkHttpClient.builder()
                .privateKey(privateKey)
                .build();
        log.info("ImageKit client initialised — endpoint {}", urlEndpoint);
    }

    /**
     * Validates the upload, sends it to ImageKit, and returns the public CDN URL.
     * The ImageKit fileId is appended as a URL fragment ({@code #ik=<fileId>}) so
     * that {@link #delete} can recover it later.
     */
    public String store(Long productId, Long variantId, MultipartFile file) {
        String folder = "/products/" + productId
                + (variantId == null ? "/product" : "/variants/" + variantId);
        return storeInFolder(folder, file);
    }

    /**
     * Uploads any image to an arbitrary ImageKit folder, with the identical validation pipeline.
     *
     * Extracted so the storefront hero image can reuse the content-type sniffing, the
     * format-vs-header agreement check and the dimension ceiling rather than growing a second,
     * inevitably weaker, upload path. {@link #store} is now a caller of this.
     */
    public String storeInFolder(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("An image file is required");
        }
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BadRequestException("Only JPEG, PNG and GIF images are supported");
        }

        validateImageContent(file, contentType);

        String filename = UUID.randomUUID() + extension;

        try {
            byte[] bytes = file.getBytes();
            FileUploadParams params = FileUploadParams.builder()
                    .file(bytes)
                    .fileName(filename)
                    .folder(folder)
                    .build();

            FileUploadResponse response = client.files().upload(params);
            String cdnUrl = response.url().orElseThrow(() ->
                    new BadRequestException("ImageKit upload succeeded but returned no URL"));
            String fileId = response.fileId().orElse("");

            log.info("Uploaded image to ImageKit: {} (fileId={})", cdnUrl, fileId);

            // Encode the fileId into the URL fragment so delete() can recover it.
            return cdnUrl + "#ik=" + fileId;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BadRequestException("The image could not be uploaded to ImageKit");
        } catch (Exception ex) {
            log.error("ImageKit upload failed", ex);
            throw new BadRequestException("Image upload failed: " + ex.getMessage());
        }
    }

    /**
     * Content-type vs actual-bytes validation — identical to the local service.
     */
    private void validateImageContent(MultipartFile file, String contentType) {
        try (InputStream validationStream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(validationStream)) {
            if (imageInput == null) {
                throw new BadRequestException("The uploaded file is not a valid image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BadRequestException("The uploaded file is not a valid image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException("Image dimensions are too large");
                }
                String format = reader.getFormatName().toLowerCase();
                boolean typeMatches =
                        ("image/jpeg".equals(contentType) && ("jpeg".equals(format) || "jpg".equals(format)))
                        || ("image/png".equals(contentType) && "png".equals(format))
                        || ("image/gif".equals(contentType) && "gif".equals(format));
                if (!typeMatches || reader.read(0) == null) {
                    throw new BadRequestException(
                            "The uploaded file type does not match its image content");
                }
            } finally {
                reader.dispose();
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded image could not be read");
        }
    }

    /**
     * SHA-256 of an upload's bytes, for duplicate detection.
     */
    public String hashOf(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return hash(input);
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded image could not be read");
        }
    }

    /**
     * SHA-256 of an already-stored image. For ImageKit-hosted images we cannot read the
     * bytes back without an HTTP fetch, so we return null — duplicate detection will rely
     * on the upload-time hash only for images that were uploaded before the migration.
     * New uploads go through {@link #hashOf} at upload time.
     */
    public String hashOfStored(String imageUrl) {
        return null;
    }

    /**
     * Deletes the image from ImageKit's media library using the REST API.
     * Uses the fileId encoded in the URL fragment. Silently succeeds if the URL has
     * no embedded fileId (e.g. a legacy local-storage URL from before the migration).
     */
    public void delete(String imageUrl) {
        String fileId = extractFileId(imageUrl);
        if (fileId == null || fileId.isEmpty()) {
            log.warn("No ImageKit fileId found in URL, skipping delete: {}", imageUrl);
            return;
        }
        try {
            // ImageKit REST API: DELETE https://api.imagekit.io/v1/files/:fileId
            // Auth: Basic base64(privateKey + ":")
            String credentials = Base64.getEncoder().encodeToString(
                    (privateKey + ":").getBytes(StandardCharsets.UTF_8));
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + credentials);

            ResponseEntity<Void> resp = restTemplate.exchange(
                    "https://api.imagekit.io/v1/files/" + fileId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);

            log.info("Deleted image from ImageKit: fileId={} (status={})", fileId, resp.getStatusCode());
        } catch (Exception ex) {
            // Log but don't fail — the DB record is authoritative.
            log.error("Failed to delete image from ImageKit (fileId={}): {}", fileId, ex.getMessage());
        }
    }

    /**
     * Extracts the ImageKit fileId from a URL fragment like {@code #ik=file_abc123}.
     */
    private String extractFileId(String imageUrl) {
        if (imageUrl == null) return null;
        int hashIndex = imageUrl.indexOf("#ik=");
        if (hashIndex < 0) return null;
        return imageUrl.substring(hashIndex + 4);
    }

    private static String hash(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read = input.read(buffer); read > 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
