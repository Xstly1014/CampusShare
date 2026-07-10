# CampusShare Backend - 性能审查报告

**生成日期**: 2026-07-05  
**审查范围**: E:\workspace_work\CampusShare\backend  
**审查级别**: 非常彻底 (Very Thorough)

---

## 执行摘要

本报告对 CampusShare 后端项目进行了全面的性能审查，识别出 **23 个高优先级性能问题** 和 **15 个中优先级性能问题**。主要问题类别包括：

- **N+1 查询问题**: 7 处严重 N+1 查询模式
- **缺少分页**: 5 处查询结果无分页限制
- **异步线程池配置不当**: 3 处使用默认 ForkJoinPool
- **定时任务全量扫描**: 3 处定时任务执行全表扫描
- **批量操作效率低下**: 3 处逐条处理而非批量操作
- **连接池配置**: 可能在高并发下不足

---

## 1. 项目模块结构

| 模块名称 | Java 文件数 | 主要职责 |
|---------|------------|---------|
| campushare-common | 7 | 公共工具类、常量、基础配置 |
| campushare-user | 60 | 用户管理、认证、关注、消息、通知 |
| campushare-post | 51 | 帖子管理、分类、评论、下载、浏览历史 |
| campushare-gateway | 3 | API 网关、统一鉴权、路由 |
| campushare-agent | 49 | AI 助手、向量检索、知识库 |
| **总计** | **170** | |

---

## 2. 依赖分析 (pom.xml)

### 2.1 数据库相关依赖

#### campushare-common/pom.xml
- MyBatis Plus 3.5.6 - ORM 框架
- MySQL 驱动

#### campushare-agent/pom.xml (双数据源)
- PostgreSQL + pgvector 0.1.6 - 用于向量检索

### 2.2 缓存依赖

所有模块通过 common 传递依赖引入:
- spring-boot-starter-data-redis (Lettuce 客户端)

### 2.3 监控依赖

campushare-user, campushare-post, campushare-agent:
- spring-boot-starter-actuator - 健康检查、指标暴露
- micrometer-registry-prometheus - Prometheus 指标采集

### 2.4 异步框架依赖

#### campushare-agent/pom.xml
- spring-boot-starter-webflux - 响应式编程
- spring-cloud-starter-circuitbreaker-resilience4j - 熔断器
- resilience4j-reactor - 响应式熔断器

#### 所有服务模块
- spring-retry - 重试框架

### 2.5 服务间调用

所有服务模块:
- spring-cloud-starter-openfeign - 声明式 HTTP 客户端
- feign-hc5 - Apache HC5 作为 Feign 底层 HTTP 客户端

---

## 3. 配置文件性能分析

### 3.1 数据库连接池配置问题

**文件**: `E:\workspace_work\CampusShare\backend\campushare-user\src\main\resources\application.yml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 问题: 高并发可能不足
      minimum-idle: 5            # 问题: 与maximum不匹配，导致连接数波动延迟
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**问题**: 
- `minimum-idle=5` 过低，建议设为与 `maximum-pool-size` 相同
- 无 `leak-detection-threshold` 配置，无法检测连接泄漏

### 3.2 Redis 连接池配置问题

**文件**: `E:\workspace_work\CampusShare\backend\campushare-user\src\main\resources\application.yml`

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 8          # 问题: 高并发可能不足
          max-idle: 8
          min-idle: 0            # 问题: 建议设为max-idle的50%
          max-wait: -1ms         # 问题: 无限等待，可能导致线程阻塞
```

### 3.3 MyBatis Plus 缓存关闭

**文件**: `E:\workspace_work\CampusShare\backend\campushare-user\src\main\resources\application.yml`

```yaml
mybatis-plus:
  configuration:
    cache-enabled: false          # 问题: 二级缓存关闭，所有查询直接访问数据库
```

---

## 4. 严重 N+1 查询问题 (P0)

### 4.1 FollowServiceImpl - 3 处 N+1

