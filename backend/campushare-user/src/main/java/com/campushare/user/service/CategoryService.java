package com.campushare.user.service;

import com.campushare.user.dto.CategoryDTO;

import java.util.List;

public interface CategoryService {
    List<CategoryDTO> getAllCategories();
    List<CategoryDTO> getSubCategoriesByCategoryId(String categoryId);
    CategoryDTO getCategoryById(String categoryId);
}
