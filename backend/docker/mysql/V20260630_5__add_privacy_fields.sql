-- 隐私设置字段：5个开关
ALTER TABLE users ADD COLUMN public_posts TINYINT DEFAULT 1 COMMENT '隐私：是否公开帖子';
ALTER TABLE users ADD COLUMN public_stars TINYINT DEFAULT 0 COMMENT '隐私：是否公开收藏';
ALTER TABLE users ADD COLUMN public_likes TINYINT DEFAULT 0 COMMENT '隐私：是否公开点赞';
ALTER TABLE users ADD COLUMN public_history TINYINT DEFAULT 0 COMMENT '隐私：是否公开浏览历史';
ALTER TABLE users ADD COLUMN searchable TINYINT DEFAULT 1 COMMENT '隐私：是否允许被搜索';
