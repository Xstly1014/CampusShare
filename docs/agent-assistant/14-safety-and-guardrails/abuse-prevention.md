# 滥用防护

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[10-tools-and-apis/internal-api-design.md](../10-tools-and-apis/internal-api-design.md)

## 一、滥用场景与风险

Agent 每次 LLM 调用都有成本(DeepSeek-V3 约 ¥0.01-0.05/次),且工具调用会触发下游服务负载。滥用场景:

| 场景 | 风险 |
|------|------|
| 高频对话刷量 | LLM API 成本失控 |
| 超长输入攻击 | Token 消耗激增 |
| 死循环工具调用 | 下游服务过载 |
| 并发会话 | 单用户占满连接池 |
| 脚本自动化调用 | 批量滥用 |

## 二、限流策略

### 2.1 多维限流

```
┌──────────────────────────────────────────────────────┐
│                  限流维度                               │
├──────────────────────────────────────────────────────┤
│                                                        │
│  单用户维度                                             │
│  ├── 每分钟消息数:  10 条/min                          │
│  ├── 每小时消息数:  100 条/h                           │
│  ├── 每日消息数:    500 条/day                        │
│  ├── 并发会话数:    1 个 (同一用户同时只能 1 个活跃会话) │
│  └── 单会话轮数:    50 轮                              │
│                                                        │
│  全局维度                                               │
│  ├── 全局 QPS:      50/s (所有用户合计)                │
│  ├── 全局并发:      100 个活跃会话                     │
│  └── 日总调用:      10000 次 (成本上限)                │
│                                                        │
│  LLM 维度                                              │
│  ├── 单次会话 LLM 调用: ≤ 5 次 (防反思死循环)          │
│  └── 单次工具调用链:   ≤ 3 次 (防工具递归)             │
│                                                        │
└──────────────────────────────────────────────────────┘
```

### 2.2 实现:Redis 滑动窗口 + Lua

```lua
-- 滑动窗口限流 Lua 脚本
-- KEYS[1] = 限流 key (如 rate:user:123:minute)
-- ARGV[1] = 窗口大小 (秒)
-- ARGV[2] = 窗口内最大请求数
-- ARGV[3] = 当前时间戳

local key = KEYS[1]
local window = tonumber(ARGV[1])
local max_requests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local window_start = now - window

-- 清除窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- 当前窗口内请求数
local current = redis.call('ZCARD', key)

if current >= max_requests then
    return 0  -- 限流
end

-- 记录本次请求
redis.call('ZADD', key, now, now)
redis.call('EXPIRE', key, window)

return 1  -- 放行
```

```java
@Component
public class AgentRateLimiter {

    @Autowired private StringRedisTemplate redis;

    public RateLimitResult check(String userId) {
        // 1. 每分钟限流
        if (!tryAcquire("rate:" + userId + ":minute", 60, 10)) {
            return RateLimitResult.blocked("RATE_LIMIT_MINUTE",
                "请求过于频繁,请稍后再试 (每分钟最多 10 条消息)");
        }

        // 2. 每小时限流
        if (!tryAcquire("rate:" + userId + ":hour", 3600, 100)) {
            return RateLimitResult.blocked("RATE_LIMIT_HOUR",
                "本小时消息数已达上限,请稍后再试");
        }

        // 3. 每日限流
        if (!tryAcquire("rate:" + userId + ":day", 86400, 500)) {
            return RateLimitResult.blocked("RATE_LIMIT_DAY",
                "今日消息数已达上限,明日重置");
        }

        // 4. 并发会话检查
        String activeSession = redis.opsForValue().get("active_session:" + userId);
        if (activeSession != null) {
            return RateLimitResult.blocked("CONCURRENT_SESSION",
                "你有一个进行中的会话,请先完成或关闭");
        }

        return RateLimitResult.passed();
    }

    private boolean tryAcquire(String key, int windowSec, int maxReq) {
        Long result = redis.execute(
            rateLimitScript,
            List.of(key),
            String.valueOf(windowSec),
            String.valueOf(maxReq),
            String.valueOf(System.currentTimeMillis())
        );
        return result != null && result == 1;
    }
}
```

### 2.3 限流响应

被限流时返回 HTTP 429 + 友好提示:
```json
{
  "code": 429,
  "message": "请求过于频繁,请稍后再试",
  "detail": "每分钟最多 10 条消息",
  "retry_after": 30
}
```

前端展示 Toast(符合用户偏好:用 Toast 而非 alert)。

## 三、配额管理

