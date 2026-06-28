package com.campushare.post.service;

import com.campushare.post.dto.CategoryDTO;

import java.util.List;

public interface CategoryService {
    List<CategoryDTO> getAllCategories();
    List<CategoryDTO> getSubCategoriesByCategoryId(String categoryId);
    CategoryDTO getCategoryById(String categoryId);
}
