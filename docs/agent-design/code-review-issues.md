# Code Review 问题清单:SystemPrompt + 意图识别 + 知识库管理 + 长期记忆

> 审查日期: 2026-07-08(初始)/ 2026-07-10(更新)
> 审查范围: campushare-agent 模块的 SystemPrompt、意图识别、知识库管理、长期记忆四大模块
> 验证方式: 双重子代理交叉验证(2/2 高置信度确认)
> 严重程度: Critical × 4, Major × 4, 功能缺失 × 7(长期记忆模块)

---

## 问题汇总表

| 编号 | 严重程度 | 问题标题 | 文件 |
|------|----------|----------|------|
| 1 | Major | rollback() 日志 from 字段打印新版本号 | KnowledgeVersionService.java |
| 2 | Critical | upsertChunks() 先删后插无 @Transactional | KnowledgeVectorStore.java |
| 3 | Critical | rollback 跨 MySQL/PG 双源无事务 | InternalAgentController.java |
| 4 | Major | AgentChatService 手动 new ObjectMapper() | AgentChatService.java |
| 5 | Major | md5() 手动实现与项目不一致 | KnowledgeIngestionService.java |
| 6 | Major | cacheService.put().subscribe() 吞错误 | IntentClassifier.java |
| 7 | Critical | getCurrentVersion() 每请求查 DB 无缓存 | PromptVersionManager.java |
| 8 | Critical | 更新文档时重复检测误判自身旧版本导致跳过更新 | KnowledgeIngestionService.java |
| 9 | 功能缺失 | 长期记忆:INFERRED 行为推断通道未实现 | LongTermMemoryService.java |
| 10 | 功能缺失 | 长期记忆:向量检索未实现(memory_vectors 表未创建) | LongTermMemoryService.java |
| 11 | 功能缺失 | 长期记忆:冲突仲裁未实现(直接覆盖不检测冲突) | LongTermMemoryService.java |
| 12 | 功能缺失 | 长期记忆:used_memory_ids 回写未实现 | LongTermMemoryService.java |
| 13 | 功能缺失 | 长期记忆:用户记忆管理 API 未实现 | — |
| 14 | 功能缺失 | 长期记忆:证据表和审计表闲置 | UserMemoryMapper.java |
| 15 | 功能缺失 | 长期记忆:物理清除和记忆恢复未实现 | LongTermMemoryService.java |

---

## 问题详情

### 问题 1: rollback() 日志 from 字段打印新版本号而非旧版本号

- **严重程度**: Major
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeVersionService.java`
- **行号**: 126-127

**问题描述**:
在 `rollback()` 方法中,日志输出 `"Rollback completed: articleId={}, from={}, to={}, newVersion={}"`,其中 `from=current.getVersion()`。但在 L123,`current.setVersion(nextVer.toString())` 已经将 current 的版本号覆盖为新版本号,因此 `from` 打印的是新版本号而非旧版本号。

**修复建议**:
在 `setVersion` 前保存旧版本号到局部变量,日志使用该变量:
```java
String oldVersion = current.getVersion();
SemVer nextVer = SemVer.parseOrInitial(current.getVersion()).nextPatch();
// ... setVersion ...
log.info("Rollback completed: articleId={}, from={}, to={}, newVersion={}",
        articleId, oldVersion, targetVersion, nextVer);
```

---

### 问题 2: upsertChunks() 先删后插无 @Transactional

- **严重程度**: Critical
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/store/KnowledgeVectorStore.java`
- **行号**: 37-69

**问题描述**:
`upsertChunks()` 方法先执行 `DELETE FROM knowledge_vectors WHERE article_id = ?`,再执行 `jdbcTemplate.batchUpdate(sql, batchArgs)` 插入新分块。两个操作之间没有 `@Transactional` 注解,如果 INSERT 失败(如 PG 连接断开、约束冲突),DELETE 已提交,该文章的所有向量数据丢失,导致检索无法命中该文章。

**修复建议**:
方法添加 `@Transactional` 注解(需确认 PgVectorConfig 配置了 `DataSourceTransactionManager`):
```java
@Transactional(transactionManager = "pgvectorTransactionManager")
public void upsertChunks(Long articleId, List<Chunk> chunks, List<float[]> embeddings, ...) {
    jdbcTemplate.update("DELETE FROM knowledge_vectors WHERE article_id = ?", articleId);
    // ... batchUpdate ...
}
```

