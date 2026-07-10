-- ============================================================
-- 测试关联数据生成脚本（点赞/收藏/评论/浏览/下载）
-- 用法：docker exec -i campushare-mysql mysql -uroot -proot123456 campushare < generate-test-interactions.sql
-- 前提：已有用户(users)和帖子(posts)数据，本脚本只补充关联表
-- 预期产出（基于3000用户×10000帖子）：
--   post_likes   ≈ 150,000 条（每帖0-30赞，均值15）
--   post_stars   ≈  75,000 条（每帖0-15收藏，均值7.5）
--   comments     ≈  25,000 条（每帖0-5评论，均值2.5）
--   view_history ≈ 100,000 条（每帖0-20浏览，均值10）
--   post_downloads ≈ 30,000 条（仅资源帖，每帖0-10下载）
-- 总磁盘占用：约 30-50 MB
-- 执行时间：约 1-3 分钟
-- ============================================================

-- 清空已有的关联数据（保留 users 和 posts）
TRUNCATE TABLE post_likes;
TRUNCATE TABLE post_stars;
TRUNCATE TABLE comment_likes;
TRUNCATE TABLE comments;
TRUNCATE TABLE view_history;
TRUNCATE TABLE post_downloads;

-- 清除 Redis 中的计数缓存（通过 MySQL 触发即可，Redis 由应用层管理）
-- 注意：Redis 缓存需手动清除，见脚本末尾说明

DELIMITER //

DROP PROCEDURE IF EXISTS sp_generate_interactions//

