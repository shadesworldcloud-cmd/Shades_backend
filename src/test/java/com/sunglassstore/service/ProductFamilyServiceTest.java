package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.dto.response.ProductResponse;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.OrderItem;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.FieldValidationException;
import com.sunglassstore.exception.OptimisticLockConflictException;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The product-family invariants, end to end through the real service against a real schema:
 * every family has a Main Variant at position 1, positions stay contiguous through every edit,
 * and each variant owns at most one main image (its card face).
 *
 * Expectations are derived from the requests the tests build — never hard-coded counts — so a
 * fixture growing a variant does not break an unrelated assertion.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product-family;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("test")
class ProductFamilyServiceTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private com.sunglassstore.repository.ProductVariantRepository variants;
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;

    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // Orders first (their lines reference variants), then through the real service, which
        // clears the movement ledger and cart lines a bare repository delete trips over.
        createdOrderIds.forEach(orders::deleteById);
        createdOrderIds.clear();
        createdProductIds.forEach(id -> {
            if (products.existsById(id)) productService.deleteProduct(id);
        });
        createdProductIds.clear();
        createdUserIds.forEach(users::deleteById);
        createdUserIds.clear();
    }

    private CreateVariantRequest variant(String label, String price, int stock) {
        CreateVariantRequest request = new CreateVariantRequest();
        request.setSku("FAM-" + label + "-" + UUID.randomUUID());
        request.setVariantName(label);
        request.setPrice(new BigDecimal(price));
        request.setQuantityAvailable(stock);
        request.setLowStockThreshold(1);
        request.setAttributes(Map.of("color", label));
        return request;
    }

    private CreateProductRequest familyRequest(CreateVariantRequest... variants) {
        CreateProductRequest request = new CreateProductRequest();
        request.setProductName("Family " + UUID.randomUUID());
        request.setBrand("Shades World");
        request.setVariants(List.of(variants));
        return request;
    }

    private ProductResponse create(CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        createdProductIds.add(response.getProductId());
        return response;
    }

    private CreateImageRequest image(String name, Long variantId) {
        CreateImageRequest request = new CreateImageRequest();
        request.setImageUrl("http://localhost:8080/uploads/products/test/" + name + ".png");
        request.setVariantId(variantId);
        return request;
    }

    // ── Creation ──────────────────────────────────────────────────────────────────────────

    @Test
    void createWithOnlyMainVariantMakesItPositionOne() {
        CreateVariantRequest main = variant("Onyx", "1200.00", 4);
        ProductResponse created = create(familyRequest(main));

        assertEquals(1, created.getVariants().size());
        ProductResponse.VariantSummary summary = created.getVariants().get(0);
        assertEquals(1, summary.position());
        assertEquals(Boolean.TRUE, summary.mainVariant());
        // Opening stock went through the ledger, not a bare column write.
        assertEquals(4, summary.quantityAvailable());
        // The family fallback price defaults to the Main Variant's own.
        assertEquals(0, created.getBasePrice().compareTo(new BigDecimal("1200.00")));
        assertEquals(Boolean.TRUE, created.getIsActive());
        assertNotNull(created.getPublishedAt());
    }

    @Test
    void createWithSeveralVariantsUsesListOrderAsFamilyOrder() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 2), variant("Blue", "1100.00", 3), variant("Rose", "900.00", 0)));

        List<ProductResponse.VariantSummary> variants = created.getVariants();
        assertEquals(List.of(1, 2, 3), variants.stream().map(ProductResponse.VariantSummary::position).toList());
        assertEquals(List.of("Onyx", "Blue", "Rose"),
                variants.stream().map(ProductResponse.VariantSummary::variantName).toList());
        assertEquals(List.of(true, false, false),
                variants.stream().map(ProductResponse.VariantSummary::mainVariant).toList());
    }

    @Test
    void createWithoutAnyVariantIsRefused() {
        CreateProductRequest request = familyRequest();
        request.setVariants(List.of());
        request.setBasePrice(new BigDecimal("100.00"));
        FieldValidationException refusal =
                assertThrows(FieldValidationException.class, () -> productService.createProduct(request));
        assertTrue(refusal.getFieldErrors().containsKey("variants"));
    }

    @Test
    void createWithBothVariantShapesIsRefusedNotGuessed() {
        CreateProductRequest request = familyRequest(variant("Onyx", "1000.00", 1));
        request.setInitialVariant(variant("Blue", "1100.00", 1));
        FieldValidationException refusal =
                assertThrows(FieldValidationException.class, () -> productService.createProduct(request));
        assertTrue(refusal.getFieldErrors().containsKey("variants"));
    }

    @Test
    void duplicateSkuInsideOneRequestNamesTheExactField() {
        CreateVariantRequest first = variant("Onyx", "1000.00", 1);
        CreateVariantRequest second = variant("Blue", "1100.00", 1);
        second.setSku(first.getSku());
        FieldValidationException refusal = assertThrows(FieldValidationException.class,
                () -> productService.createProduct(familyRequest(first, second)));
        assertTrue(refusal.getFieldErrors().containsKey("variants[1].sku"),
                "expected the SECOND occurrence to be blamed, got " + refusal.getFieldErrors());
    }

    @Test
    void skuAlreadyInTheCatalogueNamesTheExactField() {
        CreateVariantRequest existing = variant("Onyx", "1000.00", 1);
        create(familyRequest(existing));

        CreateVariantRequest clash = variant("Blue", "1100.00", 1);
        clash.setSku(existing.getSku());
        FieldValidationException refusal = assertThrows(FieldValidationException.class,
                () -> productService.createProduct(familyRequest(clash)));
        assertTrue(refusal.getFieldErrors().containsKey("variants[0].sku"));
    }

    @Test
    void draftIsCreatedUnpublishedAndPublishingStampsIt() {
        CreateProductRequest request = familyRequest(variant("Onyx", "1000.00", 1));
        request.setIsActive(false);
        ProductResponse draft = create(request);
        assertEquals(Boolean.FALSE, draft.getIsActive());
        assertNull(draft.getPublishedAt(), "a draft must not carry a publication date");

        ProductResponse published = productService.setProductActive(draft.getProductId(), true);
        assertEquals(Boolean.TRUE, published.getIsActive());
        assertNotNull(published.getPublishedAt());
    }

    // ── Editing ───────────────────────────────────────────────────────────────────────────

    @Test
    void updateWithReorderedListRenumbersPositions() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        ProductResponse.VariantSummary onyx = created.getVariants().get(0);
        ProductResponse.VariantSummary blue = created.getVariants().get(1);

        CreateProductRequest update = new CreateProductRequest();
        update.setProductName(created.getProductName());
        update.setBrand(created.getBrand());
        update.setVersion(created.getVersion());
        CreateVariantRequest blueFirst = variant("Blue", "1100.00", 1);
        blueFirst.setVariantId(blue.variantId());
        blueFirst.setSku(blue.sku());
        CreateVariantRequest onyxSecond = variant("Onyx", "1000.00", 1);
        onyxSecond.setVariantId(onyx.variantId());
        onyxSecond.setSku(onyx.sku());
        update.setVariants(List.of(blueFirst, onyxSecond));

        ProductResponse updated = productService.updateProduct(created.getProductId(), update);
        assertEquals(List.of(blue.variantId(), onyx.variantId()),
                updated.getVariants().stream().map(ProductResponse.VariantSummary::variantId).toList());
        assertEquals(List.of(1, 2),
                updated.getVariants().stream().map(ProductResponse.VariantSummary::position).toList());
    }

    @Test
    void updateOmittingAnExistingVariantIsRefused() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        ProductResponse.VariantSummary onyx = created.getVariants().get(0);

        CreateProductRequest update = new CreateProductRequest();
        update.setProductName(created.getProductName());
        CreateVariantRequest onlyOnyx = variant("Onyx", "1000.00", 1);
        onlyOnyx.setVariantId(onyx.variantId());
        onlyOnyx.setSku(onyx.sku());
        update.setVariants(List.of(onlyOnyx));

        FieldValidationException refusal = assertThrows(FieldValidationException.class,
                () -> productService.updateProduct(created.getProductId(), update));
        assertTrue(refusal.getFieldErrors().containsKey("variants"));
    }

    @Test
    void staleVersionIsRefusedWithConflict() {
        ProductResponse created = create(familyRequest(variant("Onyx", "1000.00", 1)));

        CreateProductRequest bump = new CreateProductRequest();
        bump.setProductName(created.getProductName() + " renamed");
        bump.setVersion(created.getVersion());
        productService.updateProduct(created.getProductId(), bump);

        CreateProductRequest stale = new CreateProductRequest();
        stale.setProductName(created.getProductName() + " conflicting rename");
        stale.setVersion(created.getVersion()); // the version loaded BEFORE the first save
        assertThrows(OptimisticLockConflictException.class,
                () -> productService.updateProduct(created.getProductId(), stale));
    }

    @Test
    void addVariantAppendsToTheEndOfTheFamily() {
        ProductResponse created = create(familyRequest(variant("Onyx", "1000.00", 1)));
        ProductResponse.VariantSummary added =
                productService.addVariant(created.getProductId(), variant("Blue", "1100.00", 2));
        assertEquals(2, added.position());
        assertEquals(Boolean.FALSE, added.mainVariant());
    }

    @Test
    void setMainVariantRotatesTheFamilyOrder() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1), variant("Rose", "900.00", 1)));
        Long roseId = created.getVariants().get(2).variantId();

        ProductResponse rotated = productService.setMainVariant(created.getProductId(), roseId);
        assertEquals(roseId, rotated.getVariants().get(0).variantId());
        assertEquals(List.of(1, 2, 3),
                rotated.getVariants().stream().map(ProductResponse.VariantSummary::position).toList());
        assertEquals(Boolean.TRUE, rotated.getVariants().get(0).mainVariant());
    }

    @Test
    void archivingAVariantKeepsItAndItsPosition() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        Long blueId = created.getVariants().get(1).variantId();

        ProductResponse.VariantSummary archived =
                productService.setVariantActive(created.getProductId(), blueId, false);
        assertEquals(Boolean.FALSE, archived.isActive());
        assertEquals(2, archived.position());

        ProductResponse reloaded = productService.getProductById(created.getProductId());
        assertEquals(2, reloaded.getVariants().size(), "archiving must not remove the variant");
    }

    // ── Deletion guards ───────────────────────────────────────────────────────────────────

    @Test
    void theLastVariantCannotBeDeleted() {
        ProductResponse created = create(familyRequest(variant("Onyx", "1000.00", 1)));
        Long onlyVariant = created.getVariants().get(0).variantId();
        assertThrows(BadRequestException.class,
                () -> productService.deleteVariant(created.getProductId(), onlyVariant));
    }

    @Test
    void anOrderedVariantMustBeArchivedNotDeleted() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 5), variant("Blue", "1100.00", 5)));
        ProductResponse.VariantSummary blue = created.getVariants().get(1);

        User buyer = new User();
        buyer.setEmail("family+" + UUID.randomUUID() + "@example.test");
        buyer.setPasswordHash("test-only");
        buyer.setName("Family Buyer");
        users.save(buyer);
        createdUserIds.add(buyer.getUserId());

        Order order = new Order();
        order.setUser(buyer);
        order.setOrderStatus(OrderStatus.PLACED);
        order.setSubtotalAmount(blue.price());
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setShippingAmount(BigDecimal.ZERO);
        order.setTotalAmount(blue.price());
        order.setShippingName("Family Buyer");
        order.setShippingAddressLine1("1 Test Street");
        order.setShippingCity("Testville");
        order.setShippingState("Test State");
        order.setShippingPincode("560001");
        order.setShippingCountry("India");
        OrderItem line = new OrderItem();
        line.setOrder(order);
        line.setProductName(created.getProductName());
        line.setSku(blue.sku());
        line.setVariantLabel("Blue");
        line.setQuantity(1);
        line.setUnitPrice(blue.price());
        line.setLineTotal(blue.price());
        line.setVariant(variants.findById(blue.variantId()).orElseThrow());
        order.setItems(List.of(line));
        createdOrderIds.add(orders.save(order).getOrderId());

        assertThrows(ConflictException.class,
                () -> productService.deleteVariant(created.getProductId(), blue.variantId()));
    }

    @Test
    void deletingTheMainVariantPromotesTheNextAndRehomesNothingItOwns() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1), variant("Rose", "900.00", 1)));
        Long onyxId = created.getVariants().get(0).variantId();
        Long blueId = created.getVariants().get(1).variantId();

        // Give the doomed main variant a photo; it must survive on the new main variant, demoted.
        productService.addImage(created.getProductId(), image("onyx-hero", onyxId));
        productService.addImage(created.getProductId(), image("blue-hero", blueId));

        productService.deleteVariant(created.getProductId(), onyxId);

        ProductResponse after = productService.getProductById(created.getProductId());
        assertEquals(List.of(1, 2), after.getVariants().stream()
                .map(ProductResponse.VariantSummary::position).toList());
        assertEquals(blueId, after.getVariants().get(0).variantId());
        assertEquals(Boolean.TRUE, after.getVariants().get(0).mainVariant());

        // Both photos now belong to Blue: its own hero stays its main image, the re-homed one
        // arrives as an ordinary additional photo.
        List<ProductResponse.ImageSummary> blueImages = after.getImages().stream()
                .filter(imageSummary -> blueId.equals(imageSummary.variantId())).toList();
        assertEquals(2, blueImages.size());
        assertEquals(1, blueImages.stream().filter(ProductResponse.ImageSummary::isPrimary).count());
        assertTrue(blueImages.stream().anyMatch(imageSummary ->
                imageSummary.imageUrl().contains("blue-hero") && imageSummary.isPrimary()));
    }

    // ── Images ────────────────────────────────────────────────────────────────────────────

    @Test
    void anImageWithoutAVariantIsFiledOnTheMainVariant() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        ProductResponse.ImageSummary stored =
                productService.addImage(created.getProductId(), image("legacy-general", null));
        assertEquals(created.getVariants().get(0).variantId(), stored.variantId());
        assertEquals(Boolean.TRUE, stored.isPrimary(), "a variant's first photo becomes its main image");
    }

    @Test
    void eachVariantGetsItsOwnMainImageIndependently() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        Long onyxId = created.getVariants().get(0).variantId();
        Long blueId = created.getVariants().get(1).variantId();

        ProductResponse.ImageSummary onyxHero = productService.addImage(created.getProductId(), image("onyx-1", onyxId));
        ProductResponse.ImageSummary onyxSecond = productService.addImage(created.getProductId(), image("onyx-2", onyxId));
        ProductResponse.ImageSummary blueHero = productService.addImage(created.getProductId(), image("blue-1", blueId));

        assertEquals(Boolean.TRUE, onyxHero.isPrimary());
        assertEquals(Boolean.FALSE, onyxSecond.isPrimary());
        assertEquals(Boolean.TRUE, blueHero.isPrimary(), "Blue's first photo is Blue's main, independent of Onyx");
    }

    @Test
    void promotingAMainImageOnlyDemotesWithinItsOwnVariant() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        Long onyxId = created.getVariants().get(0).variantId();
        Long blueId = created.getVariants().get(1).variantId();

        productService.addImage(created.getProductId(), image("onyx-1", onyxId));
        productService.addImage(created.getProductId(), image("blue-1", blueId));
        ProductResponse.ImageSummary blueSecond =
                productService.addImage(created.getProductId(), image("blue-2", blueId));

        List<ProductResponse.ImageSummary> after =
                productService.setPrimaryImage(created.getProductId(), blueSecond.imageId());

        assertEquals(1, after.stream().filter(i -> blueId.equals(i.variantId()))
                .filter(ProductResponse.ImageSummary::isPrimary).count());
        assertTrue(after.stream().filter(i -> onyxId.equals(i.variantId()))
                .anyMatch(ProductResponse.ImageSummary::isPrimary), "Onyx must keep its own main image");
        assertTrue(after.stream().filter(ProductResponse.ImageSummary::isPrimary)
                .anyMatch(i -> i.imageUrl().contains("blue-2")));
    }

    @Test
    void deletingAMainImagePromotesTheVariantsNextPhoto() {
        ProductResponse created = create(familyRequest(variant("Onyx", "1000.00", 1)));
        Long onyxId = created.getVariants().get(0).variantId();
        ProductResponse.ImageSummary hero = productService.addImage(created.getProductId(), image("first", onyxId));
        productService.addImage(created.getProductId(), image("second", onyxId));

        productService.deleteImage(created.getProductId(), hero.imageId());

        ProductResponse after = productService.getProductById(created.getProductId());
        List<ProductResponse.ImageSummary> remaining = after.getImages();
        assertEquals(1, remaining.size());
        assertEquals(Boolean.TRUE, remaining.get(0).isPrimary());
        assertTrue(remaining.get(0).imageUrl().contains("second"));
    }

    @Test
    void theImageCeilingIsPerVariantNotPerProduct() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        Long onyxId = created.getVariants().get(0).variantId();
        Long blueId = created.getVariants().get(1).variantId();

        for (int index = 0; index < 10; index++) {
            productService.addImage(created.getProductId(), image("onyx-" + index, onyxId));
        }
        assertThrows(BadRequestException.class,
                () -> productService.addImage(created.getProductId(), image("onyx-overflow", onyxId)));
        // A full sibling variant must not block this one.
        assertDoesNotThrow(() -> productService.addImage(created.getProductId(), image("blue-ok", blueId)));
    }

    @Test
    void refilingAPhotoKeepsBothVariantsWithAMainImage() {
        ProductResponse created = create(familyRequest(
                variant("Onyx", "1000.00", 1), variant("Blue", "1100.00", 1)));
        Long onyxId = created.getVariants().get(0).variantId();
        Long blueId = created.getVariants().get(1).variantId();

        ProductResponse.ImageSummary onyxHero = productService.addImage(created.getProductId(), image("onyx-1", onyxId));
        productService.addImage(created.getProductId(), image("onyx-2", onyxId));
        productService.addImage(created.getProductId(), image("blue-1", blueId));

        // Move Onyx's MAIN photo onto Blue. Blue already has a main, so it must arrive demoted;
        // Onyx must promote its remaining photo rather than losing its card face.
        CreateImageRequest move = new CreateImageRequest();
        move.setImageUrl(onyxHero.imageUrl());
        move.setVariantId(blueId);
        productService.updateImage(created.getProductId(), onyxHero.imageId(), move);

        ProductResponse after = productService.getProductById(created.getProductId());
        List<ProductResponse.ImageSummary> onyxImages = after.getImages().stream()
                .filter(i -> onyxId.equals(i.variantId())).toList();
        List<ProductResponse.ImageSummary> blueImages = after.getImages().stream()
                .filter(i -> blueId.equals(i.variantId())).toList();
        assertEquals(1, onyxImages.size());
        assertEquals(1, onyxImages.stream().filter(ProductResponse.ImageSummary::isPrimary).count());
        assertEquals(2, blueImages.size());
        assertEquals(1, blueImages.stream().filter(ProductResponse.ImageSummary::isPrimary).count());
        assertTrue(blueImages.stream().filter(ProductResponse.ImageSummary::isPrimary)
                .allMatch(i -> i.imageUrl().contains("blue-1")), "Blue's incumbent main must survive the move");
    }
}
