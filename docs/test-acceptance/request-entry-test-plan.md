# Request Entry 模块测试验收方案

## 1. 文档概述

### 1.1 测试目标

验证 Request Entry 模块的以下功能是否符合设计要求：

| 模块 | 测试目标 | 设计文档参考 |
|------|----------|-------------|
| RateLimitService | Redis + Lua 滑动窗口多级限流的准确性和可靠性 | [request-entry-design.md](../agent-advanced-design/request-entry-design.md) |
| AuthenticationFilter | 可插拔鉴权架构（JWT/API Key/Internal Token） | [request-entry-design.md](../agent-advanced-design/request-entry-design.md) |
| AntiReplayFilter | 基于 X-Request-Id 的防重放保护 | [request-entry-design.md](../agent-advanced-design/request-entry-design.md) |
| RateLimitFilter | 限流过滤器与业务接口的集成 | [request-entry-design.md](../agent-advanced-design/request-entry-design.md) |
| RateLimitConfigController | 限流配置管理接口的 CRUD 操作 | [request-entry-design.md](../agent-advanced-design/request-entry-design.md) |

### 1.2 测试范围

- **新增文件**：
  - `RateLimitService.java`
  - `AuthenticationFilter.java`
  - `AntiReplayFilter.java`
  - `RateLimitFilter.java`
  - `RateLimitConfigController.java`
  - `AuthContext.java`
  - `RateLimitConfig.java`
  - `RateLimitResult.java`
  - `RateLimitStatus.java`
  - `AuthenticationException.java`
  - `RateLimitException.java`
  - `ReplayDetectedException.java`
  - `AuthenticationProvider.java`
  - `JwtAuthenticationProvider.java`
  - `ApiKeyAuthenticationProvider.java`
  - `InternalAuthenticationProvider.java`

- **修改文件**：
  - `AgentController.java`
  - `AgentGlobalExceptionHandler.java`
  - `application.yml`
  - `application-docker.yml`

---

## 2. 测试环境准备

### 2.1 本地开发环境

| 组件 | 版本 | 要求 |
|------|------|------|
| Java | 21 | JDK 21 LTS |
| Maven | 3.9+ | 构建工具 |
| Redis | 7.0+ | 限流和防重放存储 |
| Spring Boot | 3.2+ | WebFlux 框架 |

### 2.2 环境变量配置

```bash
# Redis 连接配置
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
export SPRING_DATA_REDIS_PASSWORD=

# JWT 配置
export AGENT_SECURITY_JWT_SECRET=your-256-bit-secret-key-here
export AGENT_SECURITY_JWT_EXPIRE_HOURS=24

# API Key 配置
export AGENT_SECURITY_API_KEY=test-api-key-12345

# Internal Token 配置
export AGENT_SECURITY_INTERNAL_TOKEN=internal-secret-token
```

### 2.3 启动 Redis

```bash
# 使用 Docker 启动 Redis（推荐）
docker run -d --name redis-test -p 6379:6379 redis:7.0

# 验证 Redis 连接
redis-cli ping
# 预期输出：PONG
```

---

## 3. 单元测试方案

### 3.1 RateLimitService 单元测试

#### 3.1.1 测试类设计

**测试类**：`RateLimitServiceTest.java`

**测试方法**：

| 测试方法 | 测试场景 | 预期结果 |
|----------|----------|----------|
| `checkSingleRateLimit_allowed()` | 单限流键未超过阈值 | 返回 `RateLimitResult.allowed()` |
| `checkSingleRateLimit_exceeded()` | 单限流键超过阈值 | 返回 `RateLimitResult.exceeded()` |
| `checkMultiRateLimit_allAllowed()` | 多个限流键均未超过阈值 | 返回 `RateLimitResult.allowed()` |
| `checkMultiRateLimit_oneExceeded()` | 多个限流键中有一个超过阈值 | 返回对应的 `exceeded` 键名 |
| `checkMultiRateLimit_redisError()` | Redis 不可用时降级 | 返回 `RateLimitResult.allowed()` |
| `resetRateLimit_success()` | 重置限流状态 | 限流计数清零 |
| `slidingWindow_precision()` | 滑动窗口精度验证 | 精度误差 < 0.01% |

