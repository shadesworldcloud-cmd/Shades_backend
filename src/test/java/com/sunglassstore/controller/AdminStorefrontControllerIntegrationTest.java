package com.sunglassstore.controller;

import com.sunglassstore.entity.Category;
import com.sunglassstore.entity.Product;
import com.sunglassstore.repository.CategoryRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.service.ImageKitStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the two storefront settings, through the real HTTP layer, the real security
 * annotations, the real service and a real database.
 *
 * MockMvc with @WithMockUser rather than a signed-in session: it exercises the same controller and
 * the same @PreAuthorize without needing anyone's password.
 *
 * H2, so nothing database-specific is proved here — but neither setting depends on MySQL. What is
 * proved is the part that could break: that the curated order survives a round trip in the order it
 * was given, that it actually changes what the PUBLIC best-sellers endpoint serves, and that the
 * roles which may reach /api/admin/** but must not touch the shop window are refused.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminStorefrontControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    /** Nothing is uploaded to the real CDN by a test run. */
    @MockBean private ImageKitStorageService imageStorageService;

    private Long first;
    private Long second;
    private Long third;

    @BeforeEach
    void seedCatalogue() {
        Category category = categoryRepository.findAll().stream()
                .filter(entry -> "Unisex".equals(entry.getCategoryName()))
                .findFirst()
                .orElseGet(() -> {
                    Category created = new Category();
                    created.setCategoryName("Unisex");
                    created.setIsActive(true);
                    return categoryRepository.save(created);
                });
        first = saveProduct("Storefront One", category);
        second = saveProduct("Storefront Two", category);
        third = saveProduct("Storefront Three", category);
    }

    private Long saveProduct(String name, Category category) {
        Product product = new Product();
        product.setProductName(name);
        product.setBrand("Shades World");
        product.setBasePrice(new BigDecimal("1999.00"));
        product.setIsActive(true);
        product.publish();
        product.getCategories().add(category);
        return productRepository.save(product).getProductId();
    }

    // ── access control ────────────────────────────────────────────────────────────────────────

    @Test
    void anonymousCannotReadOrWriteStorefrontSettings() throws Exception {
        mockMvc.perform(get("/api/admin/storefront/best-sellers")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/storefront/hero-image")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void aStockManagerReachesApiAdminButCannotRearrangeTheShopWindow() throws Exception {
        // The whole reason for the second @PreAuthorize gate: SecurityConfig admits this role to
        // /api/admin/**, so without it a stock manager could re-merchandise the home page.
        mockMvc.perform(get("/api/admin/storefront/best-sellers")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/storefront/hero-image")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPPORT")
    void supportCannotRearrangeTheShopWindowEither() throws Exception {
        mockMvc.perform(get("/api/admin/storefront/best-sellers")).andExpect(status().isForbidden());
    }

    /** The storefront read has to work for a visitor who is not signed in at all. */
    @Test
    void theHeroSettingIsReadableAnonymously() throws Exception {
        mockMvc.perform(get("/api/storefront/settings")).andExpect(status().isOk());
    }

    // ── feature 1: curated Best Sellers, in the administrator's order ─────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void theCuratedOrderIsStoredAndServedInExactlyTheOrderGiven() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + third + "," + first + "," + second + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIsCurated").value(true))
                .andExpect(jsonPath("$.curated[0].productId").value(third))
                .andExpect(jsonPath("$.curated[1].productId").value(first))
                .andExpect(jsonPath("$.curated[2].productId").value(second));

        // Re-read: the order must survive, not come back sorted or re-ranked.
        mockMvc.perform(get("/api/admin/storefront/best-sellers"))
                .andExpect(jsonPath("$.curated[0].productId").value(third))
                .andExpect(jsonPath("$.curated[1].productId").value(first))
                .andExpect(jsonPath("$.curated[2].productId").value(second));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theCuratedOrderIsWhatThePublicBestSellersEndpointServes() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + second + "," + third + "]}"))
                .andExpect(status().isOk());

        // This is the assertion that matters: the shopper-facing endpoint, which normally ranks by
        // units sold, now serves the administrator's list in the administrator's order. None of
        // these seeded products has a single sale, so a sales ranking could not produce this.
        mockMvc.perform(get("/api/products/best-sellers").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].product.productId").value(second))
                .andExpect(jsonPath("$[1].product.productId").value(third));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void clearingTheCurationHandsTheSectionBackToTheSalesRanking() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + first + "]}"))
                .andExpect(jsonPath("$.curated.length()").value(1));

        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIsCurated").value(false))
                .andExpect(jsonPath("$.curated.length()").value(0));

        // Deliberately NOT asserting the public endpoint here. Clearing the curation hands the
        // section back to findBestSellers, and that native query compares IS_ACTIVE to the integer
        // 1 — legal in MySQL, rejected by H2 ("Values of types BOOLEAN and INTEGER are not
        // comparable"). The aggregate path therefore cannot be exercised on this profile at all,
        // which is a pre-existing limitation of the H2 test profile rather than anything to do with
        // curation; the sales ranking is covered end-to-end against real MySQL by
        // e2e/new-badge-and-best-sellers.spec.js. sourceIsCurated=false above is the assertion that
        // actually belongs to this feature.
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pinningAProductThatDoesNotExistIsRejectedAndChangesNothing() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + first + ",99999999]}"))
                .andExpect(status().isNotFound());

        // The valid id in that same request must NOT have been stored: a rejected order leaves the
        // section exactly as it was, rather than half-applied.
        mockMvc.perform(get("/api/admin/storefront/best-sellers"))
                .andExpect(jsonPath("$.sourceIsCurated").value(false))
                .andExpect(jsonPath("$.curated.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aDeactivatedProductStaysInTheAdminListButIsDroppedFromTheStorefront() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + first + "," + second + "]}"))
                .andExpect(status().isOk());

        Product hidden = productRepository.findById(first).orElseThrow();
        hidden.setIsActive(false);
        productRepository.saveAndFlush(hidden);

        // The administrator still sees it pinned, so they can tell why the storefront is shorter...
        mockMvc.perform(get("/api/admin/storefront/best-sellers"))
                .andExpect(jsonPath("$.curated.length()").value(2));
        // ...while the shopper is not shown a product that is no longer for sale.
        mockMvc.perform(get("/api/products/best-sellers"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].product.productId").value(second));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aProductPinnedTwiceAppearsOnlyOnce() throws Exception {
        mockMvc.perform(put("/api/admin/storefront/best-sellers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + first + "," + second + "," + first + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curated.length()").value(2))
                .andExpect(jsonPath("$.curated[0].productId").value(first))
                .andExpect(jsonPath("$.curated[1].productId").value(second));
    }

    // ── feature 2: the home page image ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadingAHeroImageMakesItTheOnePublicVisitorsGet() throws Exception {
        when(imageStorageService.storeInFolder(eq("/storefront/hero"), any()))
                .thenReturn("https://ik.imagekit.io/swimgkit/storefront/hero/banner.png#ik=fileid");

        MockMultipartFile file = new MockMultipartFile(
                "file", "banner.png", MediaType.IMAGE_PNG_VALUE, "not-really-png".getBytes());

        mockMvc.perform(multipart("/api/admin/storefront/hero-image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroImageUrl")
                        .value("https://ik.imagekit.io/swimgkit/storefront/hero/banner.png#ik=fileid"));

        // The public endpoint the home page actually calls.
        mockMvc.perform(get("/api/storefront/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroImageUrl")
                        .value("https://ik.imagekit.io/swimgkit/storefront/hero/banner.png#ik=fileid"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void revertingTheHeroImageReturnsTheStorefrontToItsBundledBanner() throws Exception {
        when(imageStorageService.storeInFolder(eq("/storefront/hero"), any()))
                .thenReturn("https://cdn.example/banner.jpg");
        mockMvc.perform(multipart("/api/admin/storefront/hero-image")
                        .file(new MockMultipartFile("file", "b.jpg", MediaType.IMAGE_JPEG_VALUE, "x".getBytes())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/storefront/hero-image")).andExpect(status().isOk());

        // Blank, not the old URL: the storefront reads blank as "use the bundled default".
        mockMvc.perform(get("/api/storefront/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroImageUrl").value(""));
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void aStockManagerCannotReplaceTheHomePageImage() throws Exception {
        mockMvc.perform(multipart("/api/admin/storefront/hero-image")
                        .file(new MockMultipartFile("file", "b.png", MediaType.IMAGE_PNG_VALUE, "x".getBytes())))
                .andExpect(status().isForbidden());
    }
}
