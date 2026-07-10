# agent-service 工程结构

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、Maven 模块

在 `backend/` 下新增 `campushare-agent` 模块，与 user/post/gateway 平级：

```
backend/
├── campushare-common/        # 现有
├── campushare-user/          # 现有
├── campushare-post/          # 现有
├── campushare-gateway/       # 现有
├── campushare-agent/         # 新增
│   ├── pom.xml
│   └── src/main/java/com/campushare/agent/
├── pom.xml                   # 父 POM 新增 agent 模块
├── settings.xml
└── Dockerfile                # 新增 agent-service target
```

### 父 POM 改动

```xml
<modules>
    <module>campushare-common</module>
    <module>campushare-user</module>
    <module>campushare-post</module>
    <module>campushare-gateway</module>
    <module>campushare-agent</module>  <!-- 新增 -->
</modules>
```

## 二、campushare-agent/pom.xml

```xml
<project>
    <parent>
        <groupId>com.campushare</groupId>
        <artifactId>campushare-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    <artifactId>campushare-agent</artifactId>

    <dependencies>
        <!-- common 模块 -->
        <dependency>
            <groupId>com.campushare</groupId>
            <artifactId>campushare-common</artifactId>
        </dependency>

        <!-- Spring Boot WebFlux（SSE 流式） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>

        <!-- Resilience4j（熔断） -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>

        <!-- MyBatis Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- pgvector（向量库） -->
        <dependency>
            <groupId>com.pgvector</groupId>
            <artifactId>pgvector</artifactId>
            <version>0.1.6</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>

        <!-- jtokkit（token 估算） -->
        <dependency>
            <groupId>com.knuddels</groupId>
            <artifactId>jtokkit</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Spring Retry -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>

        <!-- Actuator + Micrometer -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- OpenTelemetry -->
        <dependency>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-spring-boot-starter</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## 三、包结构

```
com.campushare.agent
├── AgentServiceApplication.java       # 启动类
│
├── config/                            # 配置类
│   ├── WebFluxConfig.java             # WebFlux + SSE 配置
│   ├── FeignConfig.java               # Feign + 熔断配置
│   ├── RedisConfig.java               # Redis 序列化
│   ├── MyBatisPlusConfig.java         # MP 配置
│   ├── PgVectorConfig.java            # pgvector 数据源
│   └── OpenTelemetryConfig.java       # OTel 配置
│
├── controller/                        # 控制器
│   ├── AgentSessionController.java    # /api/agent/sessions*
│   ├── AgentMessageController.java    # /api/agent/sessions/{id}/messages*
│   ├── AgentFeedbackController.java   # /api/agent/sessions/.../feedback
│   ├── AgentRefController.java        # /api/agent/refs/*
│   ├── InternalAgentController.java   # /api/internal/agent/*
│   └── AdminAgentController.java      # /api/admin/agent/*
│
├── service/                           # 业务服务
│   ├── session/                       # 会话管理
│   │   ├── SessionService.java
│   │   ├── SessionStateManager.java   # 状态机
│   │   └── SessionArchiveService.java # 归档
│   ├── conversation/                  # 对话处理
│   │   ├── ConversationService.java
│   │   ├── ContextAssembler.java      # 上下文组装器
│   │   ├── CompressionService.java    # 上下文压缩
│   │   └── TokenBudgeter.java         # Token 预算
│   ├── intent/                        # 意图理解
│   │   ├── IntentClassifier.java
│   │   ├── QueryRewriter.java
│   │   └── RouterService.java
│   ├── retrieval/                     # 检索
│   │   ├── HybridRetrievalService.java
│   │   ├── VectorSearchService.java
│   │   ├── BM25SearchService.java
│   │   ├── RerankService.java
│   │   └── RRFFusionService.java
│   ├── agent/                         # Agent 核心
│   │   ├── AgentOrchestrator.java     # ReAct 主循环
│   │   ├── ToolExecutor.java          # 工具执行
│   │   ├── ReflectionService.java     # 反思
│   │   └── ToolRegistry.java          # 工具注册表
│   ├── memory/                        # 记忆
│   │   ├── ShortTermMemoryService.java   # Redis 短期
│   │   ├── LongTermMemoryService.java    # MySQL 长期
│   │   └── MemoryDecayService.java       # 衰减
│   ├── knowledge/                     # 知识库
│   │   ├── KnowledgeService.java
│   │   ├── EmbeddingService.java
│   │   └── KnowledgeIndexService.java
│   ├── llm/                           # LLM 客户端
│   │   ├── LlmClient.java             # 抽象层
│   │   ├── DeepSeekClient.java
│   │   ├── DoubaoClient.java
│   │   ├── BgeEmbeddingClient.java
│   │   ├── BgeRerankClient.java
│   │   └── LlmFallbackChain.java
│   └── streaming/                     # 流式输出
│       └── SseEventEmitter.java
│
├── feign/                             # Feign 客户端
│   ├── PostFeignClient.java
│   ├── PostFeignClientFallback.java
│   ├── UserFeignClient.java
│   ├── UserFeignClientFallback.java
│   ├── PostBehaviorFeignClient.java
│   └── PostBehaviorFeignClientFallback.java
│
├── entity/                            # 实体（MyBatis Plus）
│   ├── AgentSession.java
│   ├── AgentTurn.java
│   ├── AgentContextSnapshot.java
│   ├── AgentToolError.java
│   ├── AgentToolRegistry.java
│   ├── AgentSessionEvent.java
│   ├── UserMemory.java
│   ├── UserMemoryEvidence.java
│   ├── KnowledgeArticle.java
│   ├── PostVector.java
│   └── KnowledgeVector.java
│
├── mapper/                            # Mapper 接口
│   ├── AgentSessionMapper.java
│   ├── AgentTurnMapper.java
│   ├── AgentContextSnapshotMapper.java
│   ├── AgentToolErrorMapper.java
│   ├── AgentToolRegistryMapper.java
│   ├── UserMemoryMapper.java
│   ├── UserMemoryEvidenceMapper.java
│   ├── KnowledgeArticleMapper.java
│   └── VectorMapper.java              # pgvector 自定义 SQL
│
├── dto/                               # DTO
│   ├── request/                       # 请求 DTO
│   └── response/                      # 响应 DTO
│
├── event/                             # 事件
│   ├── BehaviorEvidenceListener.java
│   └── KnowledgeUpdateListener.java
│
├── scheduled/                         # 定时任务
│   ├── SessionArchiveScheduler.java
│   ├── MemoryDecayScheduler.java
│   ├── BehaviorEvidencePuller.java
│   └── ZombieSessionCleaner.java
│
└── util/                              # 工具
    ├── TokenCounter.java              # jtokkit 封装
    ├── JsonUtils.java
    └── TraceUtils.java
