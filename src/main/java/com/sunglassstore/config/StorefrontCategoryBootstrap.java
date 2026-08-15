package com.sunglassstore.config;

import com.sunglassstore.entity.Category;
import com.sunglassstore.repository.CategoryRepository;
import com.sunglassstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps the fixed storefront taxonomy available without requiring a manual data migration. */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StorefrontCategoryBootstrap implements ApplicationRunner {

    private static final Map<String, String> REQUIRED_CATEGORIES = new LinkedHashMap<>();
    static {
        REQUIRED_CATEGORIES.put("Men", "Eyewear designed for men");
        REQUIRED_CATEGORIES.put("Women", "Eyewear designed for women");
        REQUIRED_CATEGORIES.put("Unisex", "Eyewear designed for everyone");
        REQUIRED_CATEGORIES.put("Accessory", "Eyewear accessories and care products");
    }

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Category unisex = null;
        for (var entry : REQUIRED_CATEGORIES.entrySet()) {
            Category category = categoryRepository.findByCategoryNameIgnoreCase(entry.getKey())
                    .orElseGet(Category::new);
            category.setCategoryName(entry.getKey());
            category.setDescription(entry.getValue());
            category.setIsActive(true);
            category = categoryRepository.save(category);
            if ("Unisex".equals(entry.getKey())) unisex = category;
        }

        // Existing products predate required categories. Unisex is the least misleading safe default;
        // administrators can refine each assignment later from the product editor.
        if (unisex != null) {
            Category defaultCategory = unisex;
            productRepository.findAll().stream()
                    .filter(product -> product.getCategories() == null || product.getCategories().isEmpty())
                    .forEach(product -> {
                        product.getCategories().add(defaultCategory);
                        productRepository.save(product);
                    });
        }
    }
}