---

### 问题 3: rollback 跨 MySQL/PG 双源无事务保证

- **严重程度**: Critical
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/controller/InternalAgentController.java`
- **行号**: 92-94

**问题描述**:
`InternalAgentController.rollback()` 先调用 `knowledgeVersionService.rollback(id, version)`(更新 MySQL 主表),再调用 `knowledgeIngestionService.reingestArticle(id)`(重新分块 + embedding + 写 PG 向量)。这两个操作跨数据源(MySQL + PostgreSQL),没有分布式事务或补偿机制。如果 `reingestArticle` 失败(如 Embedding API 超时),MySQL 已回滚到旧版本内容,但 PG 向量库仍是旧版本的向量,导致检索结果与实际文章内容不一致。

**修复建议**:
- 方案 A(补偿队列): reingest 失败时记录补偿任务到 Redis 队列,定时任务重试
- 方案 B(先 PG 后 MySQL): 先 reingestArticle 成功后再提交 MySQL 事务(但需 reingest 支持基于新内容的摄入)
- 方案 C(标记不一致): reingest 失败时标记 article 状态为 `NEEDS_REINDEX`,检索时跳过或降权

---

### 问题 4: AgentChatService 手动 new ObjectMapper() 未复用 Spring Bean

- **严重程度**: Major
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/service/AgentChatService.java`
- **行号**: 69

**问题描述**:
`private final ObjectMapper objectMapper = new ObjectMapper();` 手动创建了 ObjectMapper 实例,而非注入 Spring 容器中已配置的 Bean。`application.yml` 中配置了 `spring.jackson.date-format: yyyy-MM-dd HH:mm:ss` 和 `spring.jackson.time-zone: GMT+8`,但这些配置不会应用到手动 new 的实例上。`loadPreviousRetrieval()` 和 `formatRetrievalContextJson()` 反序列化/序列化 `RetrievalResult`(含时间相关字段)时可能出现日期格式不一致。

**修复建议**:
删除 `new ObjectMapper()`,通过构造注入 Spring Bean:
```java
// 删除: private final ObjectMapper objectMapper = new ObjectMapper();
// 在构造函数参数中添加 ObjectMapper(已有 @RequiredArgsConstructor,只需添加 final 字段)
private final ObjectMapper objectMapper;
```

---

### 问题 5: md5() 手动实现与项目其他模块不一致

- **严重程度**: Major
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java`
- **行号**: 314-326

**问题描述**:
`KnowledgeIngestionService` 用 `MessageDigest` 手动实现了 MD5 计算(约 12 行代码),而同项目的 `RetrievalService.buildCacheKey()` 和 `IntentCacheService.buildKey()` 都使用 Spring 提供的 `DigestUtils.md5DigestAsHex()` 一行代码。同一项目内 MD5 实现方式不一致,属于重复代码。

**修复建议**:
替换为 Spring 工具方法:
```java
import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;

private String md5(String content) {
    return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
}
```

---

### 问题 6: cacheService.put().subscribe() fire-and-forget 吞掉错误

- **严重程度**: Major
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/service/IntentClassifier.java`
- **行号**: 92

**问题描述**:
`cacheService.put(query, result).subscribe()` 是 fire-and-forget 模式,没有提供 error handler。虽然 `IntentCacheService.put()` 内部有 try-catch 返回 `Mono.empty()`,但如果 Mono 链路中产生未预期的错误(如 Reactor 调度器异常、序列化错误),错误会被 Reactor 默认策略吞掉,仅打印到 stderr,不被业务日志捕获。

**修复建议**:
添加 error handler:
```java
cacheService.put(query, result)
        .subscribe(
                v -> {},
                e -> log.warn("Intent cache put failed for query='{}': {}", query, e.getMessage())
        );
```

---

### 问题 7: getCurrentVersion() 每请求查 DB,未缓存 PromptVersion 对象

