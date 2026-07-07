package com.campushare.agent.service;

import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.ChunkResult;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.IntentResult.SlotResult;
import com.campushare.agent.dto.RetrievalConfig;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.store.PostVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合检索服务（v2 分块版）。
 *
 * 检索流程：
 * 1. 用户 query → embedding → 1024 维向量
 * 2. 知识库分块向量检索 top-K（HNSW + 余弦距离）
 * 3. 知识库分块关键词检索 top-K（pg_trgm + GIN）
 * 4. 帖子向量检索 top-K
 * 5. 知识库 chunk 按文章聚合（同 article_id 取最高分块，多命中加成）
 * 6. RRF 融合三路结果 → top-K
 * 7. 质量评分加权（知识库结果：finalScore = rrfScore * (0.8 + 0.2 * qualityScore)）
 * 8. 异步召回计数（fire-and-forget）
 *
 * 降级策略：embedding 失败 → 返回空列表，对话不中断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final PostVectorStore postVectorStore;
    private final KnowledgeMetricsConfig metricsConfig;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.retrieval.top-k:10}")
    private int topK;

    @Value("${app.retrieval.rerank-top-k:5}")
    private int rerankTopK;

    @Value("${app.retrieval.rrf-k:60}")
    private int rrfK;

    @Value("${app.retrieval.intent-driven.low-confidence-boost:3}")
    private int lowConfidenceBoost;

    @Value("${app.retrieval.intent-driven.default-token-budget:2500}")
    private int defaultTokenBudget;

    @Value("${app.retrieval.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.retrieval.cache.ttl:5m}")
    private Duration cacheTtl;

    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = ENCODING_REGISTRY.getEncodingForModel(ModelType.GPT_3_5_TURBO);

    /**
     * 检索与查询相关的知识库文档和帖子（旧签名，不感知意图）。
     * @param query 用户查询文本
     * @return 检索结果列表，按 RRF 分数降序
     * @deprecated 使用 {@link #retrieve(String, IntentResult)} 按意图驱动检索
     */
    @Deprecated
    public Mono<List<RetrievalResult>> retrieve(String query) {
        return retrieve(query, null);
    }

    /**
     * 意图驱动检索（ADR-024）。
     *
     * @param query 用户查询文本（建议传改写后的 rewrittenQuery）
     * @param intent 意图识别结果（可为 null，走默认配置）
     * @return 检索结果列表，按 RRF 分数降序
     */
    public Mono<List<RetrievalResult>> retrieve(String query, IntentResult intent) {
        return retrieve(query, intent, null);
    }

    /**
     * 意图驱动检索 + CLARIFY 上一轮上下文合并（ADR-024 + ADR-026）。
     *
     * 按意图/子意图/置信度选择检索来源配比和 topK，slots 作为帖子检索的 SQL WHERE 过滤。
     * 四路检索：知识向量 + 知识关键词 + 帖子向量（带 slots 过滤） + 帖子关键词（按配置启用）。
     * CLARIFY 意图时，上一轮检索结果降权 0.5 后作为第五路加入 RRF 融合。
     *
     * @param query 用户查询文本（建议传改写后的 rewrittenQuery）
     * @param intent 意图识别结果（可为 null，走默认配置）
     * @param previousResults 上一轮检索结果（CLARIFY 时使用，可为 null）
     * @return 检索结果列表，按 RRF 分数降序
     */
    public Mono<List<RetrievalResult>> retrieve(String query, IntentResult intent,
            List<RetrievalResult> previousResults) {
        if (query == null || query.isBlank()) {
            return Mono.just(Collections.emptyList());
        }

        RetrievalConfig config = selectConfig(intent);
        log.debug("Retrieval config: intent={}, subIntent={}, confidence={}, knowledgeTopK={}, postTopK={}, postKeywordTopK={}, usePostKeyword={}, slots={}",
                intent != null ? intent.getIntent() : "null",
                intent != null ? intent.getSubIntent() : "null",
                intent != null ? intent.getConfidence() : -1,
                config.knowledgeTopK(), config.postTopK(), config.postKeywordTopK(),
                config.usePostKeyword(), config.slots());

        // 缓存读取（非 CLARIFY 意图，ADR-029。CLARIFY 依赖上一轮上下文，不缓存）
        String cacheKey = (cacheEnabled && !isClarifyIntent(intent)) ? buildCacheKey(query, intent) : null;
        if (cacheKey != null) {
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    List<RetrievalResult> cachedResults = objectMapper.readValue(cached,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, RetrievalResult.class));
                    log.debug("Retrieval cache hit: key={}", cacheKey);
                    return Mono.just(cachedResults);
                }
            } catch (Exception e) {
                log.warn("Failed to read retrieval cache: {}", e.getMessage());
            }
        }

        return embeddingClient.embed(query)
                .map(queryVec -> {
                    List<List<RetrievalResult>> retrievalLists = new ArrayList<>();

                    // ① 知识库向量检索
                    try {
                        List<ChunkResult> kvChunks = knowledgeVectorStore.searchChunks(queryVec, config.knowledgeTopK());
                        List<RetrievalResult> kv = aggregateByArticle(kvChunks);
                        if (!kv.isEmpty()) retrievalLists.add(kv);
                    } catch (Exception e) {
                        log.warn("Knowledge vector search failed", e);
                    }

                    // ② 知识库关键词检索
                    try {
                        List<ChunkResult> kkChunks = knowledgeVectorStore.keywordSearchChunks(query, config.knowledgeKeywordTopK());
                        List<RetrievalResult> kk = aggregateByArticle(kkChunks);
                        if (!kk.isEmpty()) retrievalLists.add(kk);
                    } catch (Exception e) {
                        log.warn("Knowledge keyword search failed", e);
                    }

                    // ③ 帖子向量检索（带 slots 过滤，ADR-025）
                    try {
                        List<RetrievalResult> pv = postVectorStore.search(queryVec, config.postTopK(), config.slots());
                        if (!pv.isEmpty()) retrievalLists.add(pv);
                    } catch (Exception e) {
                        log.warn("Post vector search failed", e);
                    }

                    // ④ 帖子关键词检索（按配置启用）
                    if (config.usePostKeyword() && config.postKeywordTopK() > 0) {
                        try {
                            List<RetrievalResult> pk = postVectorStore.keywordSearch(query, config.postKeywordTopK(), config.slots());
                            if (!pk.isEmpty()) retrievalLists.add(pk);
                        } catch (Exception e) {
                            log.warn("Post keyword search failed", e);
                        }
                    }

                    // ⑤ CLARIFY: 合并上一轮检索结果（ADR-026，降权 0.5 后作为第五路加入 RRF 融合）
                    if (intent != null && intent.getIntent() == Intent.CLARIFY
                            && previousResults != null && !previousResults.isEmpty()) {
                        mergePreviousRetrieval(retrievalLists, previousResults);
                    }

                    List<RetrievalResult> fused = rrfFusion(retrievalLists, config.rerankTopK());
                    applyQualityWeight(fused);
                    crossSourceDedup(fused);
                    truncateByTokenBudget(fused, config.tokenBudget());
                    asyncIncrementRecall(fused);

                    // 缓存写入（异步 fire-and-forget，非 CLARIFY 时）
                    if (cacheKey != null && !fused.isEmpty()) {
                        asyncCacheResults(cacheKey, fused);
                    }

                    return fused;
                })
                .onErrorResume(e -> {
                    log.warn("Retrieval failed, degrading to empty context", e);
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * 按意图选择检索配置（ADR-024）。
     *
     * 配比原则：
     *  - HOW_TO 偏知识库（功能说明），不搜帖子关键词
     *  - SEARCH/resource 偏帖子（资源贴），启用帖子关键词
     *  - SEARCH/discussion 偏帖子（讨论帖），启用帖子关键词
     *  - SEARCH/content_qa 偏知识库（内容问答），启用帖子关键词
     *  - CLARIFY 均衡检索
     *  - 低置信度（<0.6）各路 topK + lowConfidenceBoost 扩大召回
     *
     * package-private for unit testing（{@link RetrievalServiceConfigTest}）。
     */
    RetrievalConfig selectConfig(IntentResult intent) {
        if (intent == null || intent.getIntent() == null) {
            return defaultConfig();
        }

        String subIntent = intent.getSubIntent() != null ? intent.getSubIntent() : "";
        boolean lowConfidence = intent.isLowConfidence();
        int confidenceBoost = lowConfidence ? lowConfidenceBoost : 0;

        return switch (intent.getIntent()) {
            case HOW_TO -> RetrievalConfig.builder()
                    .knowledgeTopK(8 + confidenceBoost)
                    .knowledgeKeywordTopK(5 + confidenceBoost)
                    .postTopK(2)
                    .postKeywordTopK(0)
                    .rerankTopK(rerankTopK)
                    .similarityThreshold(0.5)
                    .tokenBudget(defaultTokenBudget)
                    .usePostKeyword(false)
                    .slots(intent.getSlots())
                    .build();

            case SEARCH -> switch (subIntent) {
                case Intent.SubIntent.RESOURCE -> RetrievalConfig.builder()
                        .knowledgeTopK(2)
                        .knowledgeKeywordTopK(2)
                        .postTopK(8 + confidenceBoost)
                        .postKeywordTopK(5 + confidenceBoost)
                        .rerankTopK(rerankTopK)
                        .similarityThreshold(0.4)
                        .tokenBudget(defaultTokenBudget)
                        .usePostKeyword(true)
                        .slots(intent.getSlots())
                        .build();
                case Intent.SubIntent.DISCUSSION -> RetrievalConfig.builder()
                        .knowledgeTopK(2)
                        .knowledgeKeywordTopK(0)
                        .postTopK(8 + confidenceBoost)
                        .postKeywordTopK(5 + confidenceBoost)
                        .rerankTopK(rerankTopK)
                        .similarityThreshold(0.4)
                        .tokenBudget(defaultTokenBudget)
                        .usePostKeyword(true)
                        .slots(intent.getSlots())
                        .build();
                case Intent.SubIntent.CONTENT_QA -> RetrievalConfig.builder()
                        .knowledgeTopK(8 + confidenceBoost)
                        .knowledgeKeywordTopK(5 + confidenceBoost)
                        .postTopK(3)
                        .postKeywordTopK(2)
                        .rerankTopK(rerankTopK)
                        .similarityThreshold(0.5)
                        .tokenBudget(defaultTokenBudget)
                        .usePostKeyword(true)
                        .slots(intent.getSlots())
                        .build();
                default -> defaultConfig(intent.getSlots(), confidenceBoost);
            };

            case CLARIFY -> RetrievalConfig.builder()
                    .knowledgeTopK(5)
                    .knowledgeKeywordTopK(3)
                    .postTopK(5)
                    .postKeywordTopK(3)
                    .rerankTopK(rerankTopK)
                    .similarityThreshold(0.4)
                    .tokenBudget(defaultTokenBudget)
                    .usePostKeyword(true)
                    .slots(intent.getSlots())
                    .build();

            default -> defaultConfig(intent.getSlots(), confidenceBoost);
        };
    }

    private RetrievalConfig defaultConfig() {
        return defaultConfig(null, 0);
    }

    private RetrievalConfig defaultConfig(SlotResult slots, int confidenceBoost) {
        return RetrievalConfig.builder()
                .knowledgeTopK(5 + confidenceBoost)
                .knowledgeKeywordTopK(5 + confidenceBoost)
                .postTopK(5 + confidenceBoost)
                .postKeywordTopK(0)
                .rerankTopK(rerankTopK)
                .similarityThreshold(0.4)
                .tokenBudget(defaultTokenBudget)
                .usePostKeyword(false)
                .slots(slots)
                .build();
    }

    /**
     * 文章级聚合：同 article_id 的多个 chunk 取最高 similarity 的一个。
     * 多 chunk 命中时加成：finalSim = maxSim * (1 + 0.1 * (chunkCount - 1))。
     */
    private List<RetrievalResult> aggregateByArticle(List<ChunkResult> chunks) {
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ChunkResult> bestByArticle = new HashMap<>();
        Map<Long, Integer> hitCountByArticle = new HashMap<>();

        for (ChunkResult chunk : chunks) {
            Long articleId = chunk.articleId();
            ChunkResult existing = bestByArticle.get(articleId);
            if (existing == null || chunk.similarity() > existing.similarity()) {
                bestByArticle.put(articleId, chunk);
            }
            hitCountByArticle.merge(articleId, 1, Integer::sum);
        }

        List<RetrievalResult> aggregated = new ArrayList<>(bestByArticle.size());
        for (Map.Entry<Long, ChunkResult> entry : bestByArticle.entrySet()) {
            Long articleId = entry.getKey();
            ChunkResult best = entry.getValue();
            int hits = hitCountByArticle.get(articleId);
            double boostedSim = best.similarity() * (1 + 0.1 * (hits - 1));

            Map<String, Object> meta = new HashMap<>();
            meta.put("topic", best.topic());
            meta.put("chunkIndex", best.chunkIndex());
            meta.put("headingPath", best.headingPath());
            meta.put("qualityScore", best.qualityScore());
            meta.put("chunkHits", hits);

            aggregated.add(new RetrievalResult(
                    String.valueOf(articleId),
                    best.title(),
                    best.chunkContent(),
                    boostedSim,
                    RetrievalResult.Source.KNOWLEDGE,
                    meta
            ));
        }

        aggregated.sort((a, b) -> Double.compare(b.score(), a.score()));
        return aggregated;
    }

    /**
     * RRF 融合多路检索结果。
     *
     * 对每路检索结果，按排名计算 RRF 分数：score = Σ 1/(k + rank)，rank 从 1 开始。
     * 同一结果出现在多路检索中时，分数累加。
     */
    private List<RetrievalResult> rrfFusion(List<List<RetrievalResult>> retrievalLists, int finalTopK) {
        if (retrievalLists.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, RetrievalResult> idToResult = new HashMap<>();
        Map<String, Double> idToScore = new HashMap<>();

        for (List<RetrievalResult> list : retrievalLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RetrievalResult result = list.get(rank);
                String key = result.source() + ":" + result.id();
                idToResult.putIfAbsent(key, result);
                double rrfScore = 1.0 / (rrfK + rank + 1);
                idToScore.merge(key, rrfScore, Double::sum);
            }
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(idToScore.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<RetrievalResult> fused = new ArrayList<>(Math.min(finalTopK, sorted.size()));
        for (int i = 0; i < Math.min(finalTopK, sorted.size()); i++) {
            String key = sorted.get(i).getKey();
            double score = sorted.get(i).getValue();
            RetrievalResult original = idToResult.get(key);
            fused.add(new RetrievalResult(
                    original.id(),
                    original.title(),
                    original.content(),
                    score,
                    original.source(),
                    original.metadata()
            ));
        }

        return fused;
    }

    /**
     * 质量评分加权（仅对知识库结果）。
     * finalScore = rrfScore * (0.8 + 0.2 * qualityScore)
     * 帖子结果不加权（默认 qualityScore=1.0）。
     */
    private void applyQualityWeight(List<RetrievalResult> results) {
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            if (r.source() == RetrievalResult.Source.KNOWLEDGE) {
                double qualityScore = extractQualityScore(r);
                double weightedScore = r.score() * (0.8 + 0.2 * qualityScore);
                results.set(i, new RetrievalResult(
                        r.id(), r.title(), r.content(),
                        weightedScore, r.source(), r.metadata()
                ));
            }
        }
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
    }

    private double extractQualityScore(RetrievalResult r) {
        if (r.metadata() == null) return 0.5;
        Object qs = r.metadata().get("qualityScore");
        if (qs instanceof Number n) return n.doubleValue();
        return 0.5;
    }

    /**
     * token 预算截断（ADR-027）。
     *
     * 按 score 降序逐条累加 token，超出预算时截断。保证保留最相关的结果。
     * 每条结果的 token 数 = 标题 token + 内容 token + 50（metadata 开销估算）。
     *
     * 设计权衡：
     *  - 固定 rerankTopK 截断无法控制总 token（帖子 500 字 vs 知识库 256 token 差异大）
     *  - 按 token 预算截断能精确控制 prompt 长度，防止超出 context window
     */
    private void truncateByTokenBudget(List<RetrievalResult> results, int tokenBudget) {
        if (results.isEmpty()) return;

        int totalTokens = 0;
        int lastValidIndex = 0;

        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            String text = (r.title() != null ? r.title() : "") + " " + (r.content() != null ? r.content() : "");
            int tokens = ENCODING.countTokens(text) + 50;
            if (totalTokens + tokens > tokenBudget) {
                break;
            }
            totalTokens += tokens;
            lastValidIndex = i + 1;
        }

        if (lastValidIndex < results.size()) {
            log.debug("Token budget truncation: kept {}/{} results, ~{} tokens (budget={})",
                    lastValidIndex, results.size(), totalTokens, tokenBudget);
            results.subList(lastValidIndex, results.size()).clear();
        }
    }

    /**
     * 跨源去重（ADR-028）。
     *
     * 知识库和帖子可能存在主题相同的内容（如"如何发帖"指南 vs "发帖教程"帖子）。
     * RRF 融合时 key = source:id，不会跨源去重，导致 LLM 看到重复内容浪费 context。
     *
     * 策略：对跨源（KNOWLEDGE vs POST）的结果，计算标题 Jaccard 分词相似度，
     *       相似度 > 0.8 时移除分数较低的那条（保留高质量结果）。
     *
     * 设计权衡：
     *  - 用标题分词相似度而非内容 embedding 相似度（避免额外 embedding 计算，延迟低）
     *  - 阈值 0.8 较高，只去除明显重复（保留主题相关但有差异的内容）
     */
    private void crossSourceDedup(List<RetrievalResult> results) {
        if (results.size() < 2) return;

        List<RetrievalResult> deduped = new ArrayList<>();
        Set<Integer> removed = new HashSet<>();

        for (int i = 0; i < results.size(); i++) {
            if (removed.contains(i)) continue;
            RetrievalResult current = results.get(i);
            deduped.add(current);

            Set<String> currentTokens = tokenize(current.title());
            for (int j = i + 1; j < results.size(); j++) {
                if (removed.contains(j)) continue;
                RetrievalResult other = results.get(j);
                if (current.source() != other.source()) {
                    double titleSim = jaccardSimilarity(currentTokens, tokenize(other.title()));
                    if (titleSim > 0.8) {
                        removed.add(j);
                        log.debug("Cross-source dedup: removed '{}' (sim={} with '{}')",
                                other.title(), String.format("%.2f", titleSim), current.title());
                    }
                }
            }
        }

        if (deduped.size() < results.size()) {
            results.clear();
            results.addAll(deduped);
        }
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.split("[\\s,，。.!！？?、]+"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    /**
     * 异步召回计数（fire-and-forget）。
     * 对知识库结果累加 recall_count，不阻塞主流程。
     */
    private void asyncIncrementRecall(List<RetrievalResult> results) {
        Mono.fromRunnable(() -> {
                    for (RetrievalResult r : results) {
                        if (r.source() == RetrievalResult.Source.KNOWLEDGE) {
                            try {
                                Long articleId = Long.parseLong(r.id());
                                knowledgeVectorStore.incrementRecall(articleId);
                                metricsConfig.recordRecall();
                            } catch (NumberFormatException e) {
                                log.warn("Cannot parse articleId for recall increment: {}", r.id());
                            } catch (Exception e) {
                                log.warn("Failed to increment recall for articleId={}", r.id(), e);
                            }
                        }
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> {},
                        e -> log.warn("Async recall increment failed", e)
                );
    }

    /**
     * CLARIFY 意图时合并上一轮检索结果（ADR-026）。
     *
     * 上一轮结果不是本轮直接检索的，应降权。降权方式：score × 0.5。
     * 降权后作为第五路加入 retrievalLists，由 RRF 融合统一排序。
     *
     * 设计权衡：
     *  - 不能完全忽略上一轮（CLARIFY 需要上下文指代消解）
     *  - 不能直接用上一轮结果不检索（可能遗漏新信息）
     *  - 降权 0.5 既保留上下文相关性，又让本轮新检索的结果优先
     */
    private void mergePreviousRetrieval(List<List<RetrievalResult>> retrievalLists,
            List<RetrievalResult> previousResults) {
        List<RetrievalResult> downweighted = previousResults.stream()
                .map(r -> new RetrievalResult(r.id(), r.title(), r.content(),
                        r.score() * 0.5, r.source(), r.metadata()))
                .toList();
        if (!downweighted.isEmpty()) {
            retrievalLists.add(downweighted);
            log.debug("CLARIFY merged previous retrieval: {} results downweighted by 0.5",
                    downweighted.size());
        }
    }

    /**
     * 判断是否为 CLARIFY 意图（不缓存，因为依赖上一轮上下文）。
     */
    private boolean isClarifyIntent(IntentResult intent) {
        return intent != null && intent.getIntent() == Intent.CLARIFY;
    }

    /**
     * 生成缓存 key：agent:retrieval:{md5(query:intent:subIntent)}。
     *
     * key 包含 intent 和 subIntent，因为不同意图的检索策略不同，结果不同。
     * 不包含 previousResults（会话相关），所以 CLARIFY 不缓存（由 isClarifyIntent 过滤）。
     */
    private String buildCacheKey(String query, IntentResult intent) {
        String intentKey = intent != null
                ? intent.getIntent().name() + ":" + (intent.getSubIntent() != null ? intent.getSubIntent() : "null")
                : "null";
        String raw = query.trim().toLowerCase() + ":" + intentKey;
        String md5 = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        return "agent:retrieval:" + md5;
    }

    /**
     * 异步写入检索结果缓存（fire-and-forget，不阻塞主流程）。
     */
    private void asyncCacheResults(String cacheKey, List<RetrievalResult> results) {
        Mono.fromRunnable(() -> {
                    try {
                        String json = objectMapper.writeValueAsString(results);
                        redisTemplate.opsForValue().set(cacheKey, json, cacheTtl);
                        log.debug("Retrieval cache written: key={}, ttl={}", cacheKey, cacheTtl);
                    } catch (Exception e) {
                        log.warn("Failed to write retrieval cache: {}", e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> {},
                        e -> log.warn("Async cache write failed", e)
                );
    }
}
