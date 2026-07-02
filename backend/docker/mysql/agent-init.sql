-- ========================================
-- CampusShare Agent Service - MySQL 表初始化
-- ========================================
-- 用途：agent-service 专属业务表（会话/轮次/记忆/知识库/工具注册）
-- 归属：仅 agent-service 可直接读写，其他服务通过 Feign 调用
-- 执行方式：进入 MySQL 容器手动执行
--   docker exec -i campushare-mysql mysql -uroot -proot123456 campushare < agent-init.sql
-- 注意：agent_sessions / agent_turns 用 DROP+CREATE 重建（字段与实体类严格对齐）
--       其余表用 CREATE TABLE IF NOT EXISTS，可在已有库上安全重复执行
-- ========================================

USE campushare;

-- ========================================
-- 1. 会话主表（字段与 AgentSession 实体类对齐）
--    主键 id 为 UUID（IdType.ASSIGN_UUID），非自增
-- ========================================
DROP TABLE IF EXISTS agent_sessions;
CREATE TABLE agent_sessions (
  id VARCHAR(36) PRIMARY KEY COMMENT '会话ID（UUID，MyBatis Plus ASSIGN_UUID）',
  user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
  title VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态（ACTIVE/ARCHIVED/CLOSED/ERROR）',
  message_count INT DEFAULT 0 COMMENT '消息轮次数',
  total_tokens INT DEFAULT 0 COMMENT '累计 token 数',
  total_cost DECIMAL(10,4) DEFAULT 0 COMMENT '累计成本',
  last_message_at DATETIME COMMENT '最后消息时间',
  metadata TEXT COMMENT '元数据（JSON）',
  category_id VARCHAR(36) DEFAULT NULL COMMENT '所属分类ID（NULL=未分类）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（MyMetaObjectHandler 自动填充）',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（MyMetaObjectHandler 自动填充）',
  INDEX idx_user_status (user_id, status) COMMENT '按用户查活跃会话',
  INDEX idx_last_message (last_message_at) COMMENT '按最后消息时间排序',
  INDEX idx_user_category (user_id, category_id) COMMENT '按用户分类查会话'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 会话主表';

-- ========================================
-- 2. 轮次记录表（字段与 AgentTurn 实体类对齐）
--    主键 id 为 UUID（IdType.ASSIGN_UUID），非自增
-- ========================================
DROP TABLE IF EXISTS agent_turns;
CREATE TABLE agent_turns (
  id VARCHAR(36) PRIMARY KEY COMMENT '轮次ID（UUID，MyBatis Plus ASSIGN_UUID）',
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  turn_number INT NOT NULL COMMENT '轮次序号（会话内自增）',
  user_message TEXT COMMENT '用户消息内容',
  assistant_message TEXT COMMENT '助手回答内容',
  message_role VARCHAR(16) COMMENT '消息角色（user/assistant）',
  tokens_used INT COMMENT '本轮使用 token 数',
  model_name VARCHAR(64) COMMENT '使用的模型名',
  retrieval_context TEXT COMMENT '检索上下文（JSON）',
  tools_used VARCHAR(512) COMMENT '使用的工具列表（JSON）',
  response_time_ms INT COMMENT '响应时间（毫秒）',
  status VARCHAR(32) NOT NULL DEFAULT 'STREAMING' COMMENT '轮次状态（STREAMING/COMPLETED/ERROR）',
  error_message VARCHAR(512) COMMENT '错误信息（status=ERROR 时）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（MyMetaObjectHandler 自动填充）',
  INDEX idx_session_turn (session_id, turn_number) COMMENT '会话轮次查询',
  INDEX idx_session_status (session_id, status) COMMENT '按状态查历史轮次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 轮次记录表';

-- ========================================
-- 3. 上下文快照表（调试与回放）
-- ========================================
CREATE TABLE IF NOT EXISTS agent_context_snapshots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  turn_id INT NOT NULL COMMENT '轮次序号',
  messages_json LONGTEXT COMMENT '发送给 LLM 的完整 messages（JSON）',
  layer_tokens JSON COMMENT '各上下文层 token 占用',
  total_input_tokens INT COMMENT '总输入 token 数',
  used_memory_ids JSON COMMENT '使用的长期记忆ID列表',
  truncated TINYINT DEFAULT 0 COMMENT '是否发生截断',
  truncation_reason VARCHAR(64) COMMENT '截断原因（如 token 超限）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_session_turn (session_id, turn_id) COMMENT '会话轮次查询',
  INDEX idx_created (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 上下文快照表';

-- ========================================
-- 4. 工具注册表（运行时可配置）
-- ========================================
CREATE TABLE IF NOT EXISTS agent_tool_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  tool_name VARCHAR(64) NOT NULL UNIQUE COMMENT '工具唯一标识名',
  display_name VARCHAR(64) NOT NULL COMMENT '工具显示名称',
  description TEXT NOT NULL COMMENT '工具描述（给 LLM 看）',
  parameters_schema JSON NOT NULL COMMENT '参数 JSON Schema',
  returns_schema JSON NOT NULL COMMENT '返回值 JSON Schema',
  category VARCHAR(32) NOT NULL COMMENT '工具分类',
  applicable_intents JSON NOT NULL COMMENT '适用的意图列表',
  timeout_ms INT DEFAULT 5000 COMMENT '超时时间（毫秒）',
  max_retries INT DEFAULT 1 COMMENT '最大重试次数',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用',
  version VARCHAR(16) DEFAULT 'v1' COMMENT '工具版本',
  feign_target VARCHAR(128) COMMENT 'Feign 目标服务（如 post-service）',
  handler_class VARCHAR(128) COMMENT '处理器类全限定名',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具注册表';

-- ========================================
-- 5. 工具错误归档表
-- ========================================
CREATE TABLE IF NOT EXISTS agent_tool_errors (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  session_id VARCHAR(36) COMMENT '会话ID',
  turn_id INT COMMENT '轮次序号',
  tool_name VARCHAR(64) COMMENT '工具名称',
  error_code VARCHAR(32) COMMENT '错误码',
  error_message TEXT COMMENT '错误信息',
  args_json JSON COMMENT '调用参数（JSON）',
  retry_count INT COMMENT '重试次数',
  degraded TINYINT COMMENT '是否降级处理',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_tool_created (tool_name, created_at) COMMENT '按工具查错误',
  INDEX idx_session (session_id) COMMENT '按会话查错误'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具错误归档表';

-- ========================================
-- 6. 会话状态转移事件表（状态机审计）
-- ========================================
CREATE TABLE IF NOT EXISTS agent_session_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  from_status VARCHAR(32) COMMENT '转移前状态',
  to_status VARCHAR(32) NOT NULL COMMENT '转移后状态',
  reason VARCHAR(128) COMMENT '转移原因',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_session (session_id, created_at) COMMENT '按会话查事件'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 会话状态转移事件表';

-- ========================================
-- 7. 用户长期记忆表
-- ========================================
CREATE TABLE IF NOT EXISTS user_memory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
  memory_type VARCHAR(32) NOT NULL COMMENT '记忆类型（PREFERENCE/FACT/SKILL等）',
  memory_key VARCHAR(64) NOT NULL COMMENT '记忆键（如 preferred_language）',
  memory_value TEXT NOT NULL COMMENT '记忆值',
  confidence DECIMAL(3,2) DEFAULT 1.00 COMMENT '置信度（0-1，会衰减）',
  source VARCHAR(16) NOT NULL COMMENT '来源：EXPLICIT-用户明说，INFERRED-行为推断',
  evidence_count INT DEFAULT 1 COMMENT '证据数量',
  conflict_flag TINYINT DEFAULT 0 COMMENT '是否有冲突',
  volatile_flag TINYINT DEFAULT 0 COMMENT '是否易变（如当前心情）',
  last_used_at DATETIME COMMENT '最后使用时间',
  deleted_at DATETIME COMMENT '软删除时间（30天回收站）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_type_key (user_id, memory_type, memory_key) COMMENT '用户记忆唯一键',
  INDEX idx_user_updated (user_id, updated_at) COMMENT '按更新时间查询',
  INDEX idx_user_type (user_id, memory_type) COMMENT '按类型查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户长期记忆表';

-- ========================================
-- 8. 行为证据表（记忆推断依据）
-- ========================================
CREATE TABLE IF NOT EXISTS user_memory_evidence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
  memory_id BIGINT COMMENT '关联的记忆ID',
  session_id VARCHAR(36) COMMENT '来源会话ID',
  evidence_type VARCHAR(32) COMMENT '证据类型（QUERY/FEEDBACK/TOOL_CALL）',
  evidence_payload JSON COMMENT '证据内容（JSON）',
  processed TINYINT DEFAULT 0 COMMENT '是否已处理（0-待处理，1-已处理）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user_unprocessed (user_id, processed, created_at) COMMENT '拉取未处理证据',
  INDEX idx_memory (memory_id, created_at) COMMENT '按记忆查证据'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为证据表';

-- ========================================
-- 9. 记忆历史表（软删除审计）
-- ========================================
CREATE TABLE IF NOT EXISTS user_memory_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
  memory_type VARCHAR(32) COMMENT '记忆类型',
  memory_key VARCHAR(64) COMMENT '记忆键',
  memory_value TEXT COMMENT '记忆值（变更前的快照）',
  confidence DECIMAL(3,2) COMMENT '置信度',
  source VARCHAR(16) COMMENT '来源',
  action VARCHAR(16) NOT NULL COMMENT '操作：UPDATE/DELETE/DECAY',
  reason VARCHAR(128) COMMENT '操作原因',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user (user_id, created_at) COMMENT '按用户查历史'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户记忆历史表（审计）';

-- ========================================
-- 10. 知识库文档表
-- ========================================
CREATE TABLE IF NOT EXISTS knowledge_articles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  title VARCHAR(128) NOT NULL COMMENT '文档标题',
  topic VARCHAR(32) NOT NULL COMMENT '主题分类（如 REGISTRATION/POSTING/NOTIFICATION）',
  content MEDIUMTEXT NOT NULL COMMENT '文档正文（Markdown）',
  content_md5 CHAR(32) NOT NULL COMMENT '内容 MD5（检测变更，避免重复 embedding）',
  status ENUM('DRAFT','PUBLISHED','DEPRECATED') DEFAULT 'PUBLISHED' COMMENT '发布状态',
  version INT DEFAULT 1 COMMENT '版本号',
  tags VARCHAR(256) DEFAULT NULL COMMENT '标签列表（逗号分隔）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_topic_status (topic, status) COMMENT '按主题查已发布文档',
  INDEX idx_updated (updated_at) COMMENT '按更新时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- ========================================
-- 11. 异步写入队列表（写入失败重试）
-- ========================================
CREATE TABLE IF NOT EXISTS agent_pending_writes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  target_table VARCHAR(64) NOT NULL COMMENT '目标表名',
  payload JSON NOT NULL COMMENT '写入数据（JSON）',
  retries INT DEFAULT 0 COMMENT '已重试次数',
  next_retry_at DATETIME COMMENT '下次重试时间',
  status ENUM('PENDING','SUCCESS','FAILED') DEFAULT 'PENDING' COMMENT '处理状态',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_status_retry (status, next_retry_at) COMMENT '拉取待重试任务'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 异步写入队列表';

-- ========================================
-- 12. 会话分类表（用户自建文件夹，用于会话归类）
-- ========================================
CREATE TABLE IF NOT EXISTS agent_session_categories (
  id VARCHAR(36) PRIMARY KEY COMMENT '分类ID（UUID，MyBatis Plus ASSIGN_UUID）',
  user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
  name VARCHAR(64) NOT NULL COMMENT '分类名称',
  sort_order INT DEFAULT 0 COMMENT '排序（越小越靠前）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（MyMetaObjectHandler 自动填充）',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（MyMetaObjectHandler 自动填充）',
  UNIQUE KEY uk_user_name (user_id, name) COMMENT '同用户分类名唯一',
  INDEX idx_user_sort (user_id, sort_order) COMMENT '按用户排序查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 会话分类表';

-- 兼容已存在库：为 agent_sessions 增加分类外键列与索引（幂等）
ALTER TABLE agent_sessions ADD COLUMN IF NOT EXISTS category_id VARCHAR(36) DEFAULT NULL COMMENT '所属分类ID（NULL=未分类）' AFTER metadata;
ALTER TABLE agent_sessions ADD INDEX IF NOT EXISTS idx_user_category (user_id, category_id);
