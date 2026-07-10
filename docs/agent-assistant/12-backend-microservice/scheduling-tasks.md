# 定时任务设计

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、任务清单

| 任务 | 频率 | 用途 |
|------|------|------|
| SessionArchiveScheduler | 每分钟 | 归档 30 分钟未活跃的会话 |
| ZombieSessionCleaner | 每 5 分钟 | 清理 Redis 中已过期但未归档的僵尸会话 |
| MemoryDecayScheduler | 每周日 02:00 | 长期记忆衰减 |
| BehaviorEvidencePuller | 每小时 | 从 post-service 拉取用户行为证据 |
| KnowledgeReindexScheduler | 每天 03:00 | 增量重建知识库向量索引 |
| PendingWriteRetryScheduler | 每 30 秒 | 重试失败的异步写入 |

## 二、SessionArchiveScheduler

### 2.1 职责

扫描 Redis 中所有 `agent:session:*:meta` Key，发现 `last_active_at` > 30 分钟且 `status=ACTIVE` 的会话，触发归档。

### 2.2 实现

```java
@Component
@RequiredArgsConstructor
public class SessionArchiveScheduler {
    private final SessionArchiveService archiveService;
    private final RedisTemplate<String, String> redis;

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "sessionArchive", lockAtMostFor = "55s")
    public void archive() {
        ScanOptions options = ScanOptions.scanOptions()
            .match("agent:session:*:meta").count(100).build();
        try (Cursor<byte[]> cursor = redis.getConnection().scan(options)) {
            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                String status = redis.opsForHash().get(key, "status").toString();
                if (!"ACTIVE".equals(status)) continue;

                String lastActiveStr = redis.opsForHash().get(key, "last_active_at").toString();
                long lastActive = Long.parseLong(lastActiveStr);
                if (System.currentTimeMillis() - lastActive > 30 * 60 * 1000) {
                    String sessionId = extractSessionId(key);
                    archiveService.archive(sessionId);
                }
            }
        }
    }
}
```

### 2.3 ShedLock 分布式锁

多实例部署时，用 ShedLock 保证同一时刻只有一个实例执行归档：

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.10.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-spring</artifactId>
    <version>5.10.0</version>
</dependency>
```

```java
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@Configuration
public class ShedLockConfig {
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "agent-service");
    }
}
```

## 三、ZombieSessionCleaner

### 3.1 职责

处理 Redis Key 自然过期但未归档的会话（如服务重启期间 TTL 到期）。

### 3.2 实现

- 查询 MySQL `agent_sessions` 表中 `status=ACTIVE` 且 `last_active_at` > 30 分钟的会话。
- 这些会话的 Redis Key 已过期，直接在 MySQL 标记为 ARCHIVED，触发归档流程。
- 若归档失败，记录到 `agent_archive_failures` 表。

```java
@Scheduled(fixedRate = 5 * 60_000)
@SchedulerLock(name = "zombieClean", lockAtMostFor = "4m")
public void cleanZombies() {
    List<AgentSession> zombies = sessionMapper.findZombies(
        LocalDateTime.now().minusMinutes(30)
    );
    for (AgentSession session : zombies) {
        try {
            archiveService.archiveFromDb(session.getId());
        } catch (Exception e) {
            log.error("Archive zombie failed: {}", session.getId(), e);
            archiveFailureMapper.insert(session.getId(), e.getMessage());
        }
    }
}
```

## 四、MemoryDecayScheduler

### 4.1 职责

每周日 02:00 执行长期记忆衰减：
- INFERRED BEHAVIOR 类记忆 confidence *= 0.9，<0.3 删除。
- TASK 类记忆 4 周未更新删除。
- EXPLICIT 不衰减。

### 4.2 实现

```java
@Scheduled(cron = "0 0 2 * * SUN")
@SchedulerLock(name = "memoryDecay", lockAtMostFor = "30m")
public void decay() {
    // 1. 衰减 INFERRED BEHAVIOR
    int decayed = memoryMapper.decayInferredBehaviors(0.9, 0.3);
    log.info("Decayed {} inferred behaviors", decayed);

    // 2. 删除过期 TASK
    int deleted = memoryMapper.deleteOldTasks(4); // 4 weeks
    log.info("Deleted {} old tasks", deleted);

    // 3. 记录衰减日志
    memoryDecayLogMapper.insert(decayed, deleted, LocalDateTime.now());
}
```

### 4.3 SQL

```sql
-- 衰减
UPDATE user_memory
SET confidence = confidence * 0.9
WHERE source = 'INFERRED' AND memory_type = 'BEHAVIOR'
  AND deleted_at IS NULL;
-- 删除低置信
UPDATE user_memory
SET deleted_at = NOW()
WHERE source = 'INFERRED' AND memory_type = 'BEHAVIOR'
  AND confidence < 0.3 AND deleted_at IS NULL;
-- 删除过期 TASK
UPDATE user_memory
SET deleted_at = NOW()
WHERE memory_type = 'TASK'
  AND updated_at < DATE_SUB(NOW(), INTERVAL 4 WEEK)
  AND deleted_at IS NULL;
