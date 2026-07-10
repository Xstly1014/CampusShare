# 安全审计与合规

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[input-guardrails.md](./input-guardrails.md)、[content-safety.md](./content-safety.md)

## 一、审计的目标

安全审计回答三个问题:
1. **发生了什么**:完整记录 agent 的输入、输出、工具调用、安全事件。
2. **什么时候发生的**:精确时间戳,全链路可追溯。
3. **谁的责任**:区分系统故障 vs 用户滥用 vs 内容问题。

审计日志是合规要求(《生成式人工智能服务管理暂行办法》要求留存日志),也是事故排查的依据。

## 二、审计日志体系

### 2.1 日志分类

| 日志类型 | 内容 | 保留期 | 存储 |
|----------|------|--------|------|
| 会话日志 | 用户消息、agent 回答、工具调用 | 90 天 | MySQL + 对象存储 |
| 安全事件 | 注入尝试、有害内容、PII 泄露 | 180 天 | MySQL |
| 成本日志 | LLM 调用 token、费用 | 1 年 | MySQL |
| 访问日志 | API 请求、响应状态 | 30 天 | 文件 |
| 系统日志 | 异常堆栈、降级事件 | 30 天 | 文件 + Loki |

### 2.2 会话日志(agent_audit_logs)

```sql
CREATE TABLE agent_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_type ENUM('INPUT','OUTPUT','TOOL_CALL','SECURITY','SYSTEM') NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_id BIGINT COMMENT '关联 agent_turns.id',
    user_id BIGINT NOT NULL,

    -- 内容 (敏感字段加密)
    content_encrypted VARBINARY(65535) COMMENT 'AES-256 加密的内容',
    content_hash VARCHAR(64) COMMENT 'SHA-256 哈希 (用于完整性校验)',

    -- 安全标记
    security_flags JSON COMMENT '["PII_REDACTED","INJECTION_ATTEMPT"]',
    action_taken VARCHAR(32) COMMENT 'ALLOWED/BLOCKED/REDACTED',

    -- 上下文
    agent_version VARCHAR(32),
    intent VARCHAR(32),
    experiment_id VARCHAR(64),

    occurred_at DATETIME(3) NOT NULL COMMENT '毫秒精度',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_session (session_id),
    INDEX idx_user_time (user_id, occurred_at),
    INDEX idx_type_time (log_type, occurred_at),
    INDEX idx_security (security_flags(128))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 审计日志';
```

### 2.3 加密策略

- **PII 原文**:AES-256-GCM 加密存储,密钥由 KMS 管理(非硬编码)。
- **加密场景**:用户输入含 PII 时,脱敏文本送入 LLM,原文加密存审计日志。
- **解密权限**:仅安全审计角色可解密,需二次认证。

```java
@Component
public class AuditLogEncryptor {

    @Value("${audit.encryption.key-id}")
    private String keyId;

    public EncryptedContent encrypt(String plaintext) {
        // 1. 从 KMS 获取数据加密密钥 (DEK)
        DataKey dek = kmsClient.generateDataKey(keyId);

        // 2. AES-256-GCM 加密
        byte[] iv = SecureRandom.generateSeed(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
            new SecretKeySpec(dek.getPlaintext(), "AES"),
            new GCMParameterSpec(128, iv));

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(UTF_8));

        // 3. 返回密文 + DEK 密文 + IV
        return new EncryptedContent(encrypted, dek.getCiphertextBlob(), iv);
    }

    public String decrypt(EncryptedContent content) {
        // 需审计角色权限校验
        // ...
    }
}
```

### 2.4 完整性校验
- 每条日志计算 SHA-256 哈希,存 `content_hash`。
- 定期(每日)校验哈希,防止日志被篡改。
- 哈希不匹配的日志标记为「可疑」。

## 三、安全事件审计

### 3.1 安全事件分级

| 级别 | 类型 | 响应 |
|------|------|------|
| P0 - 紧急 | 有害内容泄露给用户、大规模 PII 泄露 | 立即下线 agent,1h 内响应 |
| P1 - 高危 | Prompt 注入成功、系统提示词泄露 | 4h 内响应,修复后上线 |
| P2 - 中危 | 注入尝试(被拦截)、幻觉率突增 | 24h 内分析 |
| P3 - 低危 | 单次 PII 检测、限流触发 | 周报统计 |

### 3.2 安全事件告警

```java
@Component
public class SecurityAlertHandler {

    public void handle(SecurityEvent event) {
        // 1. 写入 agent_security_events 表
        securityEventMapper.insert(event);

        // 2. 按级别告警
        switch (event.getSeverity()) {
            case P0:
                alertService.sendCritical(event);  // 电话 + 短信 + 钉钉
                incidentService.createIncident(event);  // 创建事故
                agentCircuitBreaker.emergencyTrip();  // 紧急熔断
                break;
            case P1:
                alertService.sendHigh(event);  // 钉钉 + 邮件
                break;
            case P2:
                alertService.sendMedium(event);  // 钉钉
                break;
            case P3:
                // 仅记录,不实时告警
                break;
        }

        // 3. 自动封禁(注入尝试 3 次/小时)
        if (event.getType().contains("INJECTION")) {
            long recentAttempts = securityEventMapper.countRecent(event.getUserId(), "INJECTION", Duration.ofHours(1));
            if (recentAttempts >= 3) {
                banService.banUser(event.getUserId(), Duration.ofHours(1), "REPEATED_INJECTION");
            }
        }
    }
}
```

### 3.3 安全事件看板

