-- V20260629_2__add_composite_indexes_for_performance.sql
-- 性能优化：为帖子列表查询添加复合索引，避免全表扫描和filesort

USE campushare;

-- 学校帖子列表复合索引：覆盖 school_id + category_id + status + deleted + create_time 排序
-- 对应接口: GET /posts/school/{schoolId}
ALTER TABLE posts ADD INDEX idx_school_list (school_id, category_id, status, deleted, create_time);

-- 分类帖子列表复合索引
-- 对应接口: GET /categories/{categoryId}/posts
ALTER TABLE posts ADD INDEX idx_category_list (category_id, status, deleted, create_time);

-- 子分类帖子列表复合索引
-- 对应接口: GET /categories/sub/{subCategoryId}/posts
ALTER TABLE posts ADD INDEX idx_subcategory_list (sub_category_id, status, deleted, create_time);

-- 我的帖子列表复合索引
-- 对应接口: GET /posts/mine
ALTER TABLE posts ADD INDEX idx_author_list (author_id, deleted, create_time);
