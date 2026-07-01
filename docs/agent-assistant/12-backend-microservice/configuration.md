# application.yml 与环境变量

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、配置文件分层

```
application.yml          # 通用配置（端口/日志/通用参数）
application-docker.yml   # Docker 环境配置（数据源指向容器名）
application-dev.yml      # 本地开发配置（数据源指向 localhost）
```

启动时通过 `SPRING_PROFILES_ACTIVE=docker` 切换。

## 二、application.yml

```yaml
server:
  port: 8083
  servlet:
    context-path: /

spring:
  application:
    name: agent-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  # WebFlux 配置
  webflux:
    response-buffer-size: 32KB

  # MySQL 数据源（业务表）
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/campushare?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000

  # PostgreSQL 数据源（向量）
  pg-datasource:
    url: jdbc:postgresql://${PG_HOST:localhost}:5432/agent_vectors
    username: ${PG_USER:agent}
    password: ${PG_PASSWORD:agent123456}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  # Redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD:}
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 2

  # OpenFeign
  cloud:
    openfeign:
      client:
        config:
          agent-post:
            connect-timeout: 1000
            read-timeout: 5000
          agent-user:
            connect-timeout: 1000
            read-timeout: 3000
          agent-post-behavior:
            connect-timeout: 1000
            read-timeout: 5000
      circuitbreaker:
        enabled: true

# Resilience4j 熔断
resilience4j:
  circuitbreaker:
    instances:
      agent-post:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
      agent-user:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      agent-post-behavior:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s

# MyBatis Plus
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: assign_id
      logic-delete-field: deletedAt
      logic-not-delete-value: 'null'

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: agent-service

# OpenTelemetry
otel:
  service:
    name: agent-service
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://tempo:4317}
    metrics:
      exporter: none

# Agent 业务配置
agent:
  # Token 预算
  context:
    max-input-tokens: 8000
    budget:
      L0: 1000
      L1: 300
      L2: 500
      L3: 3000
      L4: 2500
      L5: 700

  # 会话
  session:
    ttl-hours: 2
    archive-inactive-minutes: 30
    max-turns-before-compress: 10

  # 工具调用
  tool:
    max-calls-per-turn: 5
    max-same-tool-per-turn: 3
    budget-yuan-per-session: 0.5

  # LLM
  llm:
    primary: deepseek
    fallback: doubao
    deepseek:
      base-url: https://api.deepseek.com/v1
      api-key: ${DEEPSEEK_API_KEY:}
      model: deepseek-chat
      timeout-seconds: 30
      max-retries: 1
    doubao:
      base-url: https://ark.cn-beijing.volces.com/api/v3
      api-key: ${DOUBAO_API_KEY:}
      model: doubao-pro-32k
      timeout-seconds: 30
    embedding:
      provider: bge
      base-url: ${BGE_BASE_URL:http://bge-service:8080}
      model: bge-m3
      dimension: 1024
      batch-size: 32
    rerank:
      provider: bge
      base-url: ${BGE_BASE_URL:http://bge-service:8080}
      model: bge-reranker-v2-m3
      top-k: 5

  # 检索
  retrieval:
    vector-top-k: 20
    bm25-top-k: 20
    rrf-k: 60
    rerank-top-k: 5
    hybrid-weights:
      vector: 0.6
      bm25: 0.3
      structured: 0.1

  # 反思
  reflection:
    enabled: false
    trigger-interval-turns: 5
    model: deepseek-reasoner

  # 长期记忆
  memory:
    explicit-decay: 0
    inferred-decay-per-week: 0.1
    inferred-min-confidence: 0.3
    load-top-k: 5
    evidence-min-count: 3

# 日志
logging:
  level:
    com.campushare.agent: INFO
    org.springframework.web: INFO
    com.baomidou.mybatisplus: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

## 三、application-docker.yml（覆盖）

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/campushare?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root123456
  pg-datasource:
    url: jdbc:postgresql://agent-postgres:5432/agent_vectors
    username: agent
    password: ${AGENT_PG_PASSWORD:-agent123456}
  data:
    redis:
      host: redis
      port: 6379

otel:
  exporter:
    otlp:
      endpoint: http://tempo:4317
```