- **严重程度**: Critical
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/prompt/PromptVersionManager.java`
- **行号**: 108

**问题描述**:
`getCurrentVersion(userId)` 每次调用都执行 `versionMapper.findByVersion(currentVersion)` 查询 MySQL 获取 Prompt 内容。虽然 Redis 缓存了当前版本号(`agent:prompt:current_version` key),但 PromptVersion 对象(含各层 Prompt 文本,约 2-5KB)没有缓存。`AgentChatService.prepareContext()` 每次对话都会调用此方法,在高并发场景下(如 100 QPS),每秒 100 次 DB 查询会成为瓶颈。

**修复建议**:
方案 A(Redis 缓存): 将 PromptVersion 序列化到 Redis,key 为 `agent:prompt:version:{version}`,TTL 5min。版本切换/灰度调整时主动删除缓存:
```java
public PromptVersion getCurrentVersion(String userId) {
    String currentVersion = redis.opsForValue().get(CURRENT_VERSION_KEY);
    String cacheKey = "agent:prompt:version:" + currentVersion;
    // 1. 先查 Redis 缓存
    String cached = redis.opsForValue().get(cacheKey);
    if (cached != null) {
        return objectMapper.readValue(cached, PromptVersion.class);
    }
    // 2. 未命中查 DB
    PromptVersion current = versionMapper.findByVersion(currentVersion);
    // 3. 写入缓存
    redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(current), Duration.ofMinutes(5));
    return current;
}
```

方案 B(Caffeine 本地缓存): 用 Caffeine 做本地缓存(TTL 5min, maximumSize=10),避免 Redis 网络开销。版本切换时调用 `cache.invalidateAll()`。

---

### 问题 8: 更新文档时重复检测误判自身旧版本导致跳过更新

- **严重程度**: Critical
- **文件**: `backend/campushare-agent/src/main/java/com/campushare/agent/service/KnowledgeIngestionService.java`
- **行号**: 147-154(重复检测调用处)
- **关联文件**:
  - `backend/campushare-agent/src/main/java/com/campushare/agent/service/ThresholdDuplicateDetector.java` L57(未排除自身 articleId)
  - `backend/campushare-agent/src/main/java/com/campushare/agent/store/KnowledgeVectorStore.java` L139-157(findSimilar SQL 未排除自身)

**问题描述**:

在 `ingestAll()` 流程中,重复检测对**新增**和**更新**两种情况都会执行,且 `findSimilar` 查询没有排除当前文章自身的 `article_id`。当更新一篇已有文档时,如果文档的第一个分块(chunk_index=0)内容未发生变化(仅后面的分块内容有修改),重复检测会用第一个分块的 embedding 去 PG 查询,命中自身旧版本的 chunk[0],相似度 ≈ 1.0 ≥ 0.95,被误判为 DUPLICATE,导致更新被跳过。

**触发条件**:

同时满足以下三个条件即触发:
1. 文档已存在于知识库(通过 title 匹配到 `existing` 记录,走更新流程)
2. 文档正文内容有修改(MD5 不同,通过了 L120 的 skip 判断)
3. 文档的第一个分块(chunk_index=0)内容未变化(第一个 H2 段落未改)

**触发场景示例**:

假设 `01-registration-guide.md` 内容如下:
```markdown
---
title: 注册指南
topic: 01-auth
---

# 注册指南

## 步骤一          ← 第一个 chunk 包含此处,内容未改
打开APP点击注册

## 步骤二          ← 第二个 chunk,改了内容
填写手机号码       ← 原来是"填写手机号",改成了"填写手机号码"
```

**错误执行流程**:

```
① parseFile → doc.content = 整个正文(已修改)

② md5 = md5(doc.content)
   正文改了 → md5 ≠ existing.getContentMd5()
   → 不会被 skipped,继续往下走 ✓

③ chunker.chunk(doc.content) → [chunk0, chunk1]
   chunk0 = "# 注册指南\n## 步骤一\n打开APP点击注册"  (内容未变)
   chunk1 = "## 步骤二\n填写手机号码"                (内容变了)

④ embeddings = embedBatch([chunk0文本, chunk1文本])

⑤ L147: duplicateDetector.detect(doc.content, embeddings.get(0))
                                                     ↑
                                              用 chunk0 的 embedding
   │
   ├─ ThresholdDuplicateDetector.detect() L57:
   │  knowledgeVectorStore.findSimilar(embedding, 1)
   │
   ├─ KnowledgeVectorStore.findSimilar() L141-148:
   │  SQL: SELECT article_id, 1-(embedding <=> ?::vector) AS similarity
   │       FROM knowledge_vectors
   │       WHERE chunk_index = 0 AND status = 'PUBLISHED'
   │       ORDER BY embedding <=> ?::vector LIMIT 1
   │
   │  ↑ 问题所在:没有排除 article_id = existing.id
   │  chunk0 内容没变 → 和 PG 里自身旧版本的 chunk[0] 相似度 ≈ 1.0
   │
   ├─ similarity ≥ 0.95 → 判定 DUPLICATE
   │
   └→ L148-153: duplicated++; continue;  ← 跳过!更新未执行 ✗

   L160 的更新流程(if existing != null)永远不会执行