```

## 四、启动类

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.campushare.agent.feign")
@EnableScheduling
@EnableRetry
public class AgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
```

## 五、与其他模块的依赖关系

```
campushare-agent
    │
    ├── 依赖 campushare-common（Result/异常/JWT 常量）
    │
    ├── Feign 调用 → campushare-user（用户信息）
    │
    ├── Feign 调用 → campushare-post（帖子/行为证据）
    │
    └── 被 gateway 路由 → /api/agent/** 转发到此
```

**禁止依赖**: 不依赖 campushare-user 或 campushare-post 的实体/Mapper（只通过 Feign 调用其内部 API）。

## 六、Dockerfile 改动

在现有 `backend/Dockerfile` 多阶段构建中新增 agent-service target：

```dockerfile
# 在现有 user-service/post-service/gateway-service target 旁新增
FROM ${BUILDER} AS agent-service
COPY --from=build /app/campushare-agent/target/campushare-agent.jar /app/app.jar
COPY opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

## 七、决策记录 (ADR)

### ADR-137: agent-service 独立 Maven 模块
- **理由**：遵循 AGENT-WORKFLOW.md 微服务规范，独立模块便于独立部署/演进。
- **替代**：放入 campushare-post——职责混淆，违反单一职责。

### ADR-138: 用 WebFlux 而非 Web MVC
- **理由**：SSE 流式输出用 WebFlux 的 Flux<ServerSentEvent> 更自然；Web MVC 的 SseEmitter 在复杂场景下难管理背压。
- **代价**：WebFlux 学习曲线，但项目其它服务保持 Web MVC 不受影响（agent 独立）。

### ADR-139: pgvector 用独立 PostgreSQL 数据源
- **理由**：MySQL 不支持向量类型，需 PostgreSQL。agent-service 配置双数据源（MySQL for 业务表 + PostgreSQL for 向量）。
- **实现**：用 `@DataSource` 注解或 AbstractRoutingDataSource 切换。

### ADR-140: 复用 campushare-common 不复用 user/post 实体
- **理由**：common 是纯工具无业务依赖，复用安全。user/post 实体会引入对其 DB 的耦合，违反铁律。
- **隔离**：跨服务数据全部走 Feign + DTO。

### ADR-141: jtokkit 而非调用 API 计数
- **理由**：见 [context-window-management.md](../09-context-engineering/context-window-management.md) ADR-048。

### ADR-142: 定时任务用 @Scheduled 而非 Quartz
- **理由**：任务简单（4 个定时任务），@Scheduled 足够。Quartz 引入 DB 表与调度器复杂度过高。
- **分布式**: 多实例时用 ShedLock 保证单实例执行（避免重复归档）。