```

## 五、BehaviorEvidencePuller

### 5.1 职责

每小时从 post-service 拉取最近 1 小时的用户行为证据（点击/停留/点赞），写入 `user_memory_evidence` 表。

### 5.2 实现

```java
@Scheduled(cron = "0 0 * * * *")  // 每小时整点
@SchedulerLock(name = "evidencePull", lockAtMostFor = "10m")
public void pullEvidence() {
    long since = System.currentTimeMillis() - 3600_000;
    int page = 0;
    while (true) {
        List<BehaviorEvidenceDTO> evidences = postBehaviorFeignClient
            .getUserBehavior("__batch__", since, page, 500);
        if (evidences.isEmpty()) break;

        List<UserMemoryEvidence> entities = evidences.stream()
            .map(this::toEntity).collect(Collectors.toList());
        evidenceMapper.batchInsert(entities);

        page++;
        if (evidences.size() < 500) break;
    }
    log.info("Pulled {} evidence records", page * 500);
}
```

### 5.3 post-service 内部 API

post-service 需新增 `/api/internal/post/behavior` 接口，返回最近 N 小时的行为证据（所有用户）。

### 5.4 证据处理

拉取后由 `MemoryInferenceScheduler`（每小时 30 分）处理证据：
- 同一用户同一行为 ≥3 次：upsert 隐式记忆。
- 证据标记 `processed=1`。

## 六、KnowledgeReindexScheduler

### 6.1 职责

每天 03:00 检查知识库文档变更（content_md5 变化），增量重新 embedding。

### 6.2 实现

```java
@Scheduled(cron = "0 0 3 * * *")
@SchedulerLock(name = "knowledgeReindex", lockAtMostFor = "1h")
public void reindex() {
    List<KnowledgeArticle> changed = articleMapper.findChanged();
    log.info("Reindexing {} changed articles", changed.size());

    for (KnowledgeArticle article : changed) {
        try {
            float[] embedding = embeddingClient.embed(
                article.getTitle() + " " + article.getContent()
            );
            vectorMapper.upsertKnowledgeVector(
                article.getId(), article.getTitle(), article.getTopic(),
                article.getContent().substring(0, 500), article.getContentMd5(),
                article.getStatus(), article.getVersion(), embedding
            );
            articleMapper.markIndexed(article.getId());
        } catch (Exception e) {
            log.error("Reindex failed for article {}", article.getId(), e);
        }
    }
}
```

### 6.3 全量重建

管理后台可触发 `POST /api/admin/agent/knowledge/reindex`，跳过 md5 检查全量重建。用于：
- embedding 模型升级。
- 向量索引重建（如 HNSW 参数调整）。

## 七、PendingWriteRetryScheduler

### 7.1 职责

重试 `agent_pending_writes` 表中 PENDING 状态的写入（如 agent_turns 写入失败）。

### 7.2 实现

```java
@Scheduled(fixedRate = 30_000)
public void retryPendingWrites() {
    List<PendingWrite> pendings = pendingWriteMapper.findRetryable(10);
    for (PendingWrite pw : pendings) {
        try {
            pendingWriteExecutor.execute(pw);
            pw.setStatus("SUCCESS");
        } catch (Exception e) {
            pw.setRetries(pw.getRetries() + 1);
            pw.setNextRetryAt(LocalDateTime.now().plusMinutes(pw.getRetries() * 5));
            if (pw.getRetries() >= 5) pw.setStatus("FAILED");
        }
        pendingWriteMapper.update(pw);
    }
}
```

### 7.3 退避策略

重试间隔指数退避：5min → 10min → 15min → 20min → 25min，5 次后标记 FAILED 人工介入。

## 八、任务监控

### 8.1 Prometheus 指标

每个任务暴露：
```
agent_scheduled_task_duration_seconds{task_name}   # 执行耗时
agent_scheduled_task_success_total{task_name}      # 成功次数
agent_scheduled_task_failure_total{task_name}      # 失败次数
agent_scheduled_task_last_run_timestamp{task_name} # 上次执行时间
```

### 8.2 告警

| 任务 | 告警条件 | 级别 |
|------|---------|------|
| SessionArchiveScheduler | 5 分钟未执行 | P1 |
| MemoryDecayScheduler | 周日 02:00 未执行 | P2 |
| BehaviorEvidencePuller | 2 小时未执行 | P2 |
| 任意任务失败率 | >10% | P2 |
| PendingWrite FAILED 数 | >100 | P2 |

### 8.3 Grafana 面板

新增"Agent 定时任务"面板：
- 任务执行时间线（每个任务一行，绿/红点标记成功/失败）。
- 任务耗时趋势图。
- 失败任务 Top 10。

## 九、决策记录 (ADR)

### ADR-155: 定时任务用 @Scheduled + ShedLock
- **理由**：@Scheduled 简单，ShedLock 解决多实例重复执行。Quartz/Xxl-Job 过重。
- **代价**：ShedLock 依赖 Redis，Redis 不可用时任务停摆（可接受，因 Agent 整体也依赖 Redis）。

### ADR-156: 归档任务每分钟执行
- **理由**：30 分钟超时阈值 + 每分钟扫描，最坏情况会话在过期后 31 分钟被归档，延迟可接受。
- **替代**：每秒扫描——Redis SCAN 压力大；每 5 分钟——延迟过长。

### ADR-157: 行为证据每小时拉取
- **理由**：实时推送需 post-service 改造（发 MQ），成本高。每小时拉取增量对"小时级"画像更新足够。
- **未来**：引入 RabbitMQ/Kafka 后改为实时推送。

### ADR-158: 知识库重建在凌晨 03:00
- **理由**：低峰期，embedding API 调用不挤占在线请求。03:00 而非 00:00 避开其它服务备份窗口。
- **增量**：md5 检测变更，仅重建变更部分，正常情况下 <5 分钟。

### ADR-159: PendingWrite 指数退避最多 5 次
- **理由**：避免无限重试占用资源。5 次（约 75 分钟）后人工介入。
- **告警**：FAILED 数 >100 触发 P2，避免堆积无感知。

### ADR-160: 任务指标暴露 Prometheus
- **理由**：与现有服务监控一致（Grafana 已配 Prometheus 数据源）。
- **复用**：复用现有 Grafana 看板框架，新增 Agent 面板。
