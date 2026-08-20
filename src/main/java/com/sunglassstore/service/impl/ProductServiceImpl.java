package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.catalog.ProductSlugs;
import com.sunglassstore.dto.response.ProductResponse;
import com.sunglassstore.entity.*;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.FieldValidationException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.ProductService;
import com.sunglassstore.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ProductServiceImpl implements ProductService {

    private static final Set<String> STOREFRONT_CATEGORIES = Set.of("Men", "Women", "Unisex", "Accessory");

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final ProductAttributeRepository attributeRepository;
    private final CategoryRepository categoryRepository;
    private final com.sunglassstore.service.ImageKitStorageService imageStorageService;
    private final com.sunglassstore.service.StorefrontSettingsService storefrontSettingsService;
    private final InventoryService inventoryService;
    // Used by deleteProduct and deleteVariant, to clear the two NO ACTION references that would
    // otherwise make removing catalogue rows with history impossible.
    private final CartItemRepository cartItemRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    // Only asked "has this variant ever been ordered" — the question that decides whether a
    // variant may be deleted or must be archived.
    private final OrderItemRepository orderItemRepository;
    private final com.sunglassstore.catalog.NewProductPolicy newProductPolicy;

    /**
     * Ceiling on images per VARIANT: one main image plus up to nine additional, ten in all.
     * Per variant rather than per product because photography now belongs to variants — a
     * three-colour family legitimately holds thirty photographs. Configurable because "how many
     * photos is reasonable" is a merchandising decision, not an engineering one. Enforced on the
     * server, so a caller bypassing the admin UI cannot exceed it, and reported as a validation
     * message rather than by silently dropping the extra files.
     */
    @org.springframework.beans.factory.annotation.Value("${app.catalog.max-variant-images:10}")
    private int maxImagesPerVariant;

    /**
     * Every ProductResponse in the application is built here, so the New badge is decided in
     * exactly one place. ProductResponse.fromEntity takes the flag rather than computing it, which
     * is what stops a future call site from quietly shipping an always-false badge.
     */
    private ProductResponse toResponse(Product product) {
        return ProductResponse.fromEntity(product, newProductPolicy.isNew(product));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByIsActiveTrue(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    /** Ceiling on how much of the ranking one call may ask for. */
    private static final int MAX_BEST_SELLERS = 50;

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.sunglassstore.dto.response.BestSellerResponse> getBestSellers(int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_BEST_SELLERS);

        // An administrator-curated order, if one exists, replaces the sales ranking outright — the
        // whole point of curating is to decide the section rather than nudge it. An empty setting is
        // the default and leaves everything below untouched, which is why the existing ranking
        // behaviour (and the E2E suite that pins it down) is unaffected until someone curates.
        java.util.List<Long> curated = storefrontSettingsService.getCuratedBestSellerIds();
        if (!curated.isEmpty()) {
            return curatedBestSellers(curated, capped);
        }

        java.util.List<com.sunglassstore.catalog.BestSellerRow> ranked =
                productRepository.findBestSellers(capped);
        if (ranked.isEmpty()) {
            return java.util.List.of();
        }
        // Two queries in total, not one per product: the aggregate decides the ranking and this
        // fetches exactly the products it named. findAllById returns them in no particular order,
        // so the ranking order is reapplied from `ranked` below rather than taken from this list.
        java.util.Map<Long, Product> byId = productRepository
                .findAllById(ranked.stream().map(com.sunglassstore.catalog.BestSellerRow::getProductId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(Product::getProductId, product -> product));
        return ranked.stream()
                .filter(row -> byId.containsKey(row.getProductId()))
                .map(row -> new com.sunglassstore.dto.response.BestSellerResponse(
                        toResponse(byId.get(row.getProductId())),
                        row.getSoldQuantity() == null ? 0L : row.getSoldQuantity(),
                        row.getSoldRevenue()))
                .toList();
    }

    /**
     * The curated section: exactly the products the administrator pinned, in exactly their order.
     *
     * Inactive and deleted products are dropped rather than rendered. The curated order is a list of
     * ids in a CONFIG row, so it carries no foreign key — a product unpublished or deleted after
     * being pinned would otherwise surface as a broken card, or as a 404 the moment a shopper clicked
     * it. Dropping silently is right here: the section is decoration, and failing it would take the
     * home page down over a stale id.
     *
     * soldQuantity/soldRevenue are still reported so the response shape is identical to the ranked
     * one and the storefront needs no branch, but they are the real sales figures for the pinned
     * products, not invented ones — a curated product with no sales honestly reports zero.
     */
    private java.util.List<com.sunglassstore.dto.response.BestSellerResponse> curatedBestSellers(
            java.util.List<Long> curatedIds, int limit) {
        java.util.List<Long> wanted = curatedIds.stream().limit(limit).toList();
        Map<Long, Product> byId = productRepository.findAllById(wanted).stream()
                .filter(product -> Boolean.TRUE.equals(product.getIsActive()))
                .collect(java.util.stream.Collectors.toMap(Product::getProductId, product -> product));
        if (byId.isEmpty()) {
            return java.util.List.of();
        }
        // One aggregate query for the pinned products' real sales, so the reported numbers stay
        // truthful rather than being zeroed for everything.
        //
        // Its failure must not fail the section, though. findBestSellers is hand-written native SQL
        // and the curated list does not depend on it for anything load-bearing — the administrator
        // chose the products and their order, and the sales figure is decoration the storefront does
        // not even print. An integration test caught this: the query compares IS_ACTIVE to the
        // integer 1, which MySQL accepts and H2 refuses, so on H2 the whole curated section 500d
        // over a number nobody reads. Degrading to zeros keeps the home page up.
        Map<Long, com.sunglassstore.catalog.BestSellerRow> sales = salesFiguresOrEmpty();
        return wanted.stream()
                .filter(byId::containsKey)
                .map(id -> {
                    com.sunglassstore.catalog.BestSellerRow row = sales.get(id);
                    long sold = row == null || row.getSoldQuantity() == null ? 0L : row.getSoldQuantity();
                    return new com.sunglassstore.dto.response.BestSellerResponse(
                            toResponse(byId.get(id)), sold, row == null ? null : row.getSoldRevenue());
                })
                .toList();
    }

    /** Sales figures keyed by product, or an empty map if the aggregate cannot be read. */
    private Map<Long, com.sunglassstore.catalog.BestSellerRow> salesFiguresOrEmpty() {
        try {
            return productRepository.findBestSellers(MAX_BEST_SELLERS).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.sunglassstore.catalog.BestSellerRow::getProductId, row -> row,
                            (first, second) -> first));
        } catch (RuntimeException aggregateUnavailable) {
            log.warn("Best Sellers is curated, but the sales aggregate could not be read; "
                    + "reporting zero units sold for the pinned products", aggregateUnavailable);
            return Map.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        return toResponse(findProduct(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > 100) {
            throw new BadRequestException("Search keyword cannot exceed 100 characters");
        }
        return productRepository.search(normalized, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::toResponse);
    }

    /**
     * The request's variant list in family order, whichever field carried it. `variants` is the
     * structured contract (index 0 = the Main Product); `initialVariant` is the pre-redesign
     * single-variant field, treated as a one-entry list. Both at once is refused rather than
     * guessed at — the two could disagree about which variant is main.
     */
    private List<CreateVariantRequest> requestedVariants(CreateProductRequest request) {
        boolean hasList = request.getVariants() != null && !request.getVariants().isEmpty();
        if (hasList && request.getInitialVariant() != null) {
            throw new FieldValidationException("variants",
                    "Send either variants or initialVariant, not both");
        }
        if (hasList) return request.getVariants();
        return request.getInitialVariant() == null ? List.of() : List.of(request.getInitialVariant());
    }

    /**
     * SKU checks that bean validation cannot express: duplicates inside the request, and
     * collisions with the rest of the catalogue. Reported per field path (variants[2].sku) so the
     * admin form can mark the exact offending input. `ownSkus` maps a SKU to the variant id that
     * already legitimately holds it, so an update re-submitting a variant's own SKU is not a
     * collision with itself.
     */
    private void validateVariantSkus(List<CreateVariantRequest> variants, Map<String, Long> ownSkus) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        Map<String, Integer> seen = new java.util.HashMap<>();
        for (int index = 0; index < variants.size(); index++) {
            CreateVariantRequest variant = variants.get(index);
            String sku = variant.getSku() == null ? "" : variant.getSku().trim();
            if (sku.isEmpty()) continue; // @NotBlank already reports it under the right path.
            Integer firstIndex = seen.putIfAbsent(sku, index);
            if (firstIndex != null) {
                errors.put("variants[" + index + "].sku",
                        "Duplicate SKU — variant " + (firstIndex + 1) + " already uses \"" + sku + "\"");
                continue;
            }
            Long owner = ownSkus.get(sku);
            boolean ownedByThisVariant = owner != null && owner.equals(variant.getVariantId());
            if (!ownedByThisVariant && variantRepository.existsBySku(sku)) {
                errors.put("variants[" + index + "].sku", "SKU already exists: " + sku);
            }
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
    }

    /** Copies the per-variant editable fields; everything identity-related is handled by callers. */
    private void applyVariantFields(Product product, ProductVariant variant, CreateVariantRequest request) {
        variant.setSku(request.getSku().trim());
        variant.setVariantName(request.getVariantName());
        variant.setVariantDescription(request.getVariantDescription());
        variant.setPrice(request.getPrice());
        variant.setLowStockThreshold(request.getLowStockThreshold());
        if (request.getIsActive() != null) variant.setIsActive(request.getIsActive());
        setVariantAttributes(product, variant, request.getAttributes());
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        List<CreateVariantRequest> variantRequests = requestedVariants(request);
        // The invariant every other rule hangs off: a family exists only around a Main Product.
        // Refused up front, before anything is written, so a failed create leaves nothing behind.
        if (variantRequests.isEmpty()) {
            throw new FieldValidationException("variants",
                    "A product needs at least one variant — the main product is variant 1");
        }
        validateVariantSkus(variantRequests, Map.of());

        Product product = new Product();
        product.setProductName(request.getProductName());
        product.setBrand(request.getBrand());
        product.setProductDescription(request.getProductDescription());
        // The family price is a legacy fallback now; the Main Variant's own price stands in when
        // the caller no longer sends one.
        product.setBasePrice(request.getBasePrice() != null
                ? request.getBasePrice()
                : variantRequests.get(0).getPrice());
        // Draft support: created inactive, nothing is published (and no New-badge clock starts —
        // Product.onCreate only stamps publishedAt for an active product).
        product.setIsActive(request.getIsActive() == null || request.getIsActive());
        // The only place a slug is created. An admin-supplied one is validated and must be free;
        // otherwise it is derived from the name and made unique by retry.
        product.setSlug(request.getSlug() == null || request.getSlug().isBlank()
                ? uniqueSlugFor(request.getProductName())
                : validateRequestedSlug(request.getSlug(), null));

        // Set categories
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            Set<Category> categories = new HashSet<>();
            for (Long catId : request.getCategoryIds()) {
                Category category = categoryRepository.findById(catId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + catId));
                validateStorefrontCategory(category);
                categories.add(category);
            }
            product.setCategories(categories);
        }

        Product saved = productRepository.save(product);

        // List order IS the family order: index 0 becomes position 1, the Main Product. All in
        // this one transaction — a failure anywhere rolls back the whole family, so no published
        // parent can ever exist without a valid variant 1.
        for (int index = 0; index < variantRequests.size(); index++) {
            CreateVariantRequest variantRequest = variantRequests.get(index);
            ProductVariant variant = new ProductVariant();
            variant.setProduct(saved);
            variant.setPosition(index + 1);
            int openingStock = variantRequest.getQuantityAvailable();
            variant.setQuantityAvailable(0);
            applyVariantFields(saved, variant, variantRequest);
            variantRepository.save(variant);
            // Through the inventory service rather than a bare column write, so opening stock is
            // in the movement ledger like every other stock change.
            if (openingStock > 0) inventoryService.adjustInventory(variant.getVariantId(), openingStock,
                    com.sunglassstore.entity.enums.MovementType.PURCHASE, "Opening stock from product creation");
            saved.getVariants().add(variant);
        }

        // Add attributes
        if (request.getAttributes() != null) {
            for (Map.Entry<String, String> entry : request.getAttributes().entrySet()) {
                ProductAttribute attr = new ProductAttribute();
                attr.setProduct(saved);
                attr.setAttributeName(entry.getKey());
                attr.setAttributeValue(entry.getValue());
                attributeRepository.save(attr);
            }
        }

        return toResponse(productRepository.findById(saved.getProductId()).orElse(saved));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long productId, CreateProductRequest request) {
        Product product = findProduct(productId);
        // Read-edit-save conflict check, before any field is touched. Null skips it (legacy
        // callers), so this can never brick an integration that predates versioning — but the
        // admin editor always sends what it loaded, and a stale value means another save landed
        // in between. Refusing is the point: replaying values chosen against stale data is the
        // silent overwrite this exists to prevent.
        if (request.getVersion() != null && !request.getVersion().equals(product.getVersion())) {
            throw new com.sunglassstore.exception.OptimisticLockConflictException(
                    "This product was updated elsewhere. Refresh and review the latest version before saving again.");
        }
        product.setProductName(request.getProductName());
        product.setBrand(request.getBrand());
        product.setProductDescription(request.getProductDescription());
        if (request.getBasePrice() != null) product.setBasePrice(request.getBasePrice());
        // Null means "leave publication alone" on update — a field edit must not flip a draft
        // live. True publishes (stamping publishedAt once, same as setProductActive).
        if (request.getIsActive() != null) {
            product.setIsActive(request.getIsActive());
            if (request.getIsActive()) product.publish();
        }
        // The slug deliberately does NOT follow the name. Renaming a product must not move its
        // public URL: every link a customer bookmarked or shared, and every search result, points
        // at the old one. Only an explicit, different slug in the request changes it — and then the
        // admin has chosen to break those links knowingly.
        if (request.getSlug() != null && !request.getSlug().isBlank()
                && !request.getSlug().equals(product.getSlug())) {
            product.setSlug(validateRequestedSlug(request.getSlug(), productId));
        }

        if (request.getCategoryIds() != null) {
            Set<Category> categories = new HashSet<>();
            for (Long catId : request.getCategoryIds()) {
                Category category = categoryRepository.findById(catId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + catId));
                validateStorefrontCategory(category);
                categories.add(category);
            }
            product.setCategories(categories);
        }

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            upsertVariantFamily(product, request.getVariants());
        } else if (request.getInitialVariant() != null) {
            // Pre-redesign single-variant path, unchanged for existing callers.
            CreateVariantRequest variantRequest = request.getInitialVariant();
            ProductVariant variant;
            if (variantRequest.getVariantId() == null) {
                if (variantRepository.existsBySku(variantRequest.getSku())) {
                    throw new ConflictException("SKU already exists: " + variantRequest.getSku());
                }
                variant = new ProductVariant();
                variant.setProduct(product);
                variant.setPosition(nextPosition(product));
                product.getVariants().add(variant);
            } else {
                variant = variantRepository.findById(variantRequest.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
                if (!variant.getProduct().getProductId().equals(productId)) {
                    throw new BadRequestException("Variant does not belong to this product");
                }
                if (!variant.getSku().equals(variantRequest.getSku())
                        && variantRepository.existsBySku(variantRequest.getSku())) {
                    throw new ConflictException("SKU already exists: " + variantRequest.getSku());
                }
            }
            int previousStock = variant.getQuantityAvailable() == null ? 0 : variant.getQuantityAvailable();
            if (variant.getQuantityAvailable() == null) variant.setQuantityAvailable(0);
            applyVariantFields(product, variant, variantRequest);
            variantRepository.save(variant);
            int stockChange = variantRequest.getQuantityAvailable() - previousStock;
            if (stockChange != 0) inventoryService.adjustInventory(variant.getVariantId(), stockChange,
                    com.sunglassstore.entity.enums.MovementType.ADJUSTMENT, "Stock updated from product editor");
        }

        // updateProduct never touches publishedAt except through an explicit isActive=true above:
        // editing a name, price, description or stock level must not make an old product New again.
        return toResponse(productRepository.save(product));
    }

    /**
     * Applies a full-family edit: every existing variant updated, new entries created, and the
     * list order becoming the family order (index 0 = the Main Product).
     *
     * The list must name every existing variant — the same whole-list contract reorderImages uses,
     * and for the same reason: a partial list is ambiguous between "forgot" and "remove", and
     * removal is destructive enough to demand its own guarded endpoint. Two admins saving
     * concurrently are already fenced by the version check in updateProduct.
     */
    private void upsertVariantFamily(Product product, List<CreateVariantRequest> variantRequests) {
        Map<Long, ProductVariant> existingById = product.getVariants().stream()
                .collect(java.util.stream.Collectors.toMap(ProductVariant::getVariantId, variant -> variant));
        Map<String, Long> ownSkus = product.getVariants().stream()
                .collect(java.util.stream.Collectors.toMap(ProductVariant::getSku, ProductVariant::getVariantId));

        Map<String, String> errors = new java.util.LinkedHashMap<>();
        Set<Long> submittedIds = new HashSet<>();
        for (int index = 0; index < variantRequests.size(); index++) {
            Long variantId = variantRequests.get(index).getVariantId();
            if (variantId == null) continue;
            if (!existingById.containsKey(variantId)) {
                errors.put("variants[" + index + "].variantId", "Variant does not belong to this product");
            } else if (!submittedIds.add(variantId)) {
                errors.put("variants[" + index + "].variantId", "The same variant appears twice");
            }
        }
        if (submittedIds.size() < existingById.size()) {
            List<String> missing = existingById.values().stream()
                    .filter(variant -> !submittedIds.contains(variant.getVariantId()))
                    .map(ProductVariant::getSku).toList();
            errors.put("variants", "Every existing variant must be included — missing: "
                    + String.join(", ", missing) + ". Removing one is a separate action.");
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
        validateVariantSkus(variantRequests, ownSkus);

        List<ProductVariant> ordered = new ArrayList<>();
        List<Runnable> stockAdjustments = new ArrayList<>();
        for (CreateVariantRequest variantRequest : variantRequests) {
            ProductVariant variant;
            int previousStock;
            if (variantRequest.getVariantId() == null) {
                variant = new ProductVariant();
                variant.setProduct(product);
                // Position is provisional; applyPositions below assigns the real one. A real value
                // is still needed now because the column is NOT NULL and new rows flush first.
                variant.setPosition(nextPosition(product));
                variant.setQuantityAvailable(0);
                previousStock = 0;
                product.getVariants().add(variant);
            } else {
                variant = existingById.get(variantRequest.getVariantId());
                previousStock = variant.getQuantityAvailable();
            }
            applyVariantFields(product, variant, variantRequest);
            variantRepository.save(variant);
            ordered.add(variant);
            int stockChange = variantRequest.getQuantityAvailable() - previousStock;
            // Deferred until the variant has an id (a new variant gets one on flush inside
            // applyPositions or the save above completing), and until validation cannot fail.
            if (stockChange != 0) stockAdjustments.add(() ->
                    inventoryService.adjustInventory(variant.getVariantId(), stockChange,
                            com.sunglassstore.entity.enums.MovementType.ADJUSTMENT,
                            "Stock updated from product editor"));
        }
        applyPositions(ordered);
        stockAdjustments.forEach(Runnable::run);
        // The entity list was loaded in the OLD order and @OrderBy only applies on load, so the
        // response built from it this same transaction must be re-sorted by hand.
        product.getVariants().sort(java.util.Comparator.comparing(ProductVariant::getPosition));
    }

    /** The next free position at the end of the family. */
    private int nextPosition(Product product) {
        return product.getVariants().stream()
                .map(ProductVariant::getPosition)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    /**
     * Renumbers a family to 1..N in the given order, in two flushes.
     *
     * Two, because UQ_PRODUCT_VARIANTS_POSITION sees every UPDATE as it lands: moving variant B
     * onto position 1 while variant A still holds it is a duplicate key even though the end state
     * is legal. Parking everything on the (never legitimately used) negative of its target first
     * means the second pass only ever writes into free slots.
     */
    private void applyPositions(List<ProductVariant> orderedVariants) {
        for (int index = 0; index < orderedVariants.size(); index++) {
            orderedVariants.get(index).setPosition(-(index + 1));
        }
        variantRepository.saveAll(orderedVariants);
        variantRepository.flush();
        for (int index = 0; index < orderedVariants.size(); index++) {
            orderedVariants.get(index).setPosition(index + 1);
        }
        variantRepository.saveAll(orderedVariants);
        variantRepository.flush();
    }

    private void validateStorefrontCategory(Category category) {
        if (!Boolean.TRUE.equals(category.getIsActive()) || !STOREFRONT_CATEGORIES.contains(category.getCategoryName())) {
            throw new BadRequestException("Category must be Men, Women, Unisex, or Accessory");
        }
    }

    @Override
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = findProduct(productId);
        List<String> imageUrls = product.getImages().stream().map(ProductImage::getImageUrl).toList();
        List<Long> variantIds = product.getVariants().stream().map(ProductVariant::getVariantId).toList();

        /*
         * A product can always be removed, however much history it has. What "removed" means is
         * decided per table rather than by the database refusing the whole operation:
         *
         *   - live inventory state GOES: cart lines holding it, and its stock-movement ledger.
         *     These describe a product that is about to stop existing, so keeping them would leave
         *     the shop counting stock it cannot sell.
         *   - ORDER LINES STAY. They are the record of something a customer bought and paid for,
         *     and are not the catalogue's to rewrite. ORDER_ITEMS.VARIANT_ID is ON DELETE SET NULL
         *     (see 2026-08-09-allow-product-deletion-keeping-orders.sql) and each line already
         *     snapshots PRODUCT_NAME, SKU, QUANTITY, UNIT_PRICE, TAX, DISCOUNT and LINE_TOTAL, so
         *     past orders, invoices, returns and refunds all still read correctly afterwards.
         *   - everything else (variants, images, attributes, categories, reviews, wishlist entries,
         *     automatic-offer scope rows) is ON DELETE CASCADE and goes with the product.
         *
         * The two deletes below exist because those FKs are NO ACTION: without them the database
         * rejects the whole statement, which is exactly what used to surface as "this product has
         * order or inventory history and cannot be permanently removed".
         */
        if (!variantIds.isEmpty()) {
            cartItemRepository.deleteByVariantIds(variantIds);
            inventoryMovementRepository.deleteByVariantIds(variantIds);
        }

        productRepository.delete(product);
        productRepository.flush();

        // Files last, and only once the rows are gone: deleting them first would leave a product
        // pointing at missing images if the delete were then rejected.
        imageUrls.forEach(imageStorageService::delete);
    }

    @Override
    @Transactional
    public ProductResponse setProductActive(Long productId, boolean active) {
        Product product = findProduct(productId);
        product.setIsActive(active);
        // First activation is the publication event the New badge is measured from. publish() is
        // idempotent, so relisting a delisted product keeps its original date rather than making
        // an old product New again.
        if (active) {
            product.publish();
        }
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse.VariantSummary addVariant(Long productId, CreateVariantRequest request) {
        Product product = findProduct(productId);

        if (variantRepository.existsBySku(request.getSku())) {
            throw new ConflictException("SKU already exists: " + request.getSku());
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        // Appended to the end of the family. Only creation and the explicit set-main workflow
        // hand out position 1, so adding a colourway can never displace the Main Product.
        variant.setPosition(nextPosition(product));
        int openingStock = request.getQuantityAvailable();
        variant.setQuantityAvailable(0);
        applyVariantFields(product, variant, request);
        ProductVariant saved = variantRepository.save(variant);
        if (openingStock > 0) inventoryService.adjustInventory(saved.getVariantId(), openingStock,
                com.sunglassstore.entity.enums.MovementType.PURCHASE, "Opening stock from variant creation");
        return ProductResponse.VariantSummary.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductResponse.VariantSummary updateVariant(Long productId, Long variantId, CreateVariantRequest request) {
        findProduct(productId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        if (!variant.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Variant does not belong to this product");
        }

        if (!variant.getSku().equals(request.getSku())
                && variantRepository.existsBySku(request.getSku())) {
            throw new ConflictException("SKU already exists: " + request.getSku());
        }
        int previousStock = variant.getQuantityAvailable();
        applyVariantFields(variant.getProduct(), variant, request);

        ProductVariant saved = variantRepository.save(variant);
        int stockChange = request.getQuantityAvailable() - previousStock;
        if (stockChange != 0) inventoryService.adjustInventory(saved.getVariantId(), stockChange,
                com.sunglassstore.entity.enums.MovementType.ADJUSTMENT, "Stock updated from variant editor");
        return ProductResponse.VariantSummary.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        Product product = findProduct(productId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (!variant.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Variant does not belong to this product");
        }
        // A family without variants is invalid by definition — there is nothing left to be the
        // Main Product. Deleting the whole product is the operation that means that.
        if (product.getVariants().size() <= 1) {
            throw new BadRequestException("A product must keep at least one variant. "
                    + "Delete the product itself to remove it entirely.");
        }
        // A variant someone has bought is history, not catalogue. ORDER_ITEMS would survive the
        // delete (VARIANT_ID is ON DELETE SET NULL and every line is snapshotted), but losing the
        // link degrades cancellations, returns and review eligibility for those orders — so a
        // sold variant is archived, never destroyed.
        if (orderItemRepository.existsByVariantVariantId(variantId)) {
            throw new ConflictException("This variant has been ordered and cannot be deleted. "
                    + "Archive it instead — existing orders keep their history and it stops being sold.");
        }

        // Same per-table policy as product deletion: live state (cart lines, the stock ledger)
        // goes with the variant it describes.
        cartItemRepository.deleteByVariantIds(List.of(variantId));
        inventoryMovementRepository.deleteByVariantIds(List.of(variantId));

        // Photography is the admin's work and is kept: re-homed to the family's Main Product as
        // ordinary additional photos. Demoted, because the main variant already has its own main
        // image and the database enforces one per variant.
        List<ProductVariant> remaining = product.getVariants().stream()
                .filter(candidate -> !candidate.getVariantId().equals(variantId))
                .sorted(java.util.Comparator.comparing(ProductVariant::getPosition))
                .toList();
        ProductVariant newMain = remaining.get(0);
        List<ProductImage> orphaned = imageRepository
                .findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId).stream()
                .filter(image -> variantId.equals(image.getVariantId()))
                .toList();
        for (ProductImage image : orphaned) {
            image.setVariant(newMain);
            image.setIsPrimary(false);
        }
        imageRepository.saveAll(orphaned);
        imageRepository.flush();

        product.getVariants().remove(variant);
        variantRepository.delete(variant);
        variantRepository.flush();

        // Close the gap so positions stay 1..N — and so deleting the Main Product itself promotes
        // the next variant to position 1 in the same transaction, never leaving a family headless.
        applyPositions(remaining);
    }

    @Override
    @Transactional
    public ProductResponse setMainVariant(Long productId, Long variantId) {
        Product product = findProduct(productId);
        ProductVariant target = product.getVariants().stream()
                .filter(candidate -> candidate.getVariantId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        // The deliberate "Set as Main Variant" workflow: the target moves to position 1 and the
        // others close ranks in their existing order. Nothing else may reassign position 1.
        List<ProductVariant> ordered = new ArrayList<>();
        ordered.add(target);
        product.getVariants().stream()
                .filter(candidate -> !candidate.getVariantId().equals(variantId))
                .sorted(java.util.Comparator.comparing(ProductVariant::getPosition))
                .forEach(ordered::add);
        applyPositions(ordered);
        // Same reason as upsertVariantFamily: @OrderBy sorted the list at load, before this edit.
        product.getVariants().sort(java.util.Comparator.comparing(ProductVariant::getPosition));
        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse.VariantSummary setVariantActive(Long productId, Long variantId, boolean active) {
        findProduct(productId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (!variant.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Variant does not belong to this product");
        }
        // Archiving: the safe alternative to deletion. The variant keeps its position, images,
        // stock and history; the storefront stops offering it (mapProduct drops inactive
        // variants) and checkout refuses it like any unpurchasable variant.
        variant.setIsActive(active);
        return ProductResponse.VariantSummary.fromEntity(variantRepository.save(variant));
    }

    @Override
    @Transactional
    public ProductResponse.ImageSummary addImage(Long productId, CreateImageRequest request) {
        Product product = findProduct(productId);
        List<ProductImage> existing = imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId);

        ProductVariant variant = resolveImageVariant(product, request.getVariantId());
        List<ProductImage> variantImages = existing.stream()
                .filter(image -> variant.getVariantId().equals(image.getVariantId()))
                .toList();

        if (variantImages.size() >= maxImagesPerVariant) {
            throw new BadRequestException("A variant can have at most " + maxImagesPerVariant
                    + " images (1 main + " + (maxImagesPerVariant - 1)
                    + " additional). Remove one from this variant before adding another.");
        }

        // The first image of a VARIANT becomes its main image whether or not the caller asked.
        // Without this, a variant whose uploads all arrived with isPrimary=false has no main at
        // all, and its card thumbnail falls back to "whichever row came first" — which is exactly
        // the non-determinism the ordering rules are meant to remove.
        boolean primary = Boolean.TRUE.equals(request.getIsPrimary()) || variantImages.isEmpty();
        if (primary) clearPrimaryFlag(variantImages);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setVariant(variant);
        image.setImageUrl(request.getImageUrl());
        image.setAltText(request.getAltText());
        image.setDisplayOrder(request.getDisplayOrder() == null ? existing.size() : request.getDisplayOrder());
        image.setIsPrimary(primary);

        return ProductResponse.ImageSummary.fromEntity(imageRepository.saveAndFlush(image));
    }

    /**
     * Demotes whatever is currently primary among the given images — callers pass ONE VARIANT's
     * images, because that is the scope a main image is unique within — and FLUSHES before
     * returning.
     *
     * The flush is load-bearing, not caution. UQ_PRODUCT_IMAGES_VARIANT_PRIMARY makes "main image
     * of variant N" unique, and Hibernate orders inserts before updates within a flush — so
     * promoting a new image while the old one is still marked primary in the database violates the
     * constraint and the whole request 409s. Demoting first, in its own statement, is what makes
     * the swap legal.
     */
    private void clearPrimaryFlag(List<ProductImage> images) {
        boolean changed = false;
        for (ProductImage existing : images) {
            if (Boolean.TRUE.equals(existing.getIsPrimary())) {
                existing.setIsPrimary(false);
                imageRepository.save(existing);
                changed = true;
            }
        }
        if (changed) imageRepository.flush();
    }

    /**
     * The variant an image belongs to. Every photograph is filed against exactly one variant now;
     * "general product photo" no longer exists. A null variantId — the pre-redesign way of saying
     * "shown for every colour" — resolves to the family's Main Product, so old callers keep
     * working and their uploads land somewhere sensible rather than being refused.
     *
     * Rejects a variant belonging to a different product. The foreign key alone would not catch
     * that — it only proves the variant exists — so the cross-product check has to be explicit.
     */
    private ProductVariant resolveImageVariant(Product product, Long variantId) {
        if (variantId == null) {
            return product.getMainVariant()
                    .orElseThrow(() -> new BadRequestException(
                            "This product has no variants yet, so a photo cannot be filed against one"));
        }
        return product.getVariants().stream()
                .filter(candidate -> variantId.equals(candidate.getVariantId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Variant does not belong to this product"));
    }

    @Override
    @Transactional
    public List<ProductResponse.ImageSummary> reorderImages(Long productId, List<Long> imageIdsInOrder) {
        Product product = findProduct(productId);
        List<ProductImage> images = imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId);

        // The submitted list must be exactly this product's images. A partial list would leave the
        // unlisted ones at stale positions, and an id from another product would silently reorder
        // someone else's gallery — so both are refused rather than tolerated.
        Set<Long> owned = images.stream().map(ProductImage::getImageId).collect(java.util.stream.Collectors.toSet());
        Set<Long> submitted = new java.util.LinkedHashSet<>(imageIdsInOrder == null ? List.of() : imageIdsInOrder);
        if (submitted.size() != imageIdsInOrder.size()) {
            throw new BadRequestException("The image order contains the same image more than once");
        }
        if (!owned.equals(submitted)) {
            throw new BadRequestException("The image order must list exactly this product's images");
        }

        Map<Long, ProductImage> byId = images.stream()
                .collect(java.util.stream.Collectors.toMap(ProductImage::getImageId, image -> image));
        int position = 0;
        for (Long imageId : imageIdsInOrder) byId.get(imageId).setDisplayOrder(position++);
        imageRepository.saveAll(images);
        imageRepository.flush();
        return reloadImages(product.getProductId());
    }

    @Override
    @Transactional
    public List<ProductResponse.ImageSummary> setPrimaryImage(Long productId, Long imageId) {
        Product product = findProduct(productId);
        List<ProductImage> images = imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId);
        ProductImage target = images.stream().filter(image -> image.getImageId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        // "Main image" is a per-variant title now, so only the target's own variant's incumbent is
        // demoted — every other variant keeps its main. An image left variantless by a raw-SQL
        // variant delete is re-homed to the Main Product first, because a main image must belong
        // to the variant it fronts.
        if (target.getVariant() == null) {
            target.setVariant(resolveImageVariant(product, null));
        }
        Long targetVariantId = target.getVariantId();
        clearPrimaryFlag(images.stream()
                .filter(image -> targetVariantId.equals(image.getVariantId()))
                .toList());
        target.setIsPrimary(true);
        imageRepository.saveAndFlush(target);
        return reloadImages(productId);
    }

    @Override
    @Transactional
    public ProductResponse.ImageSummary updateImage(Long productId, Long imageId, CreateImageRequest request) {
        Product product = findProduct(productId);
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (!image.getProduct().getProductId().equals(productId)) {
            // Same message and status as a missing image: an admin editing product A must not be
            // able to learn which image ids belong to product B by probing.
            throw new ResourceNotFoundException("Image not found");
        }
        // PATCH semantics: an absent field means "leave it alone", not "set it to null". Assigning
        // the variant unconditionally meant a request carrying only altText — which is exactly what
        // the admin UI sends when captioning a photo — silently detached that photo from its
        // colourway. variantId 0, which used to mean "make it a general photo", now re-files onto
        // the Main Product — the nearest thing the new model has to "product-wide".
        if (request.getAltText() != null) image.setAltText(request.getAltText());
        if (request.getVariantId() != null) {
            ProductVariant destination = resolveImageVariant(product,
                    request.getVariantId() == 0 ? null : request.getVariantId());
            boolean moving = !destination.getVariantId().equals(image.getVariantId());
            if (moving) {
                Long sourceVariantId = image.getVariantId();
                boolean wasMain = Boolean.TRUE.equals(image.getIsPrimary());
                // Arrives demoted: the destination may already have a main image, and two mains
                // for one variant is exactly what the database now refuses.
                image.setIsPrimary(false);
                image.setVariant(destination);
                imageRepository.saveAndFlush(image);
                // Neither side may be left without a main while it still has photos.
                if (wasMain && sourceVariantId != null) ensureVariantHasMainImage(productId, sourceVariantId);
                ensureVariantHasMainImage(productId, destination.getVariantId());
                return ProductResponse.ImageSummary.fromEntity(image);
            }
        }
        return ProductResponse.ImageSummary.fromEntity(imageRepository.saveAndFlush(image));
    }

    /**
     * Promotes the variant's first image (gallery order) to main when it has photos but no main —
     * the invariant every card and gallery leads with. A variant with no photos legitimately has
     * no main and nothing is invented for it.
     */
    private void ensureVariantHasMainImage(Long productId, Long variantId) {
        List<ProductImage> images = imageRepository
                .findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId).stream()
                .filter(image -> variantId.equals(image.getVariantId()))
                .toList();
        if (images.isEmpty() || images.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsPrimary()))) return;
        ProductImage promoted = images.get(0);
        promoted.setIsPrimary(true);
        imageRepository.saveAndFlush(promoted);
    }

    private List<ProductResponse.ImageSummary> reloadImages(Long productId) {
        return imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId).stream()
                .sorted(java.util.Comparator
                        .comparing((ProductImage image) -> Boolean.TRUE.equals(image.getIsPrimary()) ? 0 : 1)
                        .thenComparing(ProductImage::getDisplayOrder)
                        .thenComparing(ProductImage::getImageId))
                .map(ProductResponse.ImageSummary::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (!image.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Image does not belong to this product");
        }
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        Long ownerVariantId = image.getVariantId();
        imageRepository.delete(image);
        imageRepository.flush();

        // Removing a variant's main image must not leave that variant without one while it still
        // has photos, or its card thumbnail falls back to arbitrary order. The variant's next
        // image in gallery order is promoted; a variant left with no images has nothing to promote
        // and legitimately ends up with none (its gallery then falls back to the Main Product's).
        if (wasPrimary && ownerVariantId != null) {
            ensureVariantHasMainImage(productId, ownerVariantId);
        }

        // Storage last, and only after the row is gone. The reverse order would delete the file and
        // then leave a row pointing at nothing if the flush failed.
        imageStorageService.delete(image.getImageUrl());
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    /**
     * A free slug derived from the product name.
     *
     * The retry handles the ordinary case: a second "Classic Aviator" finds the plain slug taken
     * and takes a suffixed one instead.
     *
     * It cannot close the race. Two admins creating that name simultaneously both see the slug free
     * here, and the loser's INSERT violates UQ_PRODUCTS_SLUG. That is deliberate rather than
     * overlooked — the constraint is the authority, and GlobalExceptionHandler already maps the
     * violation to 409 "A record with this value already exists", which is an honest answer to a
     * genuine collision. Catching it here to retry would not work anyway: the violation surfaces on
     * flush, by which point this transaction is already rollback-only.
     */
    private String uniqueSlugFor(String productName) {
        String candidate = ProductSlugs.generate(productName);
        for (int attempt = 0; attempt < 5 && productRepository.existsBySlug(candidate); attempt++) {
            candidate = ProductSlugs.withFreshSuffix(candidate);
        }
        return candidate;
    }

    /**
     * An admin-supplied slug, or a 400/409 explaining exactly which rule it broke.
     *
     * @param productId the product being edited, so re-submitting a product's own slug is not a
     *                  conflict with itself; null when creating.
     */
    private String validateRequestedSlug(String requested, Long productId) {
        String slug = requested.trim().toLowerCase(java.util.Locale.ROOT);
        if (ProductSlugs.isReserved(slug)) {
            throw new BadRequestException("\"" + slug + "\" is a reserved word and cannot be used as a product URL");
        }
        if (ProductSlugs.isNumericId(slug)) {
            // Would be indistinguishable from a legacy /product/{id} link.
            throw new BadRequestException("A product URL cannot be only digits");
        }
        if (!ProductSlugs.isValid(slug)) {
            throw new BadRequestException("A product URL may use only lowercase letters, numbers and single hyphens, "
                    + "up to " + ProductSlugs.MAX_LENGTH + " characters");
        }
        productRepository.findBySlug(slug).ifPresent(existing -> {
            if (!existing.getProductId().equals(productId)) {
                // Names the collision without leaking the other product's id.
                throw new ConflictException("Another product already uses the URL \"" + slug + "\"");
            }
        });
        return slug;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        // No "did you mean" and no distinction between "never existed" and "exists but is inactive":
        // both are a plain 404, so the response cannot be used to probe which slugs are real.
        Product product = productRepository.findBySlug(slug == null ? "" : slug.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public String findCanonicalSlug(Long productId) {
        return productRepository.findSlugByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void setVariantAttributes(Product product, ProductVariant variant, Map<String, String> attributes) {
        Map<String, String> desired = new java.util.LinkedHashMap<>();
        if (attributes != null) attributes.forEach((name, value) -> {
            if (value != null && !value.isBlank()) desired.put(name, value);
        });
        // Reconciled in place, NOT clear-and-recreate. Hibernate orders INSERTs before DELETEs
        // within a flush, so recreating an unchanged attribute would transiently duplicate its row
        // under UQ_PRODUCT_ATTRIBUTE and fail the whole save with 1062 — an editor changing only a
        // price then 409s on "color: Black" being written next to the identical row it replaces.
        variant.getAttributes().removeIf(attribute -> {
            String value = desired.remove(attribute.getAttributeName());
            if (value == null) return true;      // no longer wanted
            attribute.setAttributeValue(value);  // an update in place; a no-op when unchanged
            return false;
        });
        desired.forEach((name, value) -> {
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProduct(product);
            attribute.setVariant(variant);
            attribute.setAttributeName(name);
            attribute.setAttributeValue(value);
            variant.getAttributes().add(attribute);
        });
    }
}