#### 3.1.2 测试代码示例

```java
@SpringBootTest
@DisplayName("RateLimitService 单元测试")
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // 清理 Redis 中的限流数据
        redisTemplate.keys("agent:rate:*").flatMap(redisTemplate::delete).blockLast();
    }

    @Test
    @DisplayName("单限流键未超过阈值")
    void checkSingleRateLimit_allowed() {
        Mono<Boolean> result = rateLimitService.checkSingleRateLimit("test-user-1", 10, 60);
        
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("单限流键超过阈值")
    void checkSingleRateLimit_exceeded() {
        // 先快速调用 11 次（阈值为 10）
        Flux.range(1, 11)
                .flatMap(i -> rateLimitService.checkSingleRateLimit("test-user-exceed", 10, 60))
                .blockLast();

        Mono<Boolean> result = rateLimitService.checkSingleRateLimit("test-user-exceed", 10, 60);
        
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("多个限流键中有一个超过阈值")
    void checkMultiRateLimit_oneExceeded() {
        List<String> keys = List.of("global", "user-1", "ip-192.168.1.1");
        List<Integer> maxRequests = List.of(100, 10, 50);

        // 先让 user-1 超过阈值
        Flux.range(1, 11)
                .flatMap(i -> rateLimitService.checkMultiRateLimit(
                        List.of("user-1"), List.of(10), 60))
                .blockLast();

        Mono<RateLimitResult> result = rateLimitService.checkMultiRateLimit(keys, maxRequests, 60);
        
        StepVerifier.create(result)
                .expectNextMatches(r -> !r.isAllowed() && r.getExceededKey().contains("user-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Redis 不可用时降级为允许")
    void checkMultiRateLimit_redisError() {
        // Mock RedisTemplate 返回错误
        Mono<RateLimitResult> result = rateLimitService.checkMultiRateLimit(
                List.of("test-key"), List.of(10), 60);
        
        // 即使 Redis 出错，也应该返回 allowed
        StepVerifier.create(result)
                .expectNextMatches(RateLimitResult::isAllowed)
                .verifyComplete();
    }

    @Test
    @DisplayName("重置限流状态")
    void resetRateLimit_success() {
        // 先触发限流
        Flux.range(1, 11)
                .flatMap(i -> rateLimitService.checkSingleRateLimit("test-reset", 10, 60))
                .blockLast();

        // 验证当前被限流
        Mono<Boolean> beforeReset = rateLimitService.checkSingleRateLimit("test-reset", 10, 60);
        StepVerifier.create(beforeReset).expectNext(false).verifyComplete();

        // 重置
        rateLimitService.resetRateLimit("test-reset").block();

        // 验证限流被重置
        Mono<Boolean> afterReset = rateLimitService.checkSingleRateLimit("test-reset", 10, 60);
        StepVerifier.create(afterReset).expectNext(true).verifyComplete();
    }
}
```

### 3.2 AuthenticationProvider 实现类测试

#### 3.2.1 JwtAuthenticationProvider 测试

**测试方法**：

| 测试方法 | 测试场景 | 预期结果 |
|----------|----------|----------|
| `authenticate_validToken()` | 有效 JWT Token | 返回 `AuthContext` 包含 userId |
| `authenticate_invalidToken()` | 无效 JWT Token | 抛出 `AuthenticationException` |
| `authenticate_expiredToken()` | 过期 JWT Token | 抛出 `AuthenticationException` |
| `supports_withBearerToken()` | 请求包含 Bearer Token | 返回 `true` |
| `supports_withoutBearerToken()` | 请求不包含 Bearer Token | 返回 `false` |

#### 3.2.2 ApiKeyAuthenticationProvider 测试

**测试方法**：

| 测试方法 | 测试场景 | 预期结果 |
|----------|----------|----------|
| `authenticate_validApiKey()` | 有效 API Key | 返回 `AuthContext` |
| `authenticate_invalidApiKey()` | 无效 API Key | 抛出 `AuthenticationException` |
| `supports_withApiKey()` | 请求包含 X-API-Key | 返回 `true` |
| `supports_withoutApiKey()` | 请求不包含 X-API-Key | 返回 `false` |

#### 3.2.3 InternalAuthenticationProvider 测试