## 四、.env.example 增量

在根目录 `.env.example` 新增：

```bash
# Agent Service
DEEPSEEK_API_KEY=sk-your-deepseek-key
DOUBAO_API_KEY=your-doubao-key
BGE_BASE_URL=http://bge-service:8080
AGENT_PG_PASSWORD=agent123456
```

## 五、环境变量清单

| 变量 | 用途 | 默认值 | 必填 |
|------|------|-------|------|
| DEEPSEEK_API_KEY | DeepSeek API 密钥 | - | ✅ |
| DOUBAO_API_KEY | 豆包 API 密钥（兜底） | - | ✅ |
| BGE_BASE_URL | BGE embedding/rerank 服务地址 | http://bge-service:8080 | ✅ |
| AGENT_PG_PASSWORD | PostgreSQL 密码 | agent123456 | 否 |
| MYSQL_HOST | MySQL 主机 | localhost | 否 |
| MYSQL_USER | MySQL 用户 | root | 否 |
| MYSQL_PASSWORD | MySQL 密码 | root123456 | 否 |
| PG_HOST | PostgreSQL 主机 | localhost | 否 |
| PG_USER | PostgreSQL 用户 | agent | 否 |
| PG_PASSWORD | PostgreSQL 密码 | agent123456 | 否 |
| REDIS_HOST | Redis 主机 | localhost | 否 |
| REDIS_PASSWORD | Redis 密码 | - | 否 |
| OTEL_EXPORTER_OTLP_ENDPOINT | Tempo OTLP 端点 | http://tempo:4317 | 否 |

## 六、配置热更新

部分配置支持运行时热更新（通过管理后台 + Redis 通知）：

| 配置项 | 热更新 | 方式 |
|-------|--------|------|
| 工具 timeout/enabled | ✅ | DB 更新后 DEL Redis 缓存 |
| prompt 版本 | ✅ | DB 更新 agent_sessions.prompt_version |
| LLM 主备切换 | ✅ | DB 配置表 + Redis 通知 |
| 检索 top-k | ✅ | DB 配置表 |
| Token 预算 | ❌ | 需重启 |
| 反思开关 | ✅ | DB 配置表 |

## 七、密钥管理

- **本地开发**：`.env` 文件（不入 Git）。
- **Docker 部署**：docker-compose.yml 引用 `.env`，或通过 `environment` 直接注入。
- **生产环境**：建议用 Docker Secrets 或 Vault，而非明文环境变量。
- **密钥轮换**：DeepSeek/豆包密钥支持随时更新，agent-service 监听 .env 变更并刷新客户端（无需重启）。

## 八、决策记录 (ADR)

### ADR-150: 双数据源（MySQL + PostgreSQL）
- **理由**：业务表用 MySQL（与现有服务一致），向量用 PostgreSQL+pgvector（MySQL 无原生向量支持）。
- **实现**：AbstractRoutingDataSource 或 @DataSource 注解切换。

### ADR-151: LLM 密钥走环境变量而非配置中心
- **理由**：项目无配置中心（Nacos/Apollo），环境变量最简单。.env 文件 + Docker secrets 已满足安全要求。
- **未来**：引入 Nacos 后可迁移。

### ADR-152: 业务参数集中在 agent.* 节点
- **理由**：所有 Agent 专属配置集中在 `agent.*`，便于管理与文档化。Spring 原生配置（datasource/redis）保持标准位置。
- **绑定**：用 @ConfigurationProperties 注入到 AgentProperties 类。

### ADR-153: 部分配置支持热更新
- **理由**：工具/prompt/LLM 切换需不停机调整，热更新避免重启影响在线用户。
- **实现**：DB 配置表 + Redis pub/sub 通知，agent-service 监听刷新本地缓存。

### ADR-154: BGE 服务独立部署
- **理由**：BGE-M3 模型 2GB+，与 agent-service 同容器会撑爆内存。独立容器便于资源隔离与水平扩展。
- **镜像**：用 `bge-service` 自建镜像或 HuggingFace TEI（Text Embeddings Inference）。