### 3.1 用户配额

```sql
CREATE TABLE agent_user_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    daily_limit INT DEFAULT 500 COMMENT '日消息上限',
    daily_used INT DEFAULT 0 COMMENT '今日已用',
    monthly_limit INT DEFAULT 10000 COMMENT '月消息上限',
    monthly_used INT DEFAULT 0,
    last_reset_daily DATE COMMENT '上次日重置日期',
    last_reset_monthly DATE,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 用户配额';
```

### 3.2 配额重置
- 日配额:每日 00:00 重置(定时任务)。
- 月配额:每月 1 日 00:00 重置。
- 配额耗尽时返回:
  ```
  今日助手使用次数已达上限(500次),明日 00:00 重置。
  ```

### 3.3 全局成本熔断

```yaml
# application.yml
agent:
  cost:
    daily-budget: 100  # 日成本预算 (元)
    monthly-budget: 2000  # 月成本预算
    circuit-breaker:
      enabled: true
      daily-threshold-pct: 0.9  # 日预算 90% 触发预警
      daily-block-pct: 1.0  # 日预算 100% 触发熔断
```

```java
@Component
public class CostCircuitBreaker {

    @Scheduled(cron = "0 */5 * * * *")  // 每5分钟检查
    public void checkBudget() {
        double todayCost = costTracker.getTodayCost();
        double budget = config.getDailyBudget();

        if (todayCost >= budget) {
            // 熔断:停止接受新会话,只允许已有会话完成
            circuitBreaker.trip("DAILY_BUDGET_EXCEEDED");
            alertService.sendCritical("Agent 日成本超预算: ¥" + todayCost);
        } else if (todayCost >= budget * 0.9) {
            alertService.sendWarning("Agent 日成本接近预算: ¥" + todayCost);
        }
    }
}
```

熔断后:
- 新会话创建请求返回 503 + 「助手暂时不可用,稍后再试」。
- 已有活跃会话允许完成(但不再触发反思/重排等额外 LLM 调用)。

## 四、工具调用防滥用

### 4.1 工具调用次数限制

单轮对话中:
- **工具调用上限**:3 次(防 ReAct 死循环)。
- **同名工具重复调用**:2 次上限(防重复检索)。
- **工具调用总耗时**:10 秒上限。

```java
public class ToolCallGuard {
    private static final int MAX_TOOL_CALLS_PER_TURN = 3;
    private static final int MAX_SAME_TOOL_CALLS = 2;
    private static final int TOOL_TIMEOUT_SEC = 10;

    public void check(String turnId, List<ToolCall> history, String toolName) {
        // 1. 总次数
        if (history.size() >= MAX_TOOL_CALLS_PER_TURN) {
            throw new ToolCallLimitExceededException("单轮工具调用超上限");
        }

        // 2. 同名工具次数
        long sameToolCount = history.stream()
            .filter(t -> t.getName().equals(toolName)).count();
        if (sameToolCount >= MAX_SAME_TOOL_CALLS) {
            throw new ToolCallLimitExceededException("工具 " + toolName + " 调用超上限");
        }
    }
}
```

### 4.2 工具调用降级链(见 10-tools-and-apis/error-handling.md)
工具调用失败时的降级,避免持续重试:
1. 首次失败:重试 1 次(指数退避 500ms)。
2. 二次失败:返回 error tool_result 给 LLM,LLM 决定是否换工具。
3. 三次失败:强制结束工具调用,LLM 基于已有信息回答。

### 4.3 LLM 调用次数限制
单次会话:
- **生成调用**:≤ 5 次(主生成 + 反思 + 澄清)。
- **embedding 调用**:≤ 3 次(查询改写 + 检索)。
- **reranker 调用**:≤ 1 次。

超限时:
- 生成调用超限:返回已有最佳回答。
- Embedding 超限:跳过查询改写,用原始 query 检索。
- Reranker 超限:跳过重排,用 RRF 融合结果。

## 五、异常行为检测

### 5.1 行为画像

```sql
CREATE TABLE agent_user_behavior (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,

    message_count INT DEFAULT 0,
    avg_message_length INT DEFAULT 0,
    avg_session_depth DECIMAL(4,2) DEFAULT 0,
    tool_call_count INT DEFAULT 0,
    blocked_count INT DEFAULT 0 COMMENT '被限流/拦截次数',
    injection_attempt INT DEFAULT 0 COMMENT '注入尝试次数',

    -- 异常标记
    is_abnormal BOOLEAN DEFAULT FALSE,
    anomaly_score DECIMAL(4,3) DEFAULT 0,
    anomaly_reasons JSON,

    UNIQUE KEY uk_user_date (user_id, stat_date),
    INDEX idx_anomaly (is_abnormal, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 用户行为画像';
```