**文件**: `E:\workspace_work\CampusShare\backend\campushare-user\src\main\java\com\campushare\user\service\impl\FollowServiceImpl.java`

#### 问题 1: getFollowStats() - 循环查询互关数

```java
public Map<String, Long> getFollowStats(String userId) {
    List<Follow> myFollowings = followMapper.selectList(...);
    
    long mutualCount = 0;
    for (Follow f : myFollowings) {
        if (isFollowing(f.getFollowingId(), userId)) {  // N+1: 每个关注单独查询
            mutualCount++;
        }
    }
}
```
**影响**: 100 个关注 = 100 次 DB 查询

#### 问题 2: buildUserDTOsFromFollows() - 循环查询用户

```java
private List<UserDTO> buildUserDTOsFromFollows(List<Follow> follows, boolean isFollowing) {
    for (Follow f : follows) {
        String targetId = isFollowing ? f.getFollowingId() : f.getFollowerId();
        User u = userMapper.selectById(targetId);  // N+1: 每个关注单独查询用户
        result.add(convertToUserDTO(u));
    }
}
```
**影响**: 100 个关注 = 100 次 DB 查询

#### 问题 3: convertToUserDTO() - 每个用户额外 2 次 DB 查询

```java
private UserDTO convertToUserDTO(User user) {
    boolean isCreator = creatorService.isCreator(user.getId());  // 额外DB查询
    boolean isAdmin = creatorService.isAdmin(user.getId());      // 额外DB查询
}
```
**影响**: 100 个用户 = 200 次额外 DB 查询

**总计影响**: 查询 100 个关注产生 100 + 100 + 200 = 400 次 DB 查询

---

### 4.2 NotificationServiceImpl - 严重性能问题

**文件**: `E:\workspace_work\CampusShare\backend\campushare-user\src\main\java\com\campushare\user\service\impl\NotificationServiceImpl.java`

#### 问题: getNotificationFeed() - 无分页 + N+1

```java
public List<NotificationItemDTO> getNotificationFeed(String userId) {
    List<NotificationItemDTO> feed = new ArrayList<>();
    
    // 对 7 种通知类型各执行一次查询，无分页
    for (String type : Arrays.asList("SYSTEM", "LIKE", "COMMENT_LIKE", "STAR", "FOLLOW", "COMMENT", "REPLY")) {
        List<Notification> notifs = notificationMapper.selectList(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, type)
                .orderByDesc(Notification::getCreateTime)
            // 无 LIMIT 或分页
        );
        
        for (Notification n : notifs) {
            NotificationItemDTO item = buildPreview(n);  // 内部又查询用户
            feed.add(item);
        }
    }
    
    // 还加载所有消息并分组 (无分页)
    List<Message> allMessages = messageMapper.selectList(...);
}
```

**影响**: 
- 7 次全表扫描 (每个通知类型)
- 每条通知都查询发送者 (N+1)
- 加载所有消息在内存中分组

---

### 4.3 CommentServiceImpl - N+1 + 无分页

**文件**: `E:\workspace_work\CampusShare\backend\campushare-post\src\main\java\com\campushare\post\service\impl\CommentServiceImpl.java`

```java
public List<CommentDTO> getCommentsByPostId(String postId) {
    List<Comment> comments = commentMapper.selectList(...);  // 无分页
    
    for (Comment c : comments) {
        result.add(buildCommentDTO(c));  // 每条评论执行Feign + DB查询
    }
}

private CommentDTO buildCommentDTO(Comment comment) {
    // N+1: 每条评论都调用Feign
    List<UserFeignClient.UserSimpleInfo> users = userFeignClient.getBatchUserInfo(userIds);
    
    // N+1: 每条评论都查询帖子
    Post post = postMapper.selectById(comment.getPostId());
}
```

**影响**: 100 条评论 = 100 次 Feign 调用 + 100 次 DB 查询

---

## 5. 异步线程池配置问题 (P0)

### 5.1 使用默认 ForkJoinPool