```
┌──────────────────────────────────────────────────────┐
│              Agent 安全事件看板 (近 7 天)               │
├──────────────────────────────────────────────────────┤
│                                                        │
│  P0 紧急: 0  ✓                                         │
│  P1 高危: 2  (注入成功 1, 提示词泄露 1)                │
│  P2 中危: 15 (注入尝试 10, 幻觉突增 5)                 │
│  P3 低危: 89 (PII 检测 45, 限流 44)                    │
│                                                        │
│  注入攻击趋势:                                         │
│  Mon ██ 3                                             │
│  Tue █ 1                                              │
│  Wed ████ 5                                           │
│  Thu ██ 2                                             │
│  Fri ██████ 8 ← 异常激增                               │
│                                                        │
│  Top 攻击用户:                                         │
│  ├── user_7741: 12 次注入尝试 (已封禁)                 │
│  ├── user_3022: 5 次                                   │
│  └── user_9981: 3 次                                   │
│                                                        │
│  有害内容拦截:                                         │
│  ├── VIOLENCE: 0                                      │
│  ├── ILLEGAL: 1 (已处理)                              │
│  └── PII_LEAK: 2 (已拦截)                             │
│                                                        │
└──────────────────────────────────────────────────────┘
```

## 四、合规要求

### 4.1 《生成式人工智能服务管理暂行办法》

中国大陆 AIGC 服务需遵守的关键条款:

| 条款 | 要求 | 实现措施 |
|------|------|----------|
| 第7条 | 训练数据合法 | 知识库内容由运营团队合法编写,不爬取版权内容 |
| 第9条 | 标识 AIGC 内容 | agent 回答标注「AI 生成」 |
| 第14条 | 用户输入日志留存 | 审计日志保留 90 天 |
| 第15条 | 违法内容处置 | 有害内容过滤 + 人工审核队列 |
| 第17条 | 投诉举报机制 | 用户举报功能 + 客服处理 |

### 4.2 AIGC 标识

Agent 回答必须明确标识为 AI 生成:

```tsx
// 前端:助手消息气泡底部
<div className="ai-badge">
  <Bot className="w-3 h-3" />
  <span>AI 生成,仅供参考</span>
</div>
```

### 4.3 算法备案

- 面向公众的 AIGC 服务需向网信办备案算法。
- 备案信息:算法类型、用途、风险评估、安全措施。
- 本项目作为校园项目,MVP 阶段以学习为目的;正式上线前需完成备案。

### 4.4 数据保护

| 数据类型 | 保护措施 |
|----------|----------|
| 用户消息 | 传输 HTTPS,存储 AES 加密 |
| PII | 脱敏后送 LLM,原文加密存储 |
| 审计日志 | 完整性哈希,访问需审计角色 |
| LLM API Key | 环境变量注入,不入代码库 |
| 知识库文档 | Git 版本化,变更可追溯 |

## 五、事故响应流程

### 5.1 事故分级与响应

```
P0 事故 (如:有害内容泄露)
├── 1. 自动紧急熔断 (agent 下线)
├── 2. 值班人员 1h 内响应
├── 3. 影响范围评估 (多少用户受影响)
├── 4. 修复 + 回归测试
├── 5. 复盘报告 (24h 内)
└── 6. 预防措施落地

P1 事故 (如:提示词泄露)
├── 1. 值班人员 4h 内响应
├── 2. 临时修复 (更新 prompt / 拦截规则)
├── 3. 回归测试
├── 4. 复盘报告 (48h 内)
└── 5. 预防措施
```

### 5.2 复盘报告模板

```markdown
## Agent 安全事故复盘 - [事故 ID]

### 事故概述
- 时间: 2026-XX-XX HH:MM
- 级别: P0/P1
- 影响: X 名用户, 持续 X 分钟

### 事件时间线
- HH:MM 检测到异常 (告警来源)
- HH:MM 紧急熔断
- HH:MM 修复部署
- HH:MM 恢复服务

### 根因分析
- 直接原因: ...
- 深层原因: ...
- 为什么没被前置防护拦截: ...

### 改进措施
| 措施 | 负责人 | 截止日期 | 状态 |
|------|--------|----------|------|
| 增加XX检测规则 | - | - | - |
| 更新prompt | - | - | - |
| 增加监控告警 | - | - | - |

### 经验教训
- ...
```

## 六、定期安全审查

### 6.1 审查频率

| 审查项 | 频率 | 执行人 |
|--------|------|--------|
| 敏感词库更新 | 周 | 运营 |
| 安全事件分析 | 周 | 安全负责人 |
| 红队测试 | 月 | 安全工程师 |
| 代码安全扫描 | PR | CI 自动 |
| 渗透测试 | 季度 | 外部 |
| 合规自查 | 季度 | 法务 + 技术 |

### 6.2 审查报告
季度安全审查报告需包含:
- 安全事件统计与趋势。
- 已知风险与修复进度。
- 合规状态。
- 下季度安全计划。

## 七、决策记录

### ADR-181: 安全审计与合规体系
- **背景**:AIGC 服务有合规要求,且事故排查需要完整日志。
- **决策**:
  - 审计日志:会话/安全/成本/访问/系统五类,会话日志保留 90 天。
  - 敏感内容 AES-256 加密存储,KMS 管理密钥,审计角色可解密。
  - 安全事件 P0-P3 分级,P0 紧急熔断 + 1h 响应。
  - 合规:遵循《生成式 AI 管理暂行办法》,AIGC 标识,日志留存,举报机制。
  - 事故响应:P0 24h 复盘,P1 48h 复盘,固定模板。
  - 定期审查:周/月/季度多频次。
- **理由**:合规是底线,审计是事故兜底;参考《生成式 AI 管理暂行办法》和 ISO 27001。
- **权衡**:加密存储增加复杂度,但 PII 保护是硬性要求。
- **状态**:采纳
