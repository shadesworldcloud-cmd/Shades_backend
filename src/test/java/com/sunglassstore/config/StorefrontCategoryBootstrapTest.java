package com.sunglassstore.config;

import com.sunglassstore.entity.Category;
import com.sunglassstore.entity.Product;
import com.sunglassstore.repository.CategoryRepository;
import com.sunglassstore.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorefrontCategoryBootstrapTest {

    @Test
    void createsRequiredCategoriesAndAssignsLegacyProductsToUnisex() throws Exception {
        CategoryRepository categories = mock(CategoryRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        List<Category> savedCategories = new ArrayList<>();
        when(categories.findByCategoryNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(categories.save(any(Category.class))).thenAnswer(invocation -> {
            Category value = invocation.getArgument(0);
            value.setCategoryId((long) savedCategories.size() + 1);
            savedCategories.add(value);
            return value;
        });
        Product legacy = new Product(); legacy.setProductId(10L); legacy.setCategories(new HashSet<>());
        when(products.findAll()).thenReturn(List.of(legacy));

        new StorefrontCategoryBootstrap(categories, products)
                .run(new DefaultApplicationArguments(new String[0]));

        assertEquals(List.of("Men", "Women", "Unisex", "Accessory"),
                savedCategories.stream().map(Category::getCategoryName).toList());
        assertEquals("Unisex", legacy.getCategories().iterator().next().getCategoryName());
        verify(products).save(legacy);
    }
}
