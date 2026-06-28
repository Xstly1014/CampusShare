-- 数据库迁移脚本：添加分类收纳袋功能
-- 执行方式：docker exec -i campushare-mysql mysql -uroot -p<root密码> campushare < V20260629__add_categories.sql
-- 用途：在已有数据库上新增 categories、sub_categories 表，并为 posts 表添加分类字段

USE campushare;

-- 1. 为 posts 表添加分类字段（如果不存在）
SET @dbname = DATABASE();
SET @tablename = 'posts';
SET @columnname = 'category_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE posts ADD COLUMN category_id VARCHAR(36) COMMENT ''所属分类ID（非校园分类帖子使用）'' AFTER school_id'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'sub_category_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE posts ADD COLUMN sub_category_id VARCHAR(36) COMMENT ''所属子分类ID（非校园分类帖子使用）'' AFTER category_id'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 2. 添加索引（如果不存在）
SET @indexname = 'idx_category';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  'ALTER TABLE posts ADD INDEX idx_category (category_id)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @indexname = 'idx_sub_category';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  'ALTER TABLE posts ADD INDEX idx_sub_category (sub_category_id)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 3. 创建主分类表（收纳袋）
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(36) PRIMARY KEY COMMENT '分类ID',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    icon VARCHAR(50) NOT NULL COMMENT '图标名称（lucide-react图标名）',
    color VARCHAR(20) COMMENT '主题色（tailwind颜色类）',
    type VARCHAR(20) NOT NULL DEFAULT 'category' COMMENT '类型：school-校园（包含学校列表），category-普通分类（包含子分类）',
    description VARCHAR(200) COMMENT '分类描述',
    sort_order INT DEFAULT 0 COMMENT '排序权重',
    post_count INT DEFAULT 0 COMMENT '帖子数量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sort (sort_order),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主分类表（收纳袋）';

-- 4. 创建子分类表
CREATE TABLE IF NOT EXISTS sub_categories (
    id VARCHAR(36) PRIMARY KEY COMMENT '子分类ID',
    category_id VARCHAR(36) NOT NULL COMMENT '所属主分类ID',
    name VARCHAR(50) NOT NULL COMMENT '子分类名称',
    sort_order INT DEFAULT 0 COMMENT '排序权重',
    post_count INT DEFAULT 0 COMMENT '帖子数量',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category_id),
    INDEX idx_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子分类表';

-- 5. 插入主分类初始数据（使用 INSERT IGNORE 避免重复）
INSERT IGNORE INTO categories (id, name, icon, color, type, description, sort_order) VALUES
('cat-campus', '校园', 'GraduationCap', 'blue', 'school', '高校学习资源与校园交流', 1),
('cat-music', '音乐', 'Music', 'purple', 'category', '分享音乐、歌单与乐评', 2),
('cat-movie', '影视', 'Clapperboard', 'red', 'category', '电影、电视剧、综艺讨论', 3),
('cat-anime', '动漫', 'Sparkles', 'pink', 'category', '二次元动漫、漫画交流', 4),
('cat-game', '游戏', 'Gamepad2', 'green', 'category', '游戏攻略、资讯与讨论', 5),
('cat-stock', '股市', 'TrendingUp', 'emerald', 'category', '股票、基金、投资理财', 6),
('cat-interview', '面经', 'Briefcase', 'amber', 'category', '求职面试经验分享', 7),
('cat-software', '软件', 'AppWindow', 'cyan', 'category', '软件资源、应用推荐', 8),
('cat-food', '美食', 'UtensilsCrossed', 'orange', 'category', '美食菜谱、探店分享', 9),
('cat-travel', '旅行', 'Plane', 'sky', 'category', '旅行攻略、游记分享', 10),
('cat-photo', '摄影', 'Camera', 'indigo', 'category', '摄影作品、技巧交流', 11),
('cat-book', '读书', 'BookOpen', 'teal', 'category', '书籍推荐、读书笔记', 12);