### 5.2 异常检测规则

| 规则 | 阈值 | 动作 |
|------|------|------|
| 日消息数 | > 300(正常用户 <100) | 标记异常 |
| 平均消息长度 | < 5 字符(刷量) | 标记异常 |
| 注入尝试 | ≥ 3 次/日 | 标记异常 |
| 被拦截率 | > 20% | 标记异常 |
| 深夜高频(2-6点) | > 50 条/小时 | 标记异常 |

### 5.3 自动封禁
- 异常评分 > 0.8:自动封禁 agent 使用权限 24 小时。
- 封禁期间:返回「账号异常,助手暂停服务,请联系客服」。
- 申诉:用户可联系客服申诉。

## 六、成本监控

### 6.1 实时成本追踪

```java
@Component
public class CostTracker {

    @Autowired private StringRedisTemplate redis;

    // 每次 LLM 调用后记录成本
    public void recordCost(String userId, String model, int inputTokens, int outputTokens) {
        double cost = calculateCost(model, inputTokens, outputTokens);

        // Redis 实时累加
        redis.opsForValue().increment("cost:daily:" + today(), cost);
        redis.opsForValue().increment("cost:monthly:" + month(), cost);
        redis.opsForValue().increment("cost:user:" + userId + ":" + today(), cost);

        // 异步写入 MySQL (每小时批量)
        costQueue.offer(new CostRecord(userId, model, inputTokens, outputTokens, cost, Instant.now()));
    }

    private double calculateCost(String model, int inputTokens, int outputTokens) {
        return switch (model) {
            case "deepseek-v3" -> inputTokens * 0.000001 + outputTokens * 0.000002;
            case "deepseek-r1" -> inputTokens * 0.000004 + outputTokens * 0.000016;
            case "doubao-pro" -> inputTokens * 0.0000008 + outputTokens * 0.000002;
            default -> 0.000001 * (inputTokens + outputTokens);
        };
    }
}
```

### 6.2 成本看板

```
┌──────────────────────────────────────────────────────┐
│              Agent 成本看板 (实时)                      │
├──────────────────────────────────────────────────────┤
│                                                        │
│  今日成本: ¥23.50 / ¥100 (23.5%)  ✓                    │
│  本月成本: ¥456.20 / ¥2000 (22.8%)  ✓                  │
│                                                        │
│  按模型分布:                                           │
│  ├── DeepSeek-V3:  ¥18.20 (77%)                       │
│  ├── DeepSeek-R1:  ¥3.80 (16%)                        │
│  └── 豆包 Pro:     ¥1.50 (6%)                         │
│                                                        │
│  按意图分布:                                           │
│  ├── SEARCH:  ¥12.30 (52%)                            │
│  ├── HOW_TO:  ¥6.50 (28%)                             │
│  └── 其他:    ¥4.70 (20%)                             │
│                                                        │
│  Top 5 高成本用户:                                     │
│  ├── user_8842:  ¥2.30 (123次调用)                     │
│  ├── user_1024:  ¥1.80 (98次)                         │
│  └── ...                                              │
│                                                        │
│  平均单次对话成本: ¥0.047                              │
│  目标: ≤ ¥0.05  ✓                                     │
│                                                        │
└──────────────────────────────────────────────────────┘
```

## 七、决策记录

### ADR-180: 滥用防护 - 多维限流 + 成本熔断 + 行为检测
- **背景**:Agent 每次调用有成本,需防止滥用导致成本失控和服务过载。
- **决策**:
  - 多维限流:分钟/小时/日 + 并发会话 + 全局 QPS,Redis 滑动窗口 Lua 实现。
  - 用户配额:日 500 条/月 10000 条,自动重置。
  - 成本熔断:日预算 90% 预警、100% 熔断(停止新会话)。
  - 工具调用限制:单轮 ≤3 次,同名 ≤2 次,10s 超时。
  - LLM 调用限制:单会话生成 ≤5 次,防反思死循环。
  - 行为检测:异常评分 >0.8 自动封禁 24h。
- **理由**:LLM 应用成本敏感,多层防护避免单点失效;参考 OpenAI/Dify 的限流实践。
- **权衡**:严格限流可能误伤重度用户,但 MVP 阶段保守优先。
- **状态**:采纳
