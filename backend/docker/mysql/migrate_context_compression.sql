-- ========================================
-- 上下文压缩持久化表
-- 对应 ADR-070：压缩结果写入MySQL持久化，Redis作为热缓存
-- ========================================

-- context_summaries: 滚动摘要持久化
CREATE TABLE IF NOT EXISTS context_summaries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    summary_text TEXT NOT NULL,
    covered_turn_ids VARCHAR(512) NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上下文压缩摘要';

-- context_slots: 槽位冻结持久化
CREATE TABLE IF NOT EXISTS context_slots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    slot_key VARCHAR(64) NOT NULL,
    slot_value VARCHAR(256) NOT NULL,
    frozen_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_slot (session_id, slot_key),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上下文槽位冻结';

-- pin_messages: Pin消息持久化
CREATE TABLE IF NOT EXISTS pin_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    turn_id INT NOT NULL,
    pinned_by ENUM('USER','AGENT') NOT NULL DEFAULT 'AGENT',
    reason VARCHAR(256),
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_turn (session_id, turn_id),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pin消息';