-- 6. 插入子分类初始数据（使用 INSERT IGNORE 避免重复）
INSERT IGNORE INTO sub_categories (id, category_id, name, sort_order) VALUES
-- 音乐子分类
('sub-music-cn', 'cat-music', '华语流行', 1),
('sub-music-eu', 'cat-music', '欧美流行', 2),
('sub-music-jp', 'cat-music', '日语音乐', 3),
('sub-music-kr', 'cat-music', 'K-POP', 4),
('sub-music-dj', 'cat-music', 'DJ/电音', 5),
('sub-music-classic', 'cat-music', '古典音乐', 6),
('sub-music-folk', 'cat-music', '民谣', 7),
('sub-music-rock', 'cat-music', '摇滚', 8),
('sub-music-rap', 'cat-music', '说唱/Rap', 9),
('sub-music-acg', 'cat-music', 'ACG音乐', 10),
-- 影视子分类
('sub-movie-action', 'cat-movie', '动作', 1),
('sub-movie-comedy', 'cat-movie', '喜剧', 2),
('sub-movie-scifi', 'cat-movie', '科幻', 3),
('sub-movie-romance', 'cat-movie', '爱情', 4),
('sub-movie-horror', 'cat-movie', '恐怖', 5),
('sub-movie-suspense', 'cat-movie', '悬疑', 6),
('sub-movie-drama', 'cat-movie', '剧情', 7),
('sub-movie-animation', 'cat-movie', '动画电影', 8),
('sub-movie-variety', 'cat-movie', '综艺', 9),
('sub-movie-doc', 'cat-movie', '纪录片', 10),
-- 动漫子分类
('sub-anime-hot', 'cat-anime', '热血', 1),
('sub-anime-love', 'cat-anime', '恋爱', 2),
('sub-anime-otome', 'cat-anime', '乙女', 3),
('sub-anime-harem', 'cat-anime', '后宫', 4),
('sub-anime-daily', 'cat-anime', '日常', 5),
('sub-anime-farm', 'cat-anime', '种田', 6),
('sub-anime-mystery', 'cat-anime', '悬疑推理', 7),
('sub-anime-mecha', 'cat-anime', '机战', 8),
('sub-anime-sports', 'cat-anime', '运动', 9),
('sub-anime-fantasy', 'cat-anime', '异世界奇幻', 10),
-- 游戏子分类
('sub-game-pc', 'cat-game', 'PC游戏', 1),
('sub-game-mobile', 'cat-game', '手机游戏', 2),
('sub-game-console', 'cat-game', '主机游戏', 3),
('sub-game-indie', 'cat-game', '独立游戏', 4),
('sub-game-guide', 'cat-game', '攻略心得', 5),
('sub-game-mod', 'cat-game', 'MOD/修改', 6),
-- 股市子分类
('sub-stock-ashare', 'cat-stock', 'A股', 1),
('sub-stock-hk', 'cat-stock', '港股', 2),
('sub-stock-us', 'cat-stock', '美股', 3),
('sub-stock-fund', 'cat-stock', '基金', 4),
('sub-stock-crypto', 'cat-stock', '加密货币', 5),
('sub-stock-tech', 'cat-stock', '技术分析', 6),
-- 面经子分类
('sub-interview-it', 'cat-interview', '互联网', 1),
('sub-interview-finance', 'cat-interview', '金融', 2),
('sub-interview-soe', 'cat-interview', '国企/央企', 3),
('sub-interview-foreign', 'cat-interview', '外企', 4),
('sub-interview-civil', 'cat-interview', '考公', 5),
('sub-interview-grad', 'cat-interview', '考研', 6),
-- 软件子分类
('sub-soft-win', 'cat-software', 'Windows', 1),
('sub-soft-mac', 'cat-software', 'macOS', 2),
('sub-soft-android', 'cat-software', 'Android', 3),
('sub-soft-ios', 'cat-software', 'iOS', 4),
('sub-soft-crack', 'cat-software', '破解工具', 5),
('sub-soft-efficiency', 'cat-software', '效率工具', 6),
-- 美食子分类
('sub-food-recipe', 'cat-food', '菜谱分享', 1),
('sub-food-restaurant', 'cat-food', '探店打卡', 2),
('sub-food-bake', 'cat-food', '烘焙甜品', 3),
('sub-food-drink', 'cat-food', '饮品', 4),
('sub-food-diet', 'cat-food', '减脂餐', 5),
-- 旅行子分类
('sub-travel-domestic', 'cat-travel', '国内游', 1),
('sub-travel-abroad', 'cat-travel', '出境游', 2),
('sub-travel-guide', 'cat-travel', '攻略游记', 3),
('sub-travel-hotel', 'cat-travel', '民宿住宿', 4),
-- 摄影子分类
('sub-photo-people', 'cat-photo', '人像', 1),
('sub-photo-landscape', 'cat-photo', '风光', 2),
('sub-photo-street', 'cat-photo', '街拍', 3),
('sub-photo-skill', 'cat-photo', '后期技巧', 4),
-- 读书子分类
('sub-book-fiction', 'cat-book', '小说文学', 1),
('sub-book-tech', 'cat-book', '技术书籍', 2),
('sub-book-history', 'cat-book', '历史人文', 3),
('sub-book-self', 'cat-book', '自我提升', 4);
