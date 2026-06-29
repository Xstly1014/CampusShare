package com.campushare.post.cache;

import com.campushare.post.entity.Category;
import com.campushare.post.entity.SubCategory;
import com.campushare.post.mapper.CategoryMapper;
import com.campushare.post.mapper.SubCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryCache {

    private final CategoryMapper categoryMapper;
    private final SubCategoryMapper subCategoryMapper;

    private final Map<String, Category> categoryMap = new ConcurrentHashMap<>();
    private final Map<String, SubCategory> subCategoryMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedRate = 300000)
    public void refresh() {
        try {
            List<Category> categories = categoryMapper.selectList(null);
            if (categories != null) {
                categoryMap.clear();
                for (Category c : categories) {
                    categoryMap.put(c.getId(), c);
                }
            }
            List<SubCategory> subCategories = subCategoryMapper.selectList(null);
            if (subCategories != null) {
                subCategoryMap.clear();
                for (SubCategory s : subCategories) {
                    subCategoryMap.put(s.getId(), s);
                }
            }
            log.info("分类缓存刷新完成，共{}个分类，{}个子分类", categoryMap.size(), subCategoryMap.size());
        } catch (Exception e) {
            log.warn("刷新分类缓存失败: {}", e.getMessage());
        }
    }

    public Category getCategory(String id) {
        if (id == null) return null;
        return categoryMap.get(id);
    }

    public SubCategory getSubCategory(String id) {
        if (id == null) return null;
        return subCategoryMap.get(id);
    }
}
