package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campushare.agent.dto.CategoryCreateRequest;
import com.campushare.agent.dto.CategoryRenameRequest;
import com.campushare.agent.dto.CategoryResponse;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentSessionCategory;
import com.campushare.agent.mapper.AgentSessionCategoryMapper;
import com.campushare.agent.mapper.AgentSessionMapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionCategoryServiceImpl implements AgentSessionCategoryService {

    private final AgentSessionCategoryMapper categoryMapper;
    private final AgentSessionMapper sessionMapper;

    @Override
    public CategoryResponse createCategory(String userId, CategoryCreateRequest request) {
        String name = request != null && request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_IS_BLANK, "分类名不能为空");
        }
        checkNameNotExists(userId, name, null);
        AgentSessionCategory category = AgentSessionCategory.builder()
                .userId(userId)
                .name(name)
                .sortOrder(0)
                .build();
        categoryMapper.insert(category);
        return toResponse(category);
    }

    @Override
    public List<CategoryResponse> getUserCategories(String userId) {
        LambdaQueryWrapper<AgentSessionCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSessionCategory::getUserId, userId)
               .orderByAsc(AgentSessionCategory::getSortOrder)
               .orderByDesc(AgentSessionCategory::getCreatedAt);
        return categoryMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse renameCategory(String userId, String categoryId, CategoryRenameRequest request) {
        AgentSessionCategory category = getCategoryAndVerifyOwner(userId, categoryId);
        String name = request != null && request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_IS_BLANK, "分类名不能为空");
        }
        checkNameNotExists(userId, name, categoryId);
        category.setName(name);
        categoryMapper.updateById(category);
        return toResponse(category);
    }

    @Override
    public void deleteCategory(String userId, String categoryId) {
        getCategoryAndVerifyOwner(userId, categoryId);
        // 将该分类下的会话置为未分类
        LambdaUpdateWrapper<AgentSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AgentSession::getUserId, userId)
                     .eq(AgentSession::getCategoryId, categoryId)
                     .set(AgentSession::getCategoryId, null);
        sessionMapper.update(null, updateWrapper);
        // 删除分类
        categoryMapper.deleteById(categoryId);
    }

    private void checkNameNotExists(String userId, String name, String excludeCategoryId) {
        LambdaQueryWrapper<AgentSessionCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSessionCategory::getUserId, userId)
               .eq(AgentSessionCategory::getName, name);
        if (excludeCategoryId != null) {
            wrapper.ne(AgentSessionCategory::getId, excludeCategoryId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.PARAM_NOT_VALID, "分类名已存在");
        }
    }

    private AgentSessionCategory getCategoryAndVerifyOwner(String userId, String categoryId) {
        AgentSessionCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!category.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_FORBIDDEN, "无权访问此分类");
        }
        return category;
    }

    private CategoryResponse toResponse(AgentSessionCategory category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .userId(category.getUserId())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