```

**问题根源**:

1. `KnowledgeIngestionService.ingestAll()` L147:重复检测在 `existing != null`(更新)和 `existing == null`(新增)两种情况下都会执行,没有区分
2. `ThresholdDuplicateDetector.detect()` L57:`findSimilar(embedding, 1)` 查询所有 `chunk_index=0` 的记录,没有传入 `excludeArticleId` 参数
3. `KnowledgeVectorStore.findSimilar()` L141-148:SQL 的 WHERE 条件没有 `AND article_id != ?` 排除当前文章

**影响范围**:

- 文档末尾追加新章节(第一个 chunk 不变)→ 更新被跳过,新章节不会入库
- 修改文档中间或后面的内容(第一个 chunk 不变)→ 更新被跳过,修改不生效
- 修改文档格式、错别字(第一个 chunk 不变)→ 更新被跳过
- 由于 MarkdownChunker 按 H2 标题分块,只要第一个 H2 段落内容没变就会触发

实际影响:知识库文档更新后,PG 向量库仍是旧版本内容,检索结果过时,且无任何错误日志(只有 "Skip duplicated doc" 的 info 日志),难以发现。

**修复建议**:

方案 A(推荐,最小改动):更新时跳过重复检测

在 `KnowledgeIngestionService.ingestAll()` 中,仅对新增文档执行重复检测:

```java
// L146-154 修改为:
// 重复检测(仅新增文档执行,更新文档已通过 MD5 确认内容变更)
if (existing == null) {
    DuplicateDetectionResult duplicateResult = duplicateDetector.detect(doc.content, embeddings.get(0));
    if (duplicateResult.isDuplicate()) {
        log.info("Skip duplicated doc: {} (matched articleId={}, similarity={})",
                doc.title, duplicateResult.matchedArticleId(), duplicateResult.similarity());
        duplicated++;
        metricsConfig.recordIngest("DUPLICATED");
        continue;
    }
    if (duplicateResult.isSimilar()) {
        log.warn("Similar content detected for doc: {} (matched articleId={}, similarity={}), ingesting anyway",
                doc.title, duplicateResult.matchedArticleId(), duplicateResult.similarity());
    }
}
```

理由:更新流程已经通过 title 匹配确认是同一篇文章,且通过 MD5 确认内容有变更,不需要再重复检测。重复检测的设计意图是防止"不同 title 但内容相同"的新文档被重复摄入。

方案 B(findSimilar 排除自身 articleId):

修改 `findSimilar` 方法签名,增加 `excludeArticleId` 参数:

```java
// KnowledgeVectorStore.java
public SimilarMatch findSimilar(float[] embedding, int topK, Long excludeArticleId) {
    String vectorStr = toVectorString(embedding);
    String sql = """
            SELECT article_id, 1 - (embedding <=> ?::vector) AS similarity
            FROM knowledge_vectors
            WHERE chunk_index = 0
              AND status = 'PUBLISHED'
              AND article_id != ?    -- 排除自身
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;
    // ... executeQuery with excludeArticleId parameter
}

// ThresholdDuplicateDetector.java
public DuplicateDetectionResult detect(String content, float[] embedding, Long excludeArticleId) {
    // ...
    KnowledgeVectorStore.SimilarMatch match = knowledgeVectorStore.findSimilar(embedding, 1, excludeArticleId);
    // ...
}

// KnowledgeIngestionService.java L147
DuplicateDetectionResult duplicateResult = duplicateDetector.detect(
    doc.content, embeddings.get(0), existing != null ? existing.getId() : null
);
```

方案 C(接口兼容,组合方案):

如果 `KnowledgeDuplicateDetector` 接口被多处调用不想改签名,可新增重载方法:

```java
// KnowledgeDuplicateDetector 接口新增默认方法
default DuplicateDetectionResult detect(String content, float[] embedding) {
    return detect(content, embedding, null);
}
DuplicateDetectionResult detect(String content, float[] embedding, Long excludeArticleId);
```

**推荐方案 A**,因为更新时重复检测本身没有业务意义(MD5 已确认变更),跳过即可,无需改接口签名。

**验证方法**:

修复后用以下场景测试:
1. 新增文档 → 重复检测正常执行,内容相同的被跳过
2. 更新文档(第一个 chunk 不变,后面改了)→ 不执行重复检测,正常更新入库
3. 更新文档(第一个 chunk 改了)→ 不执行重复检测,正常更新入库
4. 新增与已有文档内容相同的文档(title 不同)→ 重复检测命中,被跳过

---

## 补充说明

以上 15 个问题中,问题 1-7 经双重子代理交叉验证(2/2 高置信度确认存在),问题 8 在代码走查中发现并经人工确认,问题 9-15 为长期记忆模块与设计方案文档对比发现的功能缺失。

Critical 级别问题(建议优先修复):
- 问题 2(无事务): 数据丢失风险,upsertChunks 先删后插无 @Transactional
- 问题 3(跨源无事务): 数据不一致风险,rollback 跨 MySQL/PG 无补偿机制
- 问题 7(无缓存): 高并发性能瓶颈,getCurrentVersion 每请求查 DB
- 问题 8(重复检测误判): 知识库更新失效,第一个 chunk 不变时更新被误判为重复而跳过

---

## 长期记忆模块:设计方案 vs 代码实现对比

> 对比日期: 2026-07-10
> 设计文档: `docs/agent-design/长期记忆模块设计方案.docx`
> 代码实现: `LongTermMemoryService.java` + `UserMemory.java` + `UserMemoryMapper.java`
> 整体完成度: 约 30-35%

### 架构对比

| 设计文档规划 | 代码实现 | 状态 |
|---|---|---|
| 6 个独立类(MemoryExtractor/Retrieval/Update/Decay/VectorStore/ConflictResolver) | 合并为 1 个 LongTermMemoryService | 架构简化 |
| PREFERENCE/FACT/SKILL/EVENT 四分类 | PREFERENCE/FACT/BEHAVIOR/TASK 四象限 | 分类不同 |
| EXPLICIT + INFERRED 双通道采集 | 仅 EXPLICIT 通道 | 50% |
| 向量 + 关键词双路检索 | 仅 MySQL 关键词匹配 | 30% |
| LLM 冲突仲裁(KEEP_NEW/OLD/BOTH) | 直接覆盖,无冲突检测 | 0% |
| 三层遗忘(日衰减+软删除+物理清除) | 两层(周衰减+软删除) | 60% |
| used_memory_ids 回写 agent_context_snapshots | 未实现 | 0% |
| 用户可见可控 API(查询/删除记忆) | 未实现 | 0% |
| memory_vectors 表(PostgreSQL 向量索引) | 未创建 | 0% |
| user_memory_evidence 证据表 | 表存在但代码不用 | 闲置 |
| user_memory_history 审计表 | 表存在但代码不用 | 闲置 |
| 记忆恢复(软删除期间再次提及→恢复) | 未实现 | 0% |

### 已实现的功能(✅)

1. **EXPLICIT 记忆抽取**: 会话归档时从滚动摘要 LLM 抽取显式偏好,UPSERT 到 MySQL
2. **画像装载**: `loadUserProfile()` 按相关性+优先级排序取 Top-5,格式化为画像文本(≤300字)
3. **UPSERT 累加**: 已存在同 type+key 时 confidence += 0.1(封顶 1.0),evidence_count += 1
4. **周衰减**: 每周日 02:00,BEHAVIOR 周 *0.9,TASK 周 *0.7,EXPLICIT 不衰减
5. **软删除**: confidence ≤ 0.3 或 TASK 4 周未更新 → 设置 deletedAt
6. **L1 层注入**: 画像文本作为 L1 层追加到 system prompt 末尾(通过 ContextAssembler)

---

### 问题 9: 长期记忆 INFERRED 行为推断通道未实现

- **严重程度**: 功能缺失
- **文件**: `LongTermMemoryService.java`
- **设计文档**: 第 3.3 节"记忆采集流水线"

**问题描述**:
设计文档规划了双通道采集:
- 通道 A(EXPLICIT):用户明说 → LLM 抽取 → confidence=1.0
- 通道 B(INFERRED):行为证据累积 evidence_count≥3 → LLM 推断 → confidence=0.6

代码只实现了通道 A(会话归档时从摘要抽取显式偏好)。通道 B 完全未实现:
- 没有 `user_memory_evidence` 表的写入逻辑
- 没有行为事件记录(QUERY/FEEDBACK/TOOL_CALL)
- 没有 evidence_count 累积触发推断的机制
- BEHAVIOR 类型记忆无法产生(衰减任务会查 BEHAVIOR 类型记忆,但永远不会有条目)

**影响**: Agent 无法学习用户的隐式偏好(如"用户连续 3 次问 Python 问题"应该推断出用户偏好 Python),个性化能力受限。

**修复建议**:
1. 新增 `MemoryEvidenceService`,在用户查询/反馈时写入 `user_memory_evidence` 表
2. 定时任务扫描 `evidence_count >= 3` 的证据,调 LLM 推断记忆
3. 推断的记忆 source=INFERRED,confidence=0.6,后续每次证据累加 confidence 上升

---

### 问题 10: 长期记忆向量检索未实现

- **严重程度**: 功能缺失
- **文件**: `LongTermMemoryService.java`
- **设计文档**: 第 3.4 节"记忆检索策略" + 第 4.8 节"数据库 Schema"

**问题描述**:
设计文档规划了双路检索:
- 向量召回:query embedding → memory_vectors 表 HNSW 检索 top-5
- 关键词补充:pg_trgm 模糊匹配 memory_value

代码的 `loadUserProfile()` 只做了 MySQL 关键词匹配(LambdaQueryWrapper 查 user_memory 表),按相关性+优先级排序。没有:
- `memory_vectors` PostgreSQL 表未创建
- `MemoryVectorStore` 类不存在
- 没有对 memory_value 做 embedding
- 没有向量相似度检索
- 没有相关性评分公式(0.5×confidence + 0.3×similarity + 0.2×recency)

**影响**: 记忆检索只能靠 SQL 关键词匹配,无法做语义检索。用户问"我喜欢什么编程语言"时,无法语义匹配到"preferred_language: Python"这条记忆(因为 key/value 里没有"编程语言"这个词)。

**修复建议**:
1. 在 PostgreSQL 创建 `memory_vectors` 表(设计文档 4.8 节有 DDL)
2. 新增 `MemoryVectorStore` 类,实现 search/keywordSearch/upsert/delete
3. `loadUserProfile` 改为双路检索:PROFILE 全量装载 + EVENT 向量召回
4. 实现相关性评分:score = 0.5×confidence + 0.3×similarity + 0.2×recency

---

### 问题 11: 长期记忆冲突仲裁未实现

- **严重程度**: 功能缺失
- **文件**: `LongTermMemoryService.java` L324-368(upsertMemory 方法)
- **设计文档**: 第 3.5 节"记忆更新与冲突处理"

**问题描述**:
设计文档规划了完整的冲突处理流程:
- 新记忆 value ≠ 旧记忆 value → 标记 conflict_flag=1
- `ConflictResolver` LLM 仲裁:KEEP_NEW / KEEP_OLD / KEEP_BOTH
- 仲裁后旧值归档到 `user_memory_history` 表

代码的 `upsertMemory()` 逻辑:
```java
if (existing != null) {
    // 已存在:直接覆盖 value,confidence += 0.1
    existing.setMemoryValue(value);  // ← 不比较 value 是否冲突,直接覆盖
    existing.setConfidence(newConfidence);
    userMemoryMapper.updateById(existing);
}
```

**场景**: 用户先说"我喜欢Python"(confidence=1.0),后说"我喜欢Java"
- 设计文档:标记冲突 → LLM 仲裁 → KEEP_NEW(用户改口)→ 旧值归档到 history
- 代码实际:直接覆盖为"Java",confidence=1.1→封顶1.0,无冲突记录,旧值丢失

**影响**: 用户改口时旧偏好被静默覆盖,无法追溯偏好变化历史,无法人工复核冲突。

**修复建议**:
1. upsertMemory 中比较新旧 value,不同则触发冲突处理
2. 新增 `ConflictResolver` 类,用 LLM 仲裁(设计文档 3.5.1 有 Prompt)
3. 仲裁结果写入 `user_memory_history` 表(action=UPDATE)

---

### 问题 12: 长期记忆 used_memory_ids 回写未实现

- **严重程度**: 功能缺失
- **文件**: `LongTermMemoryService.java` + `ContextSnapshotService.java`
- **设计文档**: 第 3.7 节"工作记忆与长期记忆交互" + ADR-MEM-07

**问题描述**:
设计文档规划了工作记忆与长期记忆的三个交互点,其中"回写"未实现:
- 装载(✅ 已实现):长期记忆 → 工作记忆(L1 画像层)
- **回写(❌ 未实现)**:每轮对话结束 → used_memory_ids 写入 agent_context_snapshots
- 抽取(✅ 已实现):会话归档时 → LLM 抽取新记忆

`agent_context_snapshots` 表的 `used_memory_ids` 字段已预留但闲置。`loadUserProfile()` 不返回 used_memory_ids,`ContextSnapshotService.saveSnapshot()` 不写入该字段。

**影响**: 无法审计"这轮用了哪些记忆",遗忘机制的"使用频率"统计缺少数据源。

**修复建议**:
1. `loadUserProfile` 返回值增加 `List<Long> usedMemoryIds`
2. `ContextSnapshotService.saveSnapshot` 将 usedMemoryIds 序列化写入 `used_memory_ids` 字段
3. 衰减任务可查 `used_memory_ids` 统计使用频率

---

### 问题 13: 长期记忆用户记忆管理 API 未实现

- **严重程度**: 功能缺失
- **设计文档**: 第 5.1 节"功能目标" — "用户可见可控"

**问题描述**:
设计文档要求用户可查看和管理自己的记忆:
- `GET /agent/memories` — 查看全部记忆
- `DELETE /agent/memories/{id}` — 删除单条记忆

代码中没有 `UserMemoryController`,用户无法查看 Agent 记住了什么,也无法删除错误记忆。

**影响**: 用户无法管理自己的隐私数据,无法纠正 Agent 的错误记忆(如"我不是计算机专业的")。

**修复建议**:
1. 新增 `UserMemoryController`,提供 GET/DELETE 接口
2. DELETE 时走软删除(设置 deletedAt),写入 user_memory_history 审计

---

### 问题 14: 长期记忆证据表和审计表闲置

- **严重程度**: 功能缺失
- **文件**: `UserMemoryMapper.java`
- **设计文档**: 第 4.8 节"数据库 Schema" + ADR-MEM-01

**问题描述**:
MySQL 中已创建两张表(agent-init.sql 定义),但代码中没有任何写入逻辑:
- `user_memory_evidence`(证据表):记录行为证据(QUERY/FEEDBACK/TOOL_CALL),用于 INFERRED 推断
- `user_memory_history`(审计表):记录 UPDATE/DELETE/DECAY 操作,用于追溯

**影响**:
- 证据表闲置 → INFERRED 通道无法实现(问题 9)
- 审计表闲置 → 记忆变更无审计追踪,冲突仲裁结果不归档,衰减操作不记录

**修复建议**:
1. 实现 INFERRED 通道时写入 evidence 表(关联问题 9)
2. upsertMemory/softDeleteMemory/decayMemories 操作后写入 history 表

---

### 问题 15: 长期记忆物理清除和记忆恢复未实现

- **严重程度**: 功能缺失
- **文件**: `LongTermMemoryService.java`
- **设计文档**: 第 3.6 节"遗忘机制"

**问题描述**:
设计文档三层遗忘机制,代码只实现了前两层:

| 层 | 设计文档 | 代码实现 |
|---|---|---|
| Layer 1 衰减 | 每天凌晨2点 confidence *= 0.95^days | 每周日02:00 BEHAVIOR *0.9, TASK *0.7 |
| Layer 2 软删除 | 每天凌晨2:30 confidence<0.3 且 30天未使用 | confidence≤0.3 或 TASK 4周未更新 |
| Layer 3 物理清除 | 每天凌晨3点 软删除30天后 DELETE | ❌ 未实现 |

另外,设计文档的"记忆恢复"机制未实现:
- 软删除期间用户再次提及 → 恢复(deletedAt置NULL, confidence重置为0.5)
- 代码的 `upsertMemory` 不查软删除的记忆,会直接新增一条,旧记忆留在回收站里

**影响**:
- 物理清除未实现:软删除的记忆永远留在表中,存储持续增长
- 记忆恢复未实现:用户30天前说"喜欢PDF",被软删除后再次说"喜欢PDF"会新增一条而非恢复

**修复建议**:
1. 新增 `@Scheduled(cron = "0 0 3 * * ?")` 物理清除任务
2. `upsertMemory` 查询时不过滤 deletedAt,发现软删除的同 key 记忆则恢复
