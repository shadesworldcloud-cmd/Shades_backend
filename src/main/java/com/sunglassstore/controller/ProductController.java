package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.dto.response.ProductResponse;
import com.sunglassstore.service.ProductService;
import com.sunglassstore.service.ImageKitStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ImageKitStorageService imageStorageService;

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(Pageable pageable) {
        return ResponseEntity.ok(productService.getAllActiveProducts(pageable));
    }

    /**
     * Public Best Sellers. Declared before /{productId} for readability — Spring matches the
     * literal segment ahead of the template regardless, so "best-sellers" is never parsed as an id.
     *
     * `limit` is what the homepage carousel pages through; the service clamps it.
     */
    @GetMapping("/best-sellers")
    public ResponseEntity<java.util.List<com.sunglassstore.dto.response.BestSellerResponse>> getBestSellers(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(productService.getBestSellers(limit));
    }

    /**
     * Public resolution by slug — the storefront product page's only lookup.
     *
     * Under an explicit /slug/ segment rather than resolving at /api/products/{something} and
     * sniffing whether it looks numeric. That would make "best-sellers" and "search" ambiguous with
     * a product actually slugged that way, and ProductSlugs already refuses those words precisely
     * because guessing is a bad basis for routing.
     *
     * 404 for an unknown slug AND for an inactive product, with the same body, so the response
     * cannot be used to enumerate which slugs exist.
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponse> getProductBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    /**
     * The canonical slug for a legacy numeric id, for redirecting an already-shared /product/{id}
     * link. Returns only the slug — a bookmark from the old scheme should not be a way to read a
     * product's full record without going through the public endpoint above.
     *
     * 404 rather than a redirect for an unknown id: there is nothing to redirect to, and answering
     * differently for "id 999999 never existed" versus "id 3 exists but is a draft" would leak the
     * shape of the catalogue.
     */
    @GetMapping("/{productId}/canonical")
    public ResponseEntity<java.util.Map<String, String>> getCanonicalSlug(@PathVariable Long productId) {
        return ResponseEntity.ok(java.util.Map.of("slug", productService.findCanonicalSlug(productId)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductById(productId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(@RequestParam String keyword, Pageable pageable) {
        return ResponseEntity.ok(productService.searchProducts(keyword, pageable));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getByCategory(@PathVariable Long categoryId, Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long productId,
                                                  @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(productId, request));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<Page<ProductResponse>> getAllProductsAdmin(Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @PatchMapping("/{productId}/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> setProductActive(@PathVariable Long productId,
                                                             @RequestParam boolean active) {
        return ResponseEntity.ok(productService.setProductActive(productId, active));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // Variant endpoints
    @PostMapping("/{productId}/variants")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.VariantSummary> addVariant(@PathVariable Long productId,
                                                      @Valid @RequestBody CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addVariant(productId, request));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.VariantSummary> updateVariant(@PathVariable Long productId,
                                                         @PathVariable Long variantId,
                                                         @Valid @RequestBody CreateVariantRequest request) {
        return ResponseEntity.ok(productService.updateVariant(productId, variantId, request));
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        productService.deleteVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    /** Archive or restore one variant — the safe alternative to deleting something customers bought. */
    @PatchMapping("/{productId}/variants/{variantId}/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.VariantSummary> setVariantActive(
            @PathVariable Long productId, @PathVariable Long variantId, @RequestParam boolean active) {
        return ResponseEntity.ok(productService.setVariantActive(productId, variantId, active));
    }

    /**
     * The deliberate "Set as Main Variant" workflow. Its own endpoint, rather than an editable
     * position field, so replacing the family's Main Product is always an explicit act — never a
     * side effect of reordering a list.
     */
    @PutMapping("/{productId}/variants/{variantId}/main")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> setMainVariant(
            @PathVariable Long productId, @PathVariable Long variantId) {
        return ResponseEntity.ok(productService.setMainVariant(productId, variantId));
    }

    // Image endpoints
    @PostMapping(value = "/{productId}/images/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.ImageSummary> uploadImage(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "") String altText,
            @RequestParam(defaultValue = "0") Integer displayOrder,
            @RequestParam(defaultValue = "false") Boolean isPrimary) {
        ProductResponse product = productService.getProductById(productId);
        if (variantId != null && product.getVariants().stream().noneMatch(v -> variantId.equals(v.variantId()))) {
            throw new com.sunglassstore.exception.BadRequestException("Variant does not belong to this product");
        }
        // Refuse a photograph this product already holds.
        //
        // This is the root cause of a long-running storefront complaint. The create form has a
        // "Product photos" field and a per-colour field, and admins routinely picked the SAME file
        // for both — verified on the live catalogue, where four of six products had a general image
        // byte-identical to one colourway's. A general image is shown for every colour by design,
        // so that duplicate put a photo of the (often sold-out) first colourway into every other
        // colourway's gallery. Refusing the second copy stops the situation arising at all, and is
        // far better than a gallery rule trying to guess what a photograph depicts.
        String incomingHash = imageStorageService.hashOf(file);
        for (ProductResponse.ImageSummary existing : product.getImages()) {
            if (incomingHash.equals(imageStorageService.hashOfStored(existing.imageUrl()))) {
                throw new com.sunglassstore.exception.BadRequestException(
                        "This photo is already on the product. Use \"Shown for\" on the existing copy "
                                + "to choose which colours it appears for, rather than uploading it twice.");
            }
        }

        String imageUrl = imageStorageService.store(productId, variantId, file);
        CreateImageRequest request = new CreateImageRequest();
        request.setImageUrl(imageUrl);
        request.setAltText(altText);
        request.setDisplayOrder(displayOrder);
        request.setIsPrimary(isPrimary);
        // Carried as data now rather than being recovered from the storage path later. The check
        // above stays so an invalid variant is refused before a file is written; addImage validates
        // it again because it is also reachable from the JSON endpoint.
        request.setVariantId(variantId);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(productService.addImage(productId, request));
        } catch (RuntimeException ex) {
            imageStorageService.delete(imageUrl);
            throw ex;
        }
    }

    @PostMapping("/{productId}/images")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.ImageSummary> addImage(@PathVariable Long productId,
                                                  @Valid @RequestBody CreateImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addImage(productId, request));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId, @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Replace the gallery order. Takes the full list of this product's image ids, in the order they
     * should appear, rather than a "move image X to position N" instruction: two admins reordering
     * concurrently would otherwise interleave into an order neither of them chose, whereas a whole
     * list is last-writer-wins on a state both can see.
     */
    @PutMapping("/{productId}/images/order")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<java.util.List<ProductResponse.ImageSummary>> reorderImages(
            @PathVariable Long productId, @RequestBody java.util.List<Long> imageIdsInOrder) {
        return ResponseEntity.ok(productService.reorderImages(productId, imageIdsInOrder));
    }

    @PutMapping("/{productId}/images/{imageId}/primary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<java.util.List<ProductResponse.ImageSummary>> setPrimaryImage(
            @PathVariable Long productId, @PathVariable Long imageId) {
        return ResponseEntity.ok(productService.setPrimaryImage(productId, imageId));
    }

    /** Alt text and variant association. The file itself is immutable once uploaded. */
    @PatchMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.ImageSummary> updateImage(
            @PathVariable Long productId, @PathVariable Long imageId,
            @RequestBody CreateImageRequest request) {
        return ResponseEntity.ok(productService.updateImage(productId, imageId, request));
    }
}
