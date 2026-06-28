-- 数据库增量迁移脚本：为sub_categories表添加icon字段并更新图标数据
-- 执行方式：docker exec -i campushare-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 campushare < backend/docker/mysql/V20260628_1__add_subcategory_icons.sql
-- 用途：在已有sub_categories表上新增icon字段，并为每个子分类设置图标

USE campushare;

-- 1. 为sub_categories表添加icon字段（如果不存在）
SET @dbname = DATABASE();
SET @tablename = 'sub_categories';
SET @columnname = 'icon';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE sub_categories ADD COLUMN icon VARCHAR(50) NOT NULL DEFAULT ''Hash'' COMMENT ''图标名称（lucide-react图标名）'' AFTER name'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 2. 更新子分类图标数据
-- 音乐子分类
UPDATE sub_categories SET icon='Mic2' WHERE id='sub-music-cn';
UPDATE sub_categories SET icon='Globe' WHERE id='sub-music-eu';
UPDATE sub_categories SET icon='Languages' WHERE id='sub-music-jp';
UPDATE sub_categories SET icon='Star' WHERE id='sub-music-kr';
UPDATE sub_categories SET icon='Waves' WHERE id='sub-music-dj';
UPDATE sub_categories SET icon='Piano' WHERE id='sub-music-classic';
UPDATE sub_categories SET icon='Guitar' WHERE id='sub-music-folk';
UPDATE sub_categories SET icon='Volume2' WHERE id='sub-music-rock';
UPDATE sub_categories SET icon='Mic' WHERE id='sub-music-rap';
UPDATE sub_categories SET icon='Headphones' WHERE id='sub-music-acg';
-- 影视子分类
UPDATE sub_categories SET icon='Zap' WHERE id='sub-movie-action';
UPDATE sub_categories SET icon='Laugh' WHERE id='sub-movie-comedy';
UPDATE sub_categories SET icon='Rocket' WHERE id='sub-movie-scifi';
UPDATE sub_categories SET icon='Heart' WHERE id='sub-movie-romance';
UPDATE sub_categories SET icon='Skull' WHERE id='sub-movie-horror';
UPDATE sub_categories SET icon='Search' WHERE id='sub-movie-suspense';
UPDATE sub_categories SET icon='Film' WHERE id='sub-movie-drama';
UPDATE sub_categories SET icon='Palette' WHERE id='sub-movie-animation';
UPDATE sub_categories SET icon='Tv' WHERE id='sub-movie-variety';
UPDATE sub_categories SET icon='BookOpenCheck' WHERE id='sub-movie-doc';
-- 动漫子分类
UPDATE sub_categories SET icon='Flame' WHERE id='sub-anime-hot';
UPDATE sub_categories SET icon='Hearts' WHERE id='sub-anime-love';
UPDATE sub_categories SET icon='Sparkle' WHERE id='sub-anime-otome';
UPDATE sub_categories SET icon='Users' WHERE id='sub-anime-harem';
UPDATE sub_categories SET icon='Coffee' WHERE id='sub-anime-daily';
UPDATE sub_categories SET icon='Sprout' WHERE id='sub-anime-farm';
UPDATE sub_categories SET icon='KeyRound' WHERE id='sub-anime-mystery';
UPDATE sub_categories SET icon='Cog' WHERE id='sub-anime-mecha';
UPDATE sub_categories SET icon='Trophy' WHERE id='sub-anime-sports';
UPDATE sub_categories SET icon='Wand2' WHERE id='sub-anime-fantasy';
-- 游戏子分类
UPDATE sub_categories SET icon='Monitor' WHERE id='sub-game-pc';
UPDATE sub_categories SET icon='Smartphone' WHERE id='sub-game-mobile';
UPDATE sub_categories SET icon='Gamepad' WHERE id='sub-game-console';
UPDATE sub_categories SET icon='Puzzle' WHERE id='sub-game-indie';
UPDATE sub_categories SET icon='Lightbulb' WHERE id='sub-game-guide';
UPDATE sub_categories SET icon='Wrench' WHERE id='sub-game-mod';
-- 股市子分类
UPDATE sub_categories SET icon='Landmark' WHERE id='sub-stock-ashare';
UPDATE sub_categories SET icon='Building2' WHERE id='sub-stock-hk';
UPDATE sub_categories SET icon='DollarSign' WHERE id='sub-stock-us';
UPDATE sub_categories SET icon='PieChart' WHERE id='sub-stock-fund';
UPDATE sub_categories SET icon='Coins' WHERE id='sub-stock-crypto';
UPDATE sub_categories SET icon='LineChart' WHERE id='sub-stock-tech';
-- 面经子分类
UPDATE sub_categories SET icon='Globe2' WHERE id='sub-interview-it';
UPDATE sub_categories SET icon='Banknote' WHERE id='sub-interview-finance';
UPDATE sub_categories SET icon='Building' WHERE id='sub-interview-soe';
UPDATE sub_categories SET icon='BriefcaseBusiness' WHERE id='sub-interview-foreign';
UPDATE sub_categories SET icon='Scale' WHERE id='sub-interview-civil';
UPDATE sub_categories SET icon='BookMarked' WHERE id='sub-interview-grad';
-- 软件子分类
UPDATE sub_categories SET icon='MonitorPlay' WHERE id='sub-soft-win';
UPDATE sub_categories SET icon='Command' WHERE id='sub-soft-mac';
UPDATE sub_categories SET icon='Tablet' WHERE id='sub-soft-android';
UPDATE sub_categories SET icon='TabletSmartphone' WHERE id='sub-soft-ios';
UPDATE sub_categories SET icon='Key' WHERE id='sub-soft-crack';
UPDATE sub_categories SET icon='Blocks' WHERE id='sub-soft-efficiency';
-- 美食子分类
UPDATE sub_categories SET icon='ChefHat' WHERE id='sub-food-recipe';
UPDATE sub_categories SET icon='MapPin' WHERE id='sub-food-restaurant';
UPDATE sub_categories SET icon='CakeSlice' WHERE id='sub-food-bake';
UPDATE sub_categories SET icon='Wine' WHERE id='sub-food-drink';
UPDATE sub_categories SET icon='Dumbbell' WHERE id='sub-food-diet';
-- 旅行子分类
UPDATE sub_categories SET icon='Map' WHERE id='sub-travel-domestic';
UPDATE sub_categories SET icon='PlaneTakeoff' WHERE id='sub-travel-abroad';
UPDATE sub_categories SET icon='Compass' WHERE id='sub-travel-guide';
UPDATE sub_categories SET icon='BedDouble' WHERE id='sub-travel-hotel';
-- 摄影子分类
UPDATE sub_categories SET icon='User' WHERE id='sub-photo-people';
UPDATE sub_categories SET icon='Mountain' WHERE id='sub-photo-landscape';
UPDATE sub_categories SET icon='Aperture' WHERE id='sub-photo-street';
UPDATE sub_categories SET icon='SlidersHorizontal' WHERE id='sub-photo-skill';
-- 读书子分类
UPDATE sub_categories SET icon='BookText' WHERE id='sub-book-fiction';
UPDATE sub_categories SET icon='Code2' WHERE id='sub-book-tech';
UPDATE sub_categories SET icon='ScrollText' WHERE id='sub-book-history';
UPDATE sub_categories SET icon='Target' WHERE id='sub-book-self';
