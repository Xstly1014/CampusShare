# 日志与链路追踪

> 状态: 草稿
> 最后更新: 2026-06-30
> 前置阅读: [README.md](./README.md)、[metrics-system.md](./metrics-system.md)

## 一、日志与追踪的关系

- **日志(Logs)**:离散事件记录,回答「发生了什么」。适合错误排查、安全审计。
- **追踪(Traces)**:请求在分布式系统中的完整路径,回答「慢在哪里、断在哪里」。
- **关系**:通过 traceId 关联,从追踪定位到慢/失败的 span,再查日志看详情。

## 二、结构化日志

### 2.1 日志格式

所有 agent-service 日志采用 JSON 结构化格式,便于 ELK/Loki 检索:

```json
{
  "timestamp": "2026-06-30T14:23:15.123Z",
  "level": "INFO",
  "service": "agent-service",
  "traceId": "a1b2c3d4e5f6",
  "spanId": "7890abcd",
  "logger": "com.campushare.agent.service.AgentOrchestrator",
  "thread": "reactor-http-nio-3",
  "message": "Agent turn completed",
  "context": {
    "sessionId": "sess-xxx",
    "turnId": 42,
    "userId": 8842,
    "intent": "SEARCH",
    "agentVersion": "v1.3",
    "durationMs": 4523,
    "toolCalls": ["search_posts"],
    "tokenUsage": {"input": 1200, "output": 350},
    "costYuan": 0.003
  }
}
```

### 2.2 Logback 配置

```xml
<!-- agent-service logback-spring.xml -->
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>sessionId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <customFields>{"service":"agent-service"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>

    <!-- LLM 调用细节单独 DEBUG 级别 -->
    <logger name="com.campushare.agent.service.LlmClient" level="DEBUG"/>
    <!-- Feign 调用 -->
    <logger name="com.campushare.agent.feign" level="DEBUG"/>
</configuration>
```

### 2.3 日志级别规范

| 级别 | 使用场景 | 示例 |
|------|----------|------|
| ERROR | 系统故障,需人工介入 | LLM API 连续 3 次失败、数据库连接断开 |
| WARN | 异常但可降级处理 | 工具调用超时(已降级)、成本达 90% 预算 |
| INFO | 关键业务节点 | 会话创建、轮次完成、工具调用 |
| DEBUG | 调试细节 | LLM 请求/响应全文、检索中间结果 |
| TRACE | 极细粒度 | SSE 每个 token 事件 |

### 2.4 日志内容规范

**必须记录的字段**(每条日志):
- timestamp(毫秒精度)
- level
- traceId + spanId(OTel 注入)
- service

**业务日志额外字段**:
- sessionId / turnId / userId
- intent / agentVersion
- 关键数值(durationMs / tokenUsage / costYuan)

**禁止记录**:
- 用户 PII 原文(脱敏后记录)
- LLM API Key
- 完整 system prompt(只记录 hash)
- 用户密码

### 2.5 关键日志点

```java
// 会话创建
log.info("Session created", kv("sessionId", sid), kv("userId", uid), kv("agentVersion", ver));

// 意图识别
log.info("Intent classified", kv("sessionId", sid), kv("turnId", tid),
    kv("intent", predicted), kv("confidence", conf), kv("durationMs", ms));

// 检索
log.info("Retrieval completed", kv("sessionId", sid), kv("turnId", tid),
    kv("vectorHits", vHits), kv("keywordHits", kHits), kv("rerankedCount", rCount),
    kv("durationMs", ms));

// 工具调用
log.info("Tool call", kv("sessionId", sid), kv("turnId", tid),
    kv("tool", toolName), kv("status", status), kv("durationMs", ms));

// LLM 调用
log.info("LLM call", kv("sessionId", sid), kv("turnId", tid),
    kv("model", model), kv("phase", phase), kv("status", status),
    kv("inputTokens", inTok), kv("outputTokens", outTok), kv("costYuan", cost),
    kv("durationMs", ms));

// 安全事件
log.warn("Security event", kv("sessionId", sid), kv("userId", uid),
    kv("eventType", "PROMPT_INJECTION"), kv("layer", "RULE"), kv("action", "BLOCKED"));

// 降级
log.warn("Degradation triggered", kv("sessionId", sid), kv("turnId", tid),
    kv("component", "reranker"), kv("reason", "timeout"), kv("fallback", "skip"));

// 轮次完成
log.info("Turn completed", kv("sessionId", sid), kv("turnId", tid),
    kv("durationMs", totalMs), kv("toolCalls", toolList), kv("costYuan", totalCost));
```

## 三、链路追踪

### 3.1 OpenTelemetry 集成

项目已配置 OTel Java Agent(docker-compose.yml 中 `JAVA_OPTS=-javaagent:/app/opentelemetry-javaagent.jar`),自动注入:
- HTTP 请求 span
- Feign 调用 span
- JDBC span
- Redis span

Agent-service 需额外添加业务 span:

```java
@Component
@RequiredArgsConstructor
public class AgentTracer {

    private final Tracer tracer;  // OTel Tracer

    public Span startSpan(String name, String sessionId, String turnId) {
        Span span = tracer.spanBuilder(name)
            .setAttribute("session.id", sessionId)
            .setAttribute("turn.id", turnId)
            .startSpan();
        return span;
    }
}

// 使用
public AgentResponse processTurn(String sessionId, Long turnId, String userMessage) {
    Span span = agentTracer.startSpan("agent.processTurn", sessionId, String.valueOf(turnId));
    try (Scope scope = span.makeCurrent()) {
        // 1. 输入护栏
        Span guardSpan = tracer.spanBuilder("input_guardrail").startSpan();
        try (Scope s = guardSpan.makeCurrent()) {
            guardrailResult = inputGuardrail.check(userMessage);
        } finally { guardSpan.end(); }

        // 2. 意图识别
        Span intentSpan = tracer.spanBuilder("intent_classify").startSpan();
        try (Scope s = intentSpan.makeCurrent()) {
            intent = intentClassifier.classify(userMessage);
            intentSpan.setAttribute("intent.predicted", intent);
        } finally { intentSpan.end(); }

        // 3. 检索
        Span retrievalSpan = tracer.spanBuilder("retrieval").startSpan();
        try (Scope s = retrievalSpan.makeCurrent()) {
            docs = retrievalService.retrieve(rewrittenQuery);
            retrievalSpan.setAttribute("retrieval.count", docs.size());
        } finally { retrievalSpan.end(); }

        // ... 后续阶段

    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR);
        throw e;
    } finally {
        span.end();
    }
}
```

### 3.2 追踪 span 树结构

一次完整 agent 轮次的 span 树:

```
agent.processTurn (4523ms)
├── input_guardrail (52ms)
├── intent_classify (480ms)
├── query_rewrite (310ms)
├── retrieval (210ms)
│   ├── vector_search (75ms)
│   ├── keyword_search (50ms)
│   └── rrf_fusion (8ms)
├── rerank (145ms)
├── tool_call:search_posts (180ms)
│   └── feign.post-service (165ms)
├── llm_generate (2800ms)
│   └── http.deepseek-api (2780ms)
├── output_guardrail (180ms)
│   ├── content_safety (90ms)
│   └── hallucination_check (85ms)
└── citation_process (30ms)
```

- **瓶颈一目了然**:上例中 LLM 生成占 62%,是主要瓶颈。
- **跨服务**:feign.post-service span 可跳转到 post-service 的追踪。

### 3.3 Tempo 配置

```yaml
# tempo.yml (已有,无需修改)
# agent-service 的 trace 自动通过 OTLP 发送到 Tempo
# Grafana 中通过 traceId 查询完整链路
```

### 3.4 慢查询追踪
对超过 SLO 的请求(如 E2E > 8s),自动标记并采样:

```java
@Aspect
@Component
public class SlowTurnAspect {

    @AfterReturning(pointcut = "execution(* AgentOrchestrator.processTurn(..))", returning = "result")
    public void checkSlowTurn(JoinPoint jp, Object result) {
        long duration = ((AgentResponse) result).getDurationMs();
        if (duration > 8000) {
            String traceId = tracer.currentSpan().getSpanContext().getTraceId();
            log.warn("Slow turn detected", kv("traceId", traceId),
                kv("durationMs", duration), kv("slo", 8000));
            // 触发告警
            alertService.sendWarning("Agent slow turn: " + traceId);
        }
    }
}
```

## 四、日志聚合

### 4.1 Loki(轻量级日志聚合)

项目当前用文件日志 + docker logs。进阶阶段引入 Loki:

```yaml
# docker-compose.yml 增量
loki:
  image: grafana/loki:2.9.0
  container_name: campushare-loki
  ports:
    - "3101:3100"
  volumes:
    - loki_data:/loki
  networks:
    - campushare-network

promtail:
  image: grafana/promtail:2.9.0
  volumes:
    - /var/lib/docker/containers:/var/lib/docker/containers:ro
    - ./promtail-config.yml:/etc/promtail/config.yml:ro
  command: -config.file=/etc/promtail/config.yml
  depends_on:
    - loki
```

### 4.2 日志查询(Grafana)

在 Grafana 中通过 traceId 关联日志和追踪:

```logql
# 查询某次请求的全部日志
{service="agent-service"} |= "a1b2c3d4e5f6"

# 查询所有 ERROR 日志
{service="agent-service"} |= "ERROR"

# 查询安全事件
{service="agent-service"} |= "Security event"
```

## 五、MDC 上下文传播

```java
@Component
public class AgentMdcFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = extractTraceId(exchange);
        String sessionId = exchange.getRequest().getHeaders().getFirst("X-Session-Id");

        return chain.filter(exchange)
            .contextWrite(Context.of(
                "traceId", traceId,
                "sessionId", sessionId != null ? sessionId : ""
            ))
            .doOnEach(signal -> {
                MDC.put("traceId", traceId);
                MDC.put("sessionId", sessionId != null ? sessionId : "");
            })
            .doFinally(signal -> MDC.clear());
    }
}
```

- MDC 中的 traceId/sessionId 自动注入日志(Logback 配置)。
- Feign 调用时透传 traceId(OTel agent 自动处理)。

## 六、决策记录

### ADR-184: 日志与追踪 - 结构化 JSON + OTel 业务 span
- **背景**:Agent 链路长(10+ 阶段),需精确定位慢点和故障点。
- **决策**:
  - 日志:JSON 结构化,Logstash encoder,关键字段(traceId/sessionId/turnId/intent/costYuan)。
  - 追踪:OTel Java Agent 自动注入 + 手动业务 span(每阶段一个 span)。
  - 关联:traceId 贯穿日志、追踪、指标。
  - 慢请求:E2E > 8s 自动标记 + 告警。
  - 进阶:Loki 日志聚合,Grafana 统一查询。
- **理由**:JSON 日志便于机器检索;业务 span 是自动追踪的补充,定位业务阶段。
- **权衡**:JSON 日志可读性不如纯文本,但可检索性更重要;DEBUG 级别日志量大,生产默认 INFO。
- **状态**:采纳
