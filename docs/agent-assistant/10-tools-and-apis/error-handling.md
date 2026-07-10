# 工具调用错误处理、重试与降级

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

工具调用必然失败（网络/超时/上游故障/参数错误）。必须有清晰的错误处理链路，让 Agent 在部分工具失败时仍能给出可用回答，而非整个会话崩溃。

## 二、错误分类

### 2.1 按来源分

| 类别 | 来源 | 示例 |
|------|------|------|
| 参数错误 | LLM 生成的 args 不合规 | 必填字段缺失、类型错误、枚举值非法 |
| 鉴权错误 | 用户无权调用 | 普通用户调用管理类工具 |
| 超时错误 | 工具执行超时 | post-service 检索 >3s |
| 上游错误 | Feign 调用返回 5xx | post-service 内部异常 |
| 降级错误 | 降级路径也失败 | 向量检索失败 → BM25 也失败 |
| 预算错误 | 会话成本/次数耗尽 | 单会话工具调用 >5 次 |

### 2.2 按可恢复性分

| 类别 | 处理策略 |
|------|---------|
| 可重试 | 超时/上游 5xx，按重试策略重试 |
| 可降级 | 向量失败转 BM25，BM25 失败转空结果 |
| 不可恢复 | 参数错误/鉴权错误，返回错误让 LLM 自行决策 |
| 终止 | 预算耗尽，停止工具调用，强制让 LLM 用已有信息回答 |

## 三、重试策略

### 3.1 默认重试

```yaml
# 单工具重试配置
max_retries: 1                # 默认重试 1 次
retry_backoff: 200ms          # 指数退避起始 200ms
retry_backoff_multiplier: 2   # 每次翻倍
retry_on: [TIMEOUT, UPSTREAM_5XX]  # 仅这些错误重试
```

### 3.2 不重试的场景

- 参数错误：重试也是同样错误，不重试。
- 鉴权错误：不重试。
- 4xx 上游错误（除 429）：不重试。
- 降级路径：降级后不再重试原路径。

### 3.3 重试实现

```java
@Retryable(
    value = {ToolTimeoutException.class, FeignServerException.class},
    maxAttempts = 2,
    backoff = @Backoff(delay = 200, multiplier = 2)
)
public ToolResult executeTool(ToolCall call) { ... }
```

用 Spring Retry 注解，统一拦截。

## 四、降级链

每个工具定义自己的降级链，按顺序尝试：

### 4.1 search_posts 降级链

```
1. 向量+BM25+结构化 混合检索（默认）
   │ 失败（向量服务挂）
   ▼
2. 仅 BM25+结构化 检索（跳过向量）
   │ 失败（post-service 整体不可用）
   ▼
3. 返回空结果 + degraded=true
   │ （让 LLM 用通用知识回答）
```

### 4.2 search_knowledge 降级链

```
1. 向量检索知识库
   │ 失败
   ▼
2. 关键词 LIKE 检索
   │ 失败
   ▼
3. 返回空 + 建议用户查看帮助中心 URL
```

### 4.3 get_post_detail 降级链

```
1. Feign 调用 post-service
   │ 失败
   ▼
2. 返回 summary="详情暂不可用"，data 仅含 post_id
   │ （LLM 可基于 search_posts 的摘要回答）
```

## 五、错误返回给 LLM 的格式

工具失败时，tool_result 仍要返回（不能让 LLM 等待）：

```json
{
  "tool_call_id": "call_abc123",
  "role": "tool",
  "content": {
    "status": "error",
    "error_code": "TOOL_TIMEOUT",
    "error_message": "检索服务超时，已尝试降级但失败",
    "degraded": true,
    "partial_data": null,
    "suggestion": "可以告诉用户'暂时无法查询，请稍后再试'或基于已有信息回答"
  }
}
```

`suggestion` 字段引导 LLM 选择合理的应对策略，避免 LLM 反复重试同一工具。

## 六、Agent 循环内的错误处理

### 6.1 单工具失败

LLM 收到错误 tool_result 后，自行决策：
- 换一个工具（如 search_posts 失败 → 改用 get_hot_posts）。
- 用已有信息回答。
- 主动追问用户（CLARIFY）。

