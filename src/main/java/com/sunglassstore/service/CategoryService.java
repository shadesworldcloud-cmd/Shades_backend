package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateCategoryRequest;
import com.sunglassstore.entity.Category;

import java.util.List;

public interface CategoryService {
    List<Category> getAllActiveCategories();
    Category getCategoryById(Long categoryId);
    Category createCategory(CreateCategoryRequest request);
    Category updateCategory(Long categoryId, CreateCategoryRequest request);
    void deleteCategory(Long categoryId);
}
