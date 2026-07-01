-- ========================================
-- CampusShare Agent Service - MySQL 表初始化
-- ========================================
-- 用途：agent-service 专属业务表（会话/轮次/记忆/知识库/工具注册）
-- 归属：仅 agent-service 可直接读写，其他服务通过 Feign 调用
-- 执行方式：进入 MySQL 容器手动执行
--   docker exec -i campushare-mysql mysql -uroot -proot123456 campushare < agent-init.sql
-- 注意：所有表用 CREATE TABLE IF NOT EXISTS，可在已有库上安全重复执行
-- ========================================

USE campushare;

-- ========================================
-- 1. 会话主表
-- ========================================
CREATE TABLE IF NOT EXISTS agent_sessions (
  id VARCHAR(36) PRIMARY KEY COMMENT '会话ID（UUID）',
  user_id VARCHAR(36) NOT NULL COMMENT '用户ID',
  status ENUM('INIT','ACTIVE','TOOL_CALLING','WAITING_CLARIFY','REFLECTING','ARCHIVED','CLOSED','ERROR') NOT NULL COMMENT '会话状态机',
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
  last_active_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  closed_at DATETIME COMMENT '会话关闭时间',
  turn_count INT DEFAULT 0 COMMENT '已进行轮次数',
  prompt_version VARCHAR(16) COMMENT 'Prompt 版本（用于 A/B 测试和回溯）',
  llm_model VARCHAR(32) COMMENT '使用的 LLM 模型名',
  intent_summary VARCHAR(256) COMMENT '意图摘要（会话级主题）',
  total_input_tokens INT DEFAULT 0 COMMENT '累计输入 token 数',
  total_output_tokens INT DEFAULT 0 COMMENT '累计输出 token 数',
  total_cost_yuan DECIMAL(10,4) DEFAULT 0 COMMENT '累计成本（元）',
  feedback_positive INT DEFAULT 0 COMMENT '正面反馈数',
  feedback_negative INT DEFAULT 0 COMMENT '负面反馈数',
  quality_score DECIMAL(3,2) COMMENT '质量评分（LLM-as-Judge）',
  error_reason VARCHAR(256) COMMENT '错误原因（status=ERROR 时）',
  INDEX idx_user_active (user_id, status, last_active_at) COMMENT '用户活跃会话查询',
  INDEX idx_status_archived (status, last_active_at) COMMENT '归档清理查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 会话主表';

-- ========================================
-- 2. 轮次记录表
-- ========================================
CREATE TABLE IF NOT EXISTS agent_turns (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  turn_id INT NOT NULL COMMENT '轮次序号（会话内自增）',
  user_query TEXT COMMENT '用户提问内容',
  intent VARCHAR(32) COMMENT '识别的意图类型',
  intent_confidence DECIMAL(3,2) COMMENT '意图置信度（0-1）',
  tools_called JSON COMMENT '调用的工具列表',
  tool_versions JSON COMMENT '工具版本（用于回溯）',
  retrieval_refs JSON COMMENT '检索引用列表',
  assistant_answer TEXT COMMENT '助手回答内容',
  input_tokens INT COMMENT '本轮输入 token 数',
  output_tokens INT COMMENT '本轮输出 token 数',
  latency_ms INT COMMENT '本轮总延迟（毫秒）',
  cost_yuan DECIMAL(8,4) COMMENT '本轮成本（元）',
  feedback ENUM('UP','DOWN') COMMENT '用户反馈：UP-赞，DOWN-踩',
  feedback_reason VARCHAR(64) COMMENT '反馈原因（踩时填写）',
  context_snapshot_id BIGINT COMMENT '关联的上下文快照ID',
  interrupted TINYINT DEFAULT 0 COMMENT '是否被用户中断',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_session_turn (session_id, turn_id) COMMENT '会话内轮次唯一',
  INDEX idx_session (session_id, turn_id) COMMENT '会话轮次查询',
  INDEX idx_feedback (feedback, created_at) COMMENT '反馈统计查询'
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
  tags JSON COMMENT '标签列表',
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
