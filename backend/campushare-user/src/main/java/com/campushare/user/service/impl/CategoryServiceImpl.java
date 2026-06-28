package com.campushare.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.user.dto.CategoryDTO;
import com.campushare.user.dto.SubCategoryDTO;
import com.campushare.user.entity.Category;
import com.campushare.user.entity.SubCategory;
import com.campushare.user.mapper.CategoryMapper;
import com.campushare.user.mapper.SubCategoryMapper;
import com.campushare.user.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final SubCategoryMapper subCategoryMapper;

    @Override
    public List<CategoryDTO> getAllCategories() {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder)
        );

        List<SubCategory> allSubCategories = subCategoryMapper.selectList(
                new LambdaQueryWrapper<SubCategory>()
                        .orderByAsc(SubCategory::getSortOrder)
        );

        Map<String, List<SubCategory>> subMap = allSubCategories.stream()
                .collect(Collectors.groupingBy(SubCategory::getCategoryId));

        List<CategoryDTO> result = new ArrayList<>();
        for (Category cat : categories) {
            List<SubCategoryDTO> subs = subMap.getOrDefault(cat.getId(), new ArrayList<>()).stream()
                    .map(this::toSubDTO)
                    .collect(Collectors.toList());
            result.add(toDTO(cat, subs));
        }
        return result;
    }

    @Override
    public List<CategoryDTO> getSubCategoriesByCategoryId(String categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return new ArrayList<>();
        }
        List<SubCategory> subs = subCategoryMapper.selectList(
                new LambdaQueryWrapper<SubCategory>()
                        .eq(SubCategory::getCategoryId, categoryId)
                        .orderByAsc(SubCategory::getSortOrder)
        );
        List<SubCategoryDTO> subDTOs = subs.stream().map(this::toSubDTO).collect(Collectors.toList());
        List<CategoryDTO> result = new ArrayList<>();
        result.add(toDTO(category, subDTOs));
        return result;
    }

    @Override
    public CategoryDTO getCategoryById(String categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) return null;
        List<SubCategory> subs = subCategoryMapper.selectList(
                new LambdaQueryWrapper<SubCategory>()
                        .eq(SubCategory::getCategoryId, categoryId)
                        .orderByAsc(SubCategory::getSortOrder)
        );
        List<SubCategoryDTO> subDTOs = subs.stream().map(this::toSubDTO).collect(Collectors.toList());
        return toDTO(category, subDTOs);
    }

    private CategoryDTO toDTO(Category cat, List<SubCategoryDTO> subs) {
        return CategoryDTO.builder()
                .id(cat.getId())
                .name(cat.getName())
                .icon(cat.getIcon())
                .color(cat.getColor())
                .type(cat.getType())
                .description(cat.getDescription())
                .sortOrder(cat.getSortOrder())
                .postCount(cat.getPostCount())
                .subCategories(subs)
                .build();
    }

    private SubCategoryDTO toSubDTO(SubCategory sub) {
        return SubCategoryDTO.builder()
                .id(sub.getId())
                .categoryId(sub.getCategoryId())
                .name(sub.getName())
                .sortOrder(sub.getSortOrder())
                .postCount(sub.getPostCount())
                .build();
    }
}