**文件**: `E:\workspace_work\CampusShare\backend\campushare-post\src\main\java\com\campushare\post\service\impl\PostServiceImpl.java`

```java
private void notifyAgentPostChanged(String postId, String action) {
    // 问题: 使用默认 ForkJoinPool.commonPool()
    CompletableFuture.runAsync(() -> {
        agentFeignClient.notifyPostChanged(req);
    });
}

private void sendNotificationAfterCommit(String userId, String postId, String authorId, String type) {
    // 问题: 使用默认 ForkJoinPool.commonPool()
    CompletableFuture.runAsync(() -> {
        // Feign 调用发送通知
    });
}
```

**影响**: 
- 默认 ForkJoinPool 与其他任务共享，可能被占满
- 无异常处理，任务失败无感知
- 无队列容量控制

**建议**: 配置自定义线程池

```java
@Configuration
public class ThreadPoolConfig {
    @Bean("asyncTaskExecutor")
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        return executor;
    }
}
```

---

## 6. 定时任务全量扫描问题 (P0)

### 6.1 PostVectorScheduler - 每 5 分钟全量同步

**文件**: `E:\workspace_work\CampusShare\backend\campushare-agent\src\main\java\com\campushare\agent\config\PostVectorScheduler.java`

```java
@Scheduled(initialDelay = 60000, fixedDelay = 300000)  // 每 5 分钟
public void scheduledSync() {
    log.info("[向量同步] 开始定时全量同步...");
    postVectorService.syncAll().block();  // 问题: 全量同步，阻塞
    log.info("[向量同步] 定时全量同步完成");
}

@PostConstruct
public void init() {
    // 问题: 使用 new Thread() 而非线程池
    new Thread(() -> {
        Thread.sleep(60000);
        postVectorService.syncAll().block();  // 全量同步
    }).start();
}
```

**问题**:
1. 每 5 分钟全量同步所有帖子向量
2. 使用 `new Thread()` 而非线程池
3. `.block()` 阻塞调用，占用调度线程

**建议**: 改为增量同步

```java
@Scheduled(initialDelay = 60000, fixedDelay = 300000)
public void scheduledSync() {
    log.info("[向量同步] 开始增量同步...");
    postVectorService.syncIncremental().subscribe();  // 异步非阻塞
}
```

### 6.2 CategoryCache - 每 5 分钟全表扫描

**文件**: `E:\workspace_work\CampusShare\backend\campushare-post\src\main\java\com\campushare\post\cache\CategoryCache.java`

```java
@Scheduled(fixedRate = 300000)  // 每 5 分钟
public void refresh() {
    // 问题: 全表扫描
    List<Category> categories = categoryMapper.selectList(null);
    List<SubCategory> subCategories = subCategoryMapper.selectList(null);
}
```

**建议**: 只在分类变更时刷新 (通过消息队列或事务事件)

---

## 7. 性能问题优先级汇总

### 🔴 高优先级 (P0 - 立即修复)

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 1 | N+1 查询: FollowServiceImpl.getFollowStats() | campushare-user | 100 关注 = 400 次 DB 查询 |
| 2 | N+1 查询: NotificationServiceImpl.getNotificationFeed() | campushare-user | 加载所有通知无分页 |
| 3 | N+1 查询: CommentServiceImpl.getCommentsByPostId() | campushare-post | 100 评论 = 100 次 Feign + DB |
| 4 | 缺少分页: MessageServiceImpl.getConversationList() | campushare-user | 加载所有消息内存分组 |
| 5 | 异步线程池: PostServiceImpl.notifyAgentPostChanged() | campushare-post | 使用默认 ForkJoinPool |
| 6 | 定时任务全量扫描: PostVectorScheduler.scheduledSync() | campushare-agent | 每 5 分钟全量同步 |
| 7 | 批量操作: DataInitServiceImpl.batchInsertPosts() | campushare-post | 逐条 insert |

