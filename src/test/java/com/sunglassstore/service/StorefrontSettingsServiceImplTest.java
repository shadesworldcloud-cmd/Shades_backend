package com.sunglassstore.service;

import com.sunglassstore.entity.AppConfig;
import com.sunglassstore.repository.AppConfigRepository;
import com.sunglassstore.service.impl.StorefrontSettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The settings live in a shared key/value table, so what matters is that each setting round-trips
 * through its own key and that a bad value cannot take the home page down.
 */
class StorefrontSettingsServiceImplTest {

    private Map<String, AppConfig> table;
    private StorefrontSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        table = new HashMap<>();
        AppConfigRepository repository = mock(AppConfigRepository.class);
        when(repository.findByConfigShortCode(anyString()))
                .thenAnswer(call -> Optional.ofNullable(table.get(call.getArgument(0, String.class))));
        when(repository.save(any(AppConfig.class))).thenAnswer(call -> {
            AppConfig row = call.getArgument(0, AppConfig.class);
            table.put(row.getConfigShortCode(), row);
            return row;
        });
        service = new StorefrontSettingsServiceImpl(repository);
    }

    @Test
    void anUncuratedStoreReportsNoOrderAndNoHeroImage() {
        assertThat(service.getCuratedBestSellerIds()).isEmpty();
        assertThat(service.getHeroImageUrl()).isNull();
    }

    @Test
    void theCuratedOrderRoundTripsExactlyAsGiven() {
        service.setCuratedBestSellerIds(List.of(30L, 7L, 12L));
        // Not sorted, not de-duplicated into some other order: the administrator's sequence IS the
        // setting, so 30 before 7 before 12 has to survive the store/load cycle.
        assertThat(service.getCuratedBestSellerIds()).containsExactly(30L, 7L, 12L);
    }

    @Test
    void aRepeatedProductIsKeptOnlyAtItsFirstPosition() {
        List<Long> stored = service.setCuratedBestSellerIds(List.of(5L, 9L, 5L, 2L, 9L));
        assertThat(stored).containsExactly(5L, 9L, 2L);
        assertThat(service.getCuratedBestSellerIds()).containsExactly(5L, 9L, 2L);
    }

    @Test
    void anEmptyListClearsTheCurationRatherThanStoringNothing() {
        service.setCuratedBestSellerIds(List.of(4L));
        assertThat(service.getCuratedBestSellerIds()).containsExactly(4L);

        service.setCuratedBestSellerIds(List.of());
        // Empty means "hand the section back to the sales ranking", which the caller detects by the
        // list being empty — so the read must report empty, not the previous order.
        assertThat(service.getCuratedBestSellerIds()).isEmpty();
    }

    @Test
    void aNullEntryIsDroppedInsteadOfBeingStoredAsAnId() {
        assertThat(service.setCuratedBestSellerIds(java.util.Arrays.asList(1L, null, 2L)))
                .containsExactly(1L, 2L);
    }

    @Test
    void aCorruptedSettingDegradesToTheUsableIdsRatherThanThrowing() {
        AppConfig corrupted = new AppConfig();
        corrupted.setConfigShortCode("home.best_sellers.order");
        // Hand-edited in a SQL client, or written by an older build. The home page must still render.
        corrupted.setConfigValue("8, ,notanid,,13,");
        table.put(corrupted.getConfigShortCode(), corrupted);

        assertThat(service.getCuratedBestSellerIds()).containsExactly(8L, 13L);
    }

    @Test
    void theHeroImageRoundTripsAndClearingItFallsBackToTheDefault() {
        service.setHeroImageUrl("https://ik.imagekit.io/swimgkit/storefront/hero/a.jpg#ik=abc");
        assertThat(service.getHeroImageUrl()).isEqualTo("https://ik.imagekit.io/swimgkit/storefront/hero/a.jpg#ik=abc");

        service.clearHeroImage();
        // Blank, not null, because the row is kept — the storefront treats both as "use the default".
        assertThat(service.getHeroImageUrl()).isBlank();
    }

    @Test
    void theTwoSettingsDoNotOverwriteEachOther() {
        service.setHeroImageUrl("https://cdn.example/hero.png");
        service.setCuratedBestSellerIds(List.of(11L, 22L));

        assertThat(service.getHeroImageUrl()).isEqualTo("https://cdn.example/hero.png");
        assertThat(service.getCuratedBestSellerIds()).containsExactly(11L, 22L);
        assertThat(table.keySet()).containsExactlyInAnyOrder("home.hero.image_url", "home.best_sellers.order");
    }
}
