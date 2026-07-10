# 跨服务 Feign 客户端

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

agent-service 通过 OpenFeign 调用 user-service 和 post-service 的内部 API，严格遵守 AGENT-WORKFLOW.md 第六章铁律：**禁止跨服务直接访问数据库**。

## 二、Feign 客户端清单

| 客户端接口 | 目标服务 | 用途 |
|-----------|---------|------|
| PostFeignClient | post-service:8082 | 帖子检索/详情/热门/分类 |
| UserFeignClient | user-service:8081 | 用户信息/隐私设置 |
| PostBehaviorFeignClient | post-service:8082 | 行为证据拉取（隐式记忆用） |

## 三、PostFeignClient

```java
@FeignClient(name = "post-service", contextId = "agent-post")
public interface PostFeignClient {

    /**
     * 帖子混合检索（向量+BM25+结构化）
     * 路径: POST /api/internal/post/search
     */
    @PostMapping("/api/internal/post/search")
    Result<PostSearchResult> searchPosts(@RequestBody PostSearchRequest req);

    /**
     * 获取帖子详情
     * 路径: GET /api/internal/post/{postId}
     */
    @GetMapping("/api/internal/post/{postId}")
    Result<PostDetailDTO> getPostDetail(
            @PathVariable String postId,
            @RequestParam(required = false, defaultValue = "false") boolean includeComments);

    /**
     * 获取热门帖
     * 路径: GET /api/internal/post/hot
     */
    @GetMapping("/api/internal/post/hot")
    Result<List<PostSummaryDTO>> getHotPosts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String school,
            @RequestParam(defaultValue = "week") String timeWindow,
            @RequestParam(defaultValue = "10") int limit);

    /**
     * 列出所有分类与子分类
     * 路径: GET /api/internal/post/categories
     */
    @GetMapping("/api/internal/post/categories")
    Result<List<CategoryTreeDTO>> listCategories();

    /**
     * 列出所有学校
     * 路径: GET /api/internal/post/schools
     */
    @GetMapping("/api/internal/post/schools")
    Result<List<SchoolDTO>> listSchools();

    /**
     * 根据关键词推断分类路径
     * 路径: POST /api/internal/post/category-path
     */
    @PostMapping("/api/internal/post/category-path")
    Result<List<CategoryPathDTO>> inferCategoryPath(@RequestBody List<String> keywords);
}
```

## 四、UserFeignClient

```java
@FeignClient(name = "user-service", contextId = "agent-user")
public interface UserFeignClient {

    /**
     * 获取用户公开信息（受隐私设置控制）
     * 路径: GET /api/internal/user/{userId}/profile
     */
    @GetMapping("/api/internal/user/{userId}/profile")
    Result<UserPublicDTO> getUserProfile(@PathVariable String userId);

    /**
     * 批量获取用户公开信息（用于帖子列表渲染作者）
     * 路径: POST /api/internal/user/profiles-batch
     */
    @PostMapping("/api/internal/user/profiles-batch")
    Result<List<UserPublicDTO>> getUserProfilesBatch(@RequestBody List<String> userIds);

    /**
     * 获取用户统计（发帖/获赞/粉丝）
     * 路径: GET /api/internal/user/{userId}/stats
     */
    @GetMapping("/api/internal/user/{userId}/stats")
    Result<UserStatsDTO> getUserStats(@PathVariable String userId);
}
```

## 五、PostBehaviorFeignClient（隐式记忆用）

```java
@FeignClient(name = "post-service", contextId = "agent-post-behavior")
public interface PostBehaviorFeignClient {

    /**
     * 拉取用户最近的行为证据（点击/停留/点赞）
     * 路径: GET /api/internal/post/behavior
     * agent-service 每小时调用一次，增量拉取
     */
    @GetMapping("/api/internal/post/behavior")
    Result<List<BehaviorEvidenceDTO>> getUserBehavior(
            @RequestParam String userId,
            @RequestParam long sinceTimestamp,
            @RequestParam(defaultValue = "100") int limit);
}
```

## 六、请求/响应 DTO 定义