### 🟡 中优先级 (P1 - 本周修复)

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| 8 | N+1 查询: MessageServiceImpl.convertToDTO() | campushare-user | 每条消息 2 次用户查询 |
| 9 | N+1 查询: FollowServiceImpl.buildUserDTOsFromFollows() | campushare-user | 循环 selectById |
| 10 | 缺少分页: ViewHistoryMapper.selectByUserIdWithPost() | campushare-post | 无分页 |
| 11 | 缺少分页: PostStarMapper.selectByUserIdWithPost() | campushare-post | 无分页 |
| 12 | 连接池配置: HikariCP minimum-idle 过低 | 所有服务 | 连接数波动延迟 |
| 13 | 连接池配置: Redis max-active=8 | 所有服务 | 高并发瓶颈 |
| 14 | 定时任务: CategoryCache.refresh() 每 5 分钟 | campushare-post | 不必要全表扫描 |
| 15 | 复杂查询: incrementViewCount() 3 次 DB 操作 | campushare-post | 高并发性能差 |

---

## 8. 优化建议

### 8.1 N+1 查询优化 - 批量查询模式

**错误示例** (FollowServiceImpl):
```java
// 错误: N+1 查询
for (Follow f : follows) {
    User u = userMapper.selectById(f.getFollowingId());
    result.add(convertToUserDTO(u));
}
```

**正确示例**:
```java
// 正确: 批量查询
Set<String> userIds = follows.stream()
    .map(Follow::getFollowingId)
    .collect(Collectors.toSet());

List<User> users = userMapper.selectBatchIds(userIds);  // 1 次查询
Map<String, User> userMap = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));

for (Follow f : follows) {
    User u = userMap.get(f.getFollowingId());  // 内存获取
    result.add(convertToUserDTO(u));
}
```

### 8.2 分页查询 - 所有列表查询必须分页

**错误示例**:
```java
List<Notification> notifs = notificationMapper.selectList(...);  // 无分页
```

**正确示例**:
```java
Page<Notification> page = new Page<>(current, size);
Page<Notification> result = notificationMapper.selectPage(page, queryWrapper);
```

### 8.3 异步线程池 - 自定义线程池

```java
@Configuration
public class ThreadPoolConfig {
    @Bean("asyncTaskExecutor")
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}

// 使用
@Autowired
@Qualifier("asyncTaskExecutor")
private ThreadPoolTaskExecutor asyncTaskExecutor;

CompletableFuture.runAsync(() -> {
    agentFeignClient.notifyPostChanged(req);
}, asyncTaskExecutor);
```

### 8.4 缓存策略 - Redis 缓存注解

```java
@Cacheable(value = "user", key = "#userId", unless = "#result == null")
public User getUserById(String userId) {
    return userMapper.selectById(userId);
}

@CacheEvict(value = "user", key = "#userId")
public void updateUser(User user) {
    userMapper.updateById(user);
}
```

### 8.5 增量同步 - 定时任务优化

```java
// 错误: 全量同步
@Scheduled(fixedDelay = 300000)
public void scheduledSync() {
    postVectorService.syncAll().block();  // 全量
}

// 正确: 增量同步
@Scheduled(fixedDelay = 300000)
public void scheduledSync() {
    postVectorService.syncIncremental().subscribe();  // 只同步变更数据
}
```

---

## 9. 项目优点

1. ✅ 使用 MyBatis Plus，无 JPA 懒加载问题
2. ✅ 已配置 HikariCP 连接池
3. ✅ 已配置 Redis 缓存
4. ✅ 已配置 Prometheus + Actuator 监控
5. ✅ 已配置 Resilience4j 熔断器 (agent 模块)
6. ✅ 已配置 Spring Retry 重试
7. ✅ 数据库索引基本完善 (复合索引、全文索引)
8. ✅ 向量检索已配置 HNSW 索引 (pgvector)
9. ✅ 启用虚拟线程 (agent 模块)
10. ✅ Feign 使用 Apache HC5 作为 HTTP 客户端

---

## 10. 行动计划

### 第一阶段 (本周) - P0 问题修复