CREATE PROCEDURE sp_generate_interactions()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_id VARCHAR(36);
    DECLARE p_type VARCHAR(20);
    DECLARE p_count INT DEFAULT 0;
    DECLARE total_posts INT DEFAULT 0;
    DECLARE like_cnt INT;
    DECLARE star_cnt INT;
    DECLARE comment_cnt INT;
    DECLARE view_cnt INT;
    DECLARE dl_cnt INT;
    DECLARE cur CURSOR FOR SELECT id, post_type FROM posts WHERE deleted = 0;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SELECT COUNT(*) INTO total_posts FROM posts WHERE deleted = 0;
    SELECT CONCAT('开始生成关联数据，共 ', total_posts, ' 条帖子待处理...') AS info;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_id, p_type;
        IF done THEN LEAVE read_loop; END IF;
        SET p_count = p_count + 1;

        -- 点赞：每帖 0-30 个（资源帖偏多）
        SET like_cnt = IF(p_type = 'resource', FLOOR(RAND() * 31), FLOOR(RAND() * 21));
        SET @sql_like = CONCAT(
            'INSERT INTO post_likes (post_id, user_id, create_time) ',
            'SELECT ?, id, DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 259200) SECOND) ',
            'FROM users WHERE status = 1 ORDER BY RAND() LIMIT ', like_cnt,
            ' ON DUPLICATE KEY UPDATE post_id = post_id'
        );
        SET @pid = p_id;
        PREPARE stmt FROM @sql_like;
        EXECUTE stmt USING @pid;
        DEALLOCATE PREPARE stmt;

        -- 收藏：每帖 0-15 个
        SET star_cnt = FLOOR(RAND() * 16);
        SET @sql_star = CONCAT(
            'INSERT INTO post_stars (post_id, user_id, create_time) ',
            'SELECT ?, id, DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 259200) SECOND) ',
            'FROM users WHERE status = 1 ORDER BY RAND() LIMIT ', star_cnt,
            ' ON DUPLICATE KEY UPDATE post_id = post_id'
        );
        PREPARE stmt FROM @sql_star;
        EXECUTE stmt USING @pid;
        DEALLOCATE PREPARE stmt;

        -- 评论：每帖 0-5 条
        SET comment_cnt = FLOOR(RAND() * 6);
        SET @sql_comment = CONCAT(
            'INSERT INTO comments (id, post_id, user_id, content, like_count, status, create_time, update_time, deleted) ',
            'SELECT UUID(), ?, id, ',
            'ELT(1 + FLOOR(RAND() * 20), ',
            '''感谢分享！'', ''很有帮助，谢谢！'', ''正好需要这个资料'', ''学到了，感谢楼主'', ',
            '''这个总结太全了'', ''mark一下，回头细看'', ''请问有更新版吗？'', ''收藏了，慢慢看'', ',
            '''楼主太强了！'', ''请问有电子版吗？'', ''正好在复习这一块'', ''真题太有用了'', ',
            '''感谢学长的分享'', ''参考了你的笔记，考了90+'', ''这个方法不错'', ''我也遇到同样的问题'', ',
            '''已解决，谢谢！'', ''学习了'', ''赞一个''), ',
            'FLOOR(RAND() * 20), 1, ',
            'DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 259200) SECOND), ',
            'DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 259200) SECOND), 0 ',
            'FROM users WHERE status = 1 ORDER BY RAND() LIMIT ', comment_cnt
        );
        PREPARE stmt FROM @sql_comment;
        EXECUTE stmt USING @pid;
        DEALLOCATE PREPARE stmt;

        -- 浏览历史：每帖 0-20 条
        SET view_cnt = FLOOR(RAND() * 21);
        SET @sql_view = CONCAT(
            'INSERT INTO view_history (post_id, user_id, view_time) ',
            'SELECT ?, id, DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 259200) SECOND) ',
            'FROM users WHERE status = 1 ORDER BY RAND() LIMIT ', view_cnt
        );
        PREPARE stmt FROM @sql_view;
        EXECUTE stmt USING @pid;
        DEALLOCATE PREPARE stmt;

        -- 下载记录：仅资源帖，每帖 0-10 条
        IF p_type = 'resource' THEN
            SET dl_cnt = FLOOR(RAND() * 11);
            SET @sql_dl = CONCAT(
                'INSERT INTO post_downloads (post_id, user_id, download_time) ',
                'SELECT ?, id, DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 259200) SECOND) ',
                'FROM users WHERE status = 1 ORDER BY RAND() LIMIT ', dl_cnt
            );
            PREPARE stmt FROM @sql_dl;
            EXECUTE stmt USING @pid;
            DEALLOCATE PREPARE stmt;
        END IF;

        -- 更新帖子计数字段，与关联表实际记录数一致
        UPDATE posts SET
            like_count = (SELECT COUNT(*) FROM post_likes WHERE post_id = p_id),
            star_count = (SELECT COUNT(*) FROM post_stars WHERE post_id = p_id),
            comment_count = (SELECT COUNT(*) FROM comments WHERE post_id = p_id AND deleted = 0),
            view_count = view_count + (SELECT COUNT(*) FROM view_history WHERE post_id = p_id)
        WHERE id = p_id;

        IF p_count % 1000 = 0 THEN
            SELECT CONCAT('进度: ', p_count, ' / ', total_posts, ' 帖子') AS progress;
        END IF;
    END LOOP;
    CLOSE cur;

    SELECT CONCAT('✅ 关联数据生成完成！共处理 ', p_count, ' 条帖子') AS result;
    SELECT
        (SELECT COUNT(*) FROM post_likes) AS total_likes,
        (SELECT COUNT(*) FROM post_stars) AS total_stars,
        (SELECT COUNT(*) FROM comments) AS total_comments,
        (SELECT COUNT(*) FROM view_history) AS total_views,
        (SELECT COUNT(*) FROM post_downloads) AS total_downloads;
END//

DELIMITER ;

CALL sp_generate_interactions();
DROP PROCEDURE IF EXISTS sp_generate_interactions;

-- ============================================================
-- 执行后需手动清除 Redis 缓存（让应用层重新加载计数）
-- docker exec campushare-redis redis-cli FLUSHDB
-- 或选择性清除：
-- docker exec campushare-redis redis-cli --scan --pattern 'post:*' | xargs -r docker exec -i campushare-redis redis-cli DEL
-- ============================================================
