-- 通知偏好设置字段：3个开关
ALTER TABLE users ADD COLUMN notify_messages TINYINT DEFAULT 1 COMMENT '通知：是否接收新消息通知';
ALTER TABLE users ADD COLUMN notify_replies TINYINT DEFAULT 1 COMMENT '通知：是否接收帖子回复通知';
ALTER TABLE users ADD COLUMN notify_likes TINYINT DEFAULT 0 COMMENT '通知：是否接收点赞收藏通知';
