package com.sunglassstore.service.impl;

import com.sunglassstore.entity.AppConfig;
import com.sunglassstore.repository.AppConfigRepository;
import com.sunglassstore.service.StorefrontSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorefrontSettingsServiceImpl implements StorefrontSettingsService {

    static final String HERO_IMAGE_KEY = "home.hero.image_url";
    static final String BEST_SELLER_ORDER_KEY = "home.best_sellers.order";

    private final AppConfigRepository appConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public String getHeroImageUrl() {
        return read(HERO_IMAGE_KEY);
    }

    @Override
    @Transactional
    public void setHeroImageUrl(String url) {
        write(HERO_IMAGE_KEY, url, "Home page hero image uploaded by an administrator");
    }

    @Override
    @Transactional
    public void clearHeroImage() {
        // Blanked rather than deleted: the row records that this setting exists and when it last
        // changed, and an absent value and an empty value both mean "use the bundled default".
        write(HERO_IMAGE_KEY, "", "Home page hero image uploaded by an administrator");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getCuratedBestSellerIds() {
        return parseIds(read(BEST_SELLER_ORDER_KEY));
    }

    @Override
    @Transactional
    public List<Long> setCuratedBestSellerIds(List<Long> productIds) {
        // LinkedHashSet: de-duplicate while keeping the first position of each id. A list with the
        // same product twice is an administrator slip, and silently honouring it would show the same
        // card twice in the carousel.
        List<Long> ordered = new ArrayList<>(new LinkedHashSet<>(
                productIds == null ? List.of() : productIds.stream().filter(java.util.Objects::nonNull).toList()));
        StringBuilder joined = new StringBuilder();
        for (Long id : ordered) {
            if (joined.length() > 0) joined.append(',');
            joined.append(id);
        }
        write(BEST_SELLER_ORDER_KEY, joined.toString(),
                "Product ids pinned to the home page Best Sellers section, in display order");
        return ordered;
    }

    private String read(String key) {
        return appConfigRepository.findByConfigShortCode(key)
                .map(AppConfig::getConfigValue)
                .orElse(null);
    }

    private void write(String key, String value, String description) {
        AppConfig row = appConfigRepository.findByConfigShortCode(key).orElseGet(() -> {
            AppConfig created = new AppConfig();
            created.setConfigShortCode(key);
            return created;
        });
        row.setConfigValue(value);
        row.setDescription(description);
        appConfigRepository.save(row);
    }

    /**
     * A comma-separated list of ids rather than JSON. The value is a handful of longs that are always
     * read and written as one unit, so a parser buys nothing, and a plain list stays readable to
     * whoever opens the CONFIG row in a SQL client.
     *
     * Anything unparseable is skipped instead of throwing: a corrupted setting must degrade to the
     * sales ranking, never take the home page down.
     */
    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ignored) {
                // skip the junk token, keep the rest
            }
        }
        return ids;
    }
}