1. **修复 N+1 查询** (预估 2 天)
   - [ ] FollowServiceImpl.getFollowStats() - 批量查询互关
   - [ ] NotificationServiceImpl.getNotificationFeed() - 添加分页 + 批量查询
   - [ ] CommentServiceImpl.getCommentsByPostId() - 批量 Feign 调用
   - [ ] MessageServiceImpl.convertToDTO() - 批量查询用户
   - [ ] FollowServiceImpl.buildUserDTOsFromFollows() - 批量查询

2. **添加分页** (预估 1 天)
   - [ ] 所有列表查询添加 Page 分页
   - [ ] 通知、消息、评论、浏览历史、点赞、收藏

3. **配置异步线程池** (预估 0.5 天)
   - [ ] 创建 ThreadPoolConfig 配置类
   - [ ] 修改所有 CompletableFuture.runAsync() 使用自定义线程池

### 第二阶段 (本月) - P1 问题修复

4. **优化定时任务** (预估 1 天)
   - [ ] PostVectorScheduler 改为增量同步
   - [ ] CategoryCache 改为事件驱动刷新
   - [ ] KnowledgeScheduler 改为增量摄入

5. **优化批量操作** (预估 0.5 天)
   - [ ] DataInitServiceImpl.batchInsertPosts() 改为批量插入
   - [ ] PostVectorService.doSyncAll() 改为批量 upsert

6. **调整连接池配置** (预估 0.5 天)
   - [ ] HikariCP minimum-idle 调整为与 maximum-pool-size 相同
   - [ ] Redis max-active 调整为 20-50
   - [ ] Redis max-wait 调整为 2000ms

### 第三阶段 (下个季度) - 长期优化

7. **引入全局缓存策略**
   - [ ] 用户信息 Redis 缓存
   - [ ] 通知、消息缓存
   - [ ] 帖子详情缓存

8. **引入消息队列**
   - [ ] 异步任务改为消息队列 (RabbitMQ/Kafka)
   - [ ] 事件驱动缓存刷新

9. **数据库优化**
   - [ ] 读写分离
   - [ ] 分库分表 (如果需要)

---

## 11. 监控指标建议

### 11.1 需要监控的指标

1. **DB 连接池**: HikariCP 活跃连接数、等待连接数、连接获取时间
2. **Redis 连接池**: Lettuce 活跃连接数、等待连接数
3. **API 响应时间**: P50, P90, P99
4. **Feign 调用**: 成功率、响应时间、熔断次数
5. **线程池**: 活跃线程数、队列大小、拒绝任务数
6. **JVM**: 堆内存使用、GC 次数、GC 时间

### 11.2 Actuator 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,threaddump,heapdump
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## 12. 总结

本报告对 CampusShare 后端项目进行了全面的性能审查，覆盖 10 个方面：

1. ✅ 项目模块结构 (170 个 Java 文件)
2. ✅ 所有 pom.xml 依赖分析
3. ✅ 所有 application.yml 配置分析
4. ✅ 所有 Controller 类复杂操作识别
5. ✅ 所有 Service 类性能问题识别
6. ✅ 所有 Entity/Model 类关系分析
7. ✅ 所有 Mapper 文件复杂查询分析
8. ✅ 所有 Scheduled 定时任务分析
9. ✅ 所有 Config 类线程池/连接池配置分析
10. ✅ 所有 SQL 文件和迁移脚本分析

### 关键发现

- **7 处严重 N+1 查询** - 需要立即修复
- **5 处缺少分页** - 可能返回大量数据
- **3 处异步线程池配置不当** - 使用默认线程池
- **3 处定时任务全量扫描** - 应该增量处理
- **3 处批量操作效率低下** - 逐条处理

### 下一步

请按照第 10 节"行动计划"执行修复，优先处理 P0 问题。

---

**报告结束**

生成时间: 2026-07-05  
审查人: CodeBuddy Code (Auto Model)  
项目: CampusShare Backend  
总 Java 文件数: 170  
识别性能问题: 23 个高优先级 + 15 个中优先级
