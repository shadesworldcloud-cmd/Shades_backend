package com.sunglassstore.repository;

import com.sunglassstore.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByIsActiveTrueOrderByCategoryNameAsc();

    boolean existsByCategoryNameIgnoreCase(String categoryName);

    Optional<Category> findByCategoryNameIgnoreCase(String categoryName);
}