**测试方法**：

| 测试方法 | 测试场景 | 预期结果 |
|----------|----------|----------|
| `authenticate_validInternalToken()` | 有效 Internal Token | 返回 `AuthContext` |
| `authenticate_invalidInternalToken()` | 无效 Internal Token | 抛出 `AuthenticationException` |
| `supports_withInternalToken()` | 请求包含 X-Internal-Token | 返回 `true` |
| `supports_withoutInternalToken()` | 请求不包含 X-Internal-Token | 返回 `false` |

#### 3.2.4 测试代码示例

```java
@SpringBootTest
@DisplayName("JwtAuthenticationProvider 单元测试")
class JwtAuthenticationProviderTest {

    @Autowired
    private JwtAuthenticationProvider jwtProvider;

    @Value("${agent.security.jwt.secret}")
    private String jwtSecret;

    private ServerHttpRequest createRequestWithToken(String token) {
        return MockServerHttpRequest.get("/api/chat")
                .header("Authorization", "Bearer " + token)
                .build();
    }

    private String generateValidToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }

    @Test
    @DisplayName("有效 JWT Token 认证成功")
    void authenticate_validToken() {
        String validToken = generateValidToken("user-123");
        ServerHttpRequest request = createRequestWithToken(validToken);

        StepVerifier.create(jwtProvider.authenticate(request))
                .expectNextMatches(ctx -> 
                        "user-123".equals(ctx.getUserId()) && 
                        "JWT".equals(ctx.getAuthType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("无效 JWT Token 认证失败")
    void authenticate_invalidToken() {
        ServerHttpRequest request = createRequestWithToken("invalid.token.here");

        StepVerifier.create(jwtProvider.authenticate(request))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    @DisplayName("支持 Bearer Token 请求")
    void supports_withBearerToken() {
        ServerHttpRequest request = createRequestWithToken("test-token");
        
        assertTrue(jwtProvider.supports(request));
    }

    @Test
    @DisplayName("不支持无 Bearer Token 请求")
    void supports_withoutBearerToken() {
        ServerHttpRequest request = MockServerHttpRequest.get("/api/chat").build();
        
        assertFalse(jwtProvider.supports(request));
    }
}
```

### 3.3 AntiReplayFilter 单元测试

**测试方法**：

| 测试方法 | 测试场景 | 预期结果 |
|----------|----------|----------|
| `filter_duplicateRequest()` | 相同 userId 和 requestId | 返回 409 Conflict |
| `filter_firstRequest()` | 首次请求 | 正常通过 |
| `filter_differentRequestId()` | 不同 requestId | 正常通过 |
| `filter_missingRequestId()` | 缺少 X-Request-Id | 返回 400 Bad Request |
| `filter_actuatorPath()` | 请求 actuator 路径 | 跳过防重放检查 |

#### 3.3.1 测试代码示例

```java
@SpringBootTest
@DisplayName("AntiReplayFilter 单元测试")
class AntiReplayFilterTest {

    @Autowired
    private AntiReplayFilter antiReplayFilter;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private ServerWebExchange createExchange(String path, String requestId, String userId) {
        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header("X-Request-Id", requestId)
                .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        if (userId != null) {
            exchange.getAttributes().put(AuthenticationFilter.AUTH_CONTEXT_KEY, 
                    AuthContext.builder().userId(userId).authType("JWT").build());
        }
        return exchange;
    }

    @BeforeEach
    void setUp() {
        redisTemplate.keys("agent:replay:*").flatMap(redisTemplate::delete).blockLast();
    }

    @Test
    @DisplayName("重复请求返回 409 Conflict")
    void filter_duplicateRequest() {
        String requestId = "unique-request-id-001";
        String userId = "user-123";

        ServerWebExchange exchange1 = createExchange("/api/chat", requestId, userId);
        ServerWebExchange exchange2 = createExchange("/api/chat", requestId, userId);

        // 第一次请求应该通过
        StepVerifier.create(antiReplayFilter.filter(exchange1, exchange -> Mono.empty()))
                .verifyComplete();

        // 第二次请求应该返回 409
        StepVerifier.create(antiReplayFilter.filter(exchange2, exchange -> Mono.empty()))
                .verifyComplete();
        
        assertEquals(HttpStatus.CONFLICT, exchange2.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("缺少 X-Request-Id 返回 400")
    void filter_missingRequestId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/chat").build());
        exchange.getAttributes().put(AuthenticationFilter.AUTH_CONTEXT_KEY, 
                AuthContext.builder().userId("user-123").authType("JWT").build());

        StepVerifier.create(antiReplayFilter.filter(exchange, e -> Mono.empty()))
                .verifyComplete();
        
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }
}
```

