package com.campushare.agent.service;

import com.campushare.agent.dto.CategoryCreateRequest;
import com.campushare.agent.dto.CategoryRenameRequest;
import com.campushare.agent.dto.CategoryResponse;

import java.util.List;

public interface AgentSessionCategoryService {

    CategoryResponse createCategory(String userId, CategoryCreateRequest request);

    List<CategoryResponse> getUserCategories(String userId);

    CategoryResponse renameCategory(String userId, String categoryId, CategoryRenameRequest request);

    void deleteCategory(String userId, String categoryId);
}