### 6.1 PostSearchRequest

```java
@Data
public class PostSearchRequest {
    private String query;
    private String category;
    private String school;
    private String postType;   // resource | discussion
    private Integer limit = 10;
    private Boolean useVector = true;  // 是否走向量检索（降级时 false）
    private Boolean useBM25 = true;
}
```

### 6.2 PostSearchResult

```java
@Data
public class PostSearchResult {
    private Integer total;
    private List<PostSummaryDTO> posts;
    private Boolean degraded;  // 是否降级（向量失败仅 BM25）
    private Long latencyMs;
}
```

### 6.3 PostSummaryDTO

```java
@Data
public class PostSummaryDTO {
    private String postId;
    private String title;
    private String postType;
    private String category;
    private String school;
    private String authorName;
    private Boolean authorVerified;
    private String excerpt;   // 前 200 字
    private Integer likeCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private Double score;     // 检索相关度
}
```

## 七、Feign 配置

### 7.1 超时与重试

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          agent-post:
            connect-timeout: 1000
            read-timeout: 5000
            retry: 1
          agent-user:
            connect-timeout: 1000
            read-timeout: 3000
            retry: 1
          agent-post-behavior:
            connect-timeout: 1000
            read-timeout: 5000
            retry: 0  # 行为证据异步，不重试
```

### 7.2 熔断器（Resilience4j）

```yaml
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
```

熔断开启时：返回降级结果（空列表 + degraded=true），不让上游故障传导。

### 7.3 请求头透传

```java
@Bean
public RequestInterceptor agentRequestInterceptor() {
    return template -> {
        // 透传 traceId 用于链路追踪
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            template.header("X-Trace-Id", traceId);
        }
        // 标识内部调用
        template.header("X-Internal-Call", "agent-service");
    };
}
```

## 八、降级 Fallback

### 8.1 PostFeignClient Fallback

```java
@Component
public class PostFeignClientFallback implements PostFeignClient {
    @Override
    public Result<PostSearchResult> searchPosts(PostSearchRequest req) {
        return Result.success(PostSearchResult.builder()
            .total(0).posts(Collections.emptyList())
            .degraded(true).build());
    }
    // 其它方法返回空结果
}
```

### 8.2 UserFeignClient Fallback

返回匿名用户（nickname="未知用户"），不阻塞帖子检索主流程。

## 九、循环依赖规避

AGENT-WORKFLOW.md 第六章禁止循环依赖。agent-service 的依赖方向：

```
agent-service → user-service（取用户信息）
agent-service → post-service（取帖子/行为证据）
```

**反向调用**：user-service / post-service 不主动调用 agent-service，只通过内部 API 上报（如行为证据上报 `POST /api/internal/agent/behavior-evidence`），这是"被调用"而非"调用 agent"，不构成循环。

## 十、决策记录 (ADR)

### ADR-085: 三个 Feign 客户端分离 contextId
- **理由**：同一服务多个 Feign 客户端必须用 contextId 区分，否则 Bean 冲突。
- **实现**：agent-post / agent-user / agent-post-behavior 三个独立 contextId。

### ADR-086: 行为证据拉取而非推送
- **理由**：agent-service 主动拉取可控制频率与批量大小，避免 post-service 推送风暴。
- **频率**：每小时一次，增量拉取 sinceTimestamp 之后的数据。

### ADR-087: 熔断 + Fallback 双保险
- **理由**：上游服务故障时，agent-service 必须仍能返回降级结果（哪怕空），不能整个 Agent 不可用。
- **实现**：Resilience4j 熔断器 + Fallback 类返回空结果 + degraded 标记。

### ADR-088: traceId 透传
- **理由**：跨服务调用必须能在 Tempo 中串成完整链路，traceId 透传是前提。
- **实现**：RequestInterceptor 从 MDC 取 traceId 写入 X-Trace-Id 头。

### ADR-089: 批量接口 profiles-batch
- **理由**：帖子列表渲染作者信息时，若逐个调用 user-service 会产生 N+1 问题。批量接口一次取回。
- **限制**：单次最多 50 个 userId。