### 6.2 连续多工具失败

若同一轮内 ≥3 个工具失败，强制中断 Agent 循环：
- 返回已收集的部分结果。
- 在回答末尾附加"部分信息可能不准确，建议稍后重试"。
- 记录 `multi_tool_failure=true` 告警。

### 6.3 全部工具失败

返回兜底回答：
```
抱歉，我暂时无法查询到相关信息。你可以：
1. 直接访问 [仓库](/warehouse) 浏览资源
2. 查看 [帮助中心](/help)
3. 稍后再问我
```

## 七、LLM 调用本身失败的错误处理

### 7.1 意图分类 LLM 失败

- 默认走 CLARIFY 路径（让 LLM 追问用户意图）。
- 不直接报错，因为意图分类失败不代表不能对话。

### 7.2 主生成 LLM 失败

- 重试 1 次（不同 provider：DeepSeek 失败转豆包）。
- 仍失败：返回 `error` SSE 事件，retryable=true。
- partial_answer 保留已生成 token。

### 7.3 反思 LLM 失败

- 反思是异步后台任务，失败不影响主回答。
- 记录 `reflection_failed=true`，下次会话不装载反思结论。

## 八、错误监控与告警

### 8.1 Prometheus 指标

```
agent_tool_calls_total{tool_name, status}    # 调用次数（成功/失败分维度）
agent_tool_latency_seconds{tool_name}        # 延迟分布
agent_tool_error_rate{tool_name, error_code} # 错误率
agent_degraded_responses_total               # 降级响应总数
agent_multi_tool_failures_total              # 多工具连续失败次数
```

### 8.2 告警规则

| 指标 | 阈值 | 告警级别 |
|------|------|---------|
| 单工具错误率 | >20%（5分钟） | P2 |
| 单工具 P95 延迟 | >5s（5分钟） | P2 |
| 多工具连续失败次数 | >10/分钟 | P1 |
| 降级响应占比 | >30%（1小时） | P2 |
| LLM 调用失败率 | >10%（5分钟） | P1 |

### 8.3 错误归档

每次工具失败记录到 `agent_tool_errors` 表：
```sql
CREATE TABLE agent_tool_errors (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36),
  turn_id INT,
  tool_name VARCHAR(64),
  error_code VARCHAR(32),
  error_message TEXT,
  args_json JSON,
  retry_count INT,
  degraded TINYINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tool_created (tool_name, created_at),
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

保留 30 天，供离线分析"哪些工具最不稳定"。

## 九、决策记录 (ADR)

### ADR-096: 工具失败返回 error tool_result 而非抛异常
- **理由**：抛异常会中断 Agent 循环，让 LLM 看不到错误。返回 error tool_result 让 LLM 自行决策（换工具/降级/追问）。
- **代价**：LLM 可能误判（如反复重试），通过单轮调用上限兜底。

### ADR-097: suggestion 字段引导 LLM
- **理由**：LLM 面对错误时行为不可控，suggestion 给出明确建议（告诉用户失败/换工具/用已有信息），降低 LLM 决策失误率。
- **实现**：错误码 → suggestion 映射表，维护在 tool_registry。

### ADR-098: 单轮 ≥3 工具失败强制中断
- **理由**：连续失败通常意味着上游整体不可用，继续调用无意义。强制中断保护成本与体验。
- **可调**：阈值可通过管理后台调整。

### ADR-099: 降级响应单独计数
- **理由**：降级率上升是系统健康度下降的早期信号（早于完全失败）。单独监控便于提前预警。
- **告警**：降级率 >30% 触发 P2，意味着向量服务等核心组件不稳定。

### ADR-100: 错误归档 30 天
- **理由**：错误数据用于离线分析工具稳定性趋势，30 天足够覆盖一个月的周期性模式。
- **归档**：30 天后导出到对象存储，冷查询走对象存储。

### ADR-101: LLM 失败跨 provider 重试
- **理由**：DeepSeek 偶发故障时，豆包作为兜底可继续提供服务。跨 provider 重试提升可用性。
- **代价**：豆包质量略低于 DeepSeek，回答质量可能下降。但优于完全无回答。