---

## 4. 集成测试方案

### 4.1 过滤器链集成测试

**测试类**：`RequestEntryIntegrationTest.java`

**测试方法**：

| 测试方法 | 测试场景 | 预期结果 |
|----------|----------|----------|
| `chatEndpoint_withValidJwt()` | 携带有效 JWT 访问 /api/chat | 正常响应 |
| `chatEndpoint_withValidApiKey()` | 携带有效 API Key 访问 /api/chat | 正常响应 |
| `chatEndpoint_withoutAuth()` | 无认证信息访问 /api/chat | 返回 401 Unauthorized |
| `chatEndpoint_rateLimited()` | 超过限流阈值后访问 | 返回 429 Too Many Requests |
| `chatEndpoint_duplicateRequest()` | 相同 requestId 重复请求 | 返回 409 Conflict |
| `actuatorEndpoint_noAuthRequired()` | 无认证访问 /actuator/health | 返回 200 |
| `rateLimitConfigEndpoint_noAuthRequired()` | 无认证访问 /api/rate-limit/config | 返回 200 |

#### 4.1.1 集成测试代码示例

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Request Entry 集成测试")
class RequestEntryIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${agent.security.jwt.secret}")
    private String jwtSecret;

    @Value("${agent.security.api-key}")
    private String apiKey;

    private String generateJwtToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }

    @Test
    @DisplayName("携带有效 JWT 访问 /api/chat")
    void chatEndpoint_withValidJwt() {
        String token = generateJwtToken("test-user");

        webTestClient.post().uri("/api/chat")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "test-request-001")
                .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("携带有效 API Key 访问 /api/chat")
    void chatEndpoint_withValidApiKey() {
        webTestClient.post().uri("/api/chat")
                .header("X-API-Key", apiKey)
                .header("X-Request-Id", "test-request-002")
                .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("无认证信息访问 /api/chat 返回 401")
    void chatEndpoint_withoutAuth() {
        webTestClient.post().uri("/api/chat")
                .header("X-Request-Id", "test-request-003")
                .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("无认证访问 actuator 端点")
    void actuatorEndpoint_noAuthRequired() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
```

### 4.2 限流配置管理接口测试

**测试方法**：

| 测试方法 | HTTP 方法 | 路径 | 预期结果 |
|----------|-----------|------|----------|
| `getRateLimitConfig()` | GET | `/api/rate-limit/config` | 返回当前限流配置 |
| `getRateLimitStatus()` | GET | `/api/rate-limit/status` | 返回当前限流状态 |
| `resetRateLimit()` | POST | `/api/rate-limit/reset/{key}` | 重置指定键的限流 |
| `resetAllRateLimits()` | POST | `/api/rate-limit/reset-all` | 重置所有限流 |

---

## 5. 接口测试方案

### 5.1 使用 curl 进行接口测试

#### 5.1.1 健康检查接口

```bash
# 测试健康检查（无需认证）
curl -s http://localhost:8083/actuator/health
# 预期输出：{"status":"UP",...}
```

#### 5.1.2 限流配置接口

```bash
# 查询限流配置
curl -s http://localhost:8083/api/rate-limit/config
# 预期输出：{"global":100,"user":10,"ip":50,...}

# 查询限流状态
curl -s http://localhost:8083/api/rate-limit/status
# 预期输出：限流计数器状态

# 重置指定键的限流
curl -X POST -s http://localhost:8083/api/rate-limit/reset/user-123
# 预期输出：{"success":true}

# 重置所有限流
curl -X POST -s http://localhost:8083/api/rate-limit/reset-all
# 预期输出：{"success":true}
```

#### 5.1.3 Chat 接口（JWT 认证）

```bash
# 获取有效 JWT Token（假设已登录）
TOKEN="your-valid-jwt-token"

# 正常请求
curl -X POST http://localhost:8083/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-Id: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-session-001","query":"你好"}'

# 重复请求（相同 requestId）
curl -X POST http://localhost:8083/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-Id: same-request-id" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-session-001","query":"你好"}'
# 预期输出：409 Conflict
```

#### 5.1.4 Chat 接口（API Key 认证）

```bash
API_KEY="test-api-key-12345"

curl -X POST http://localhost:8083/api/chat \
  -H "X-API-Key: $API_KEY" \
  -H "X-Request-Id: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-session-002","query":"你好"}'
```

#### 5.1.5 限流测试

```bash
TOKEN="your-valid-jwt-token"

# 快速发送超过阈值的请求
for i in {1..11}; do
  curl -X POST http://localhost:8083/api/chat \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Request-Id: rate-limit-test-$i" \
    -H "Content-Type: application/json" \
    -d '{"sessionId":"test-session","query":"test"}'
  sleep 0.1
done

# 第 11 次请求应该返回 429
```

---

## 6. 性能测试方案

### 6.1 使用 JMeter 进行压力测试

#### 6.1.1 测试场景

| 场景 | 并发用户数 | 持续时间 | 目标 |
|------|-----------|----------|------|
| 正常负载 | 50 | 5 分钟 | P95 < 500ms |
| 高负载 | 200 | 10 分钟 | P95 < 1s |
| 限流触发 | 500 | 5 分钟 | 正确触发 429 |

#### 6.1.2 JMeter 配置

1. **Thread Group**：
   - Number of Threads: 200
   - Ramp-Up Period: 30 seconds
   - Loop Count: Infinite

2. **HTTP Request**：
   - Protocol: HTTP
   - Server Name: localhost
   - Port Number: 8083
   - Method: POST
   - Path: /api/chat
   - Headers:
     - Authorization: Bearer ${TOKEN}
     - X-Request-Id: ${__UUID()}
     - Content-Type: application/json
   - Body: `{"sessionId":"perf-test","query":"test query"}`

3. **Response Assertion**：
   - 检查响应码为 200 或 429

### 6.2 性能指标

| 指标 | 目标值 |
|------|--------|
| QPS | > 500 |
| P95 延迟 | < 500ms |
| P99 延迟 | < 1s |
| 错误率 | < 1% |
| Redis CPU | < 30% |

---

## 7. 安全测试方案

### 7.1 认证绕过测试

| 测试场景 | 测试方法 | 预期结果 |
|----------|----------|----------|
| 伪造 JWT Token | 修改 JWT payload 中的 userId | 返回 401 |
| 无效签名 JWT | 使用错误密钥签名 | 返回 401 |
| 过期 JWT | 使用已过期的 Token | 返回 401 |
| 伪造 API Key | 使用随机字符串作为 API Key | 返回 401 |
| 伪造 Internal Token | 使用随机字符串作为 Internal Token | 返回 401 |

### 7.2 重放攻击测试

| 测试场景 | 测试方法 | 预期结果 |
|----------|----------|----------|
| 相同 requestId 重复请求 | 发送两次相同 requestId 的请求 | 第二次返回 409 |
| 不同 userId 相同 requestId | 不同用户使用相同 requestId | 正常通过（requestId 按 userId 隔离） |
| requestId 为空 | 请求不携带 X-Request-Id | 返回 400 |

### 7.3 限流绕过测试

| 测试场景 | 测试方法 | 预期结果 |
|----------|----------|----------|
| 切换 IP 绕过 IP 限流 | 使用不同 IP 发送请求 | 仍受全局限流限制 |
| 切换用户绕过用户限流 | 使用不同 userId 发送请求 | 仍受全局限流限制 |

---

## 8. 部署测试方案

### 8.1 Docker Compose 部署测试

```bash
# 进入部署目录
cd /root/CampusShare

# 拉取最新代码
git pull origin develop

# 构建并启动服务
docker-compose up -d --build agent-service

# 等待服务启动（约 90 秒）
sleep 90

# 检查服务状态
docker-compose ps
# 预期：agent-service 状态为 Up

# 检查健康检查
curl -s http://localhost:8083/actuator/health
# 预期：{"status":"UP"}

# 查看日志
docker-compose logs agent-service | tail -50
```

### 8.2 配置验证

```bash
# 验证限流配置
curl -s http://localhost:8083/api/rate-limit/config

# 验证完整请求流程
TOKEN="your-token"
curl -X POST http://localhost:8083/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-Id: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"deploy-test","query":"hello"}'
```

---

## 9. 测试验收标准

### 9.1 单元测试验收标准

| 标准 | 要求 |
|------|------|
| 测试覆盖率 | RateLimitService: > 80%, AuthenticationFilter: > 80%, AntiReplayFilter: > 80% |
| 测试通过 | 全部单元测试通过 |
| 无内存泄漏 | 测试过程中无内存泄漏 |

### 9.2 集成测试验收标准

| 标准 | 要求 |
|------|------|
| 过滤器链顺序 | CORS → TraceId → RateLimit → Auth → AntiReplay → Validation |
| 认证优先级 | Internal Token (1) > JWT (2) > API Key (3) |
| 白名单路径 | `/actuator/**`, `/api/rate-limit/**` 无需认证 |

### 9.3 接口测试验收标准

| 标准 | 要求 |
|------|------|
| 响应码 | 正常请求 200，未认证 401，限流 429，重放 409 |
| 响应时间 | < 500ms |
| 数据完整性 | 返回数据结构正确 |

### 9.4 性能测试验收标准

| 标准 | 要求 |
|------|------|
| QPS | > 500 |
| P95 延迟 | < 500ms |
| 错误率 | < 1% |

### 9.5 安全测试验收标准

| 标准 | 要求 |
|------|------|
| 认证绕过 | 无法绕过认证 |
| 重放攻击 | 相同 requestId 被正确拦截 |
| 限流绕过 | 无法绕过多级限流 |

### 9.6 部署测试验收标准

| 标准 | 要求 |
|------|------|
| 服务启动 | Docker 容器正常启动 |
| 健康检查 | /actuator/health 返回 UP |
| 日志无错误 | 启动日志无 ERROR 级别 |

---

## 10. 测试报告模板

### 10.1 测试执行摘要

| 测试类型 | 测试用例数 | 通过 | 失败 | 跳过 |
|----------|-----------|------|------|------|
| 单元测试 | N | N | N | N |
| 集成测试 | N | N | N | N |
| 接口测试 | N | N | N | N |
| 性能测试 | N | N | N | N |
| 安全测试 | N | N | N | N |
| 部署测试 | N | N | N | N |

### 10.2 测试环境信息

| 项目 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.2.x |
| Redis | 7.0.x |
| Docker | 24.x |

### 10.3 问题清单

| 问题编号 | 严重程度 | 问题描述 | 状态 |
|----------|----------|----------|------|
| TBD | P0 | xxx | Open |
| TBD | P1 | xxx | Open |

### 10.4 结论

- [ ] 所有测试通过，可交付
- [ ] 存在阻塞问题，需修复后重新测试
- [ ] 存在非阻塞问题，可交付但需后续修复

---

## 11. 测试执行命令

### 11.1 运行所有测试

```bash
cd backend/campushare-agent
mvn test
```

### 11.2 运行指定测试类

```bash
# 运行 RateLimitService 测试
mvn test -Dtest=RateLimitServiceTest

# 运行集成测试
mvn test -Dtest=RequestEntryIntegrationTest
```

### 11.3 生成测试报告

```bash
mvn surefire-report:report
# 报告路径：target/site/surefire-report.html
```

### 11.4 代码覆盖率报告

```bash
mvn jacoco:report
# 报告路径：target/site/jacoco/index.html
```

---

## 附录：测试数据准备

### A.1 测试用户数据

| 用户 ID | 认证方式 | 测试用途 |
|---------|----------|----------|
| test-user-001 | JWT | 正常流程测试 |
| test-user-002 | API Key | API Key 认证测试 |
| internal-service | Internal Token | 内部服务调用测试 |

### A.2 测试配置参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 全局限流 | 100 req/min | 所有用户共享 |
| 用户限流 | 10 req/min | 每个用户 |
| IP 限流 | 50 req/min | 每个 IP |
| 防重放 TTL | 300 秒 | 重复请求检测时间窗口 |
