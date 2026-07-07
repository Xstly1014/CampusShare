package com.campushare.agent.service;

import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.ChunkResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.store.PostVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Value("${app.retrieval.top-k:10}")
    private int topK;

    @Value("${app.retrieval.rerank-top-k:5}")
    private int rerankTopK;

    @Value("${app.retrieval.rrf-k:60}")
    private int rrfK;

    /**
     * 检索与查询相关的知识库文档和帖子。
     * @param query 用户查询文本
     * @return 检索结果列表，按 RRF 分数降序
     */
    public Mono<List<RetrievalResult>> retrieve(String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(Collections.emptyList());
        }

        return embeddingClient.embed(query)
                .map(queryVec -> {
                    List<List<RetrievalResult>> retrievalLists = new ArrayList<>();

                    try {
                        List<ChunkResult> kvChunks = knowledgeVectorStore.searchChunks(queryVec, topK);
                        List<RetrievalResult> kv = aggregateByArticle(kvChunks);
                        if (!kv.isEmpty()) retrievalLists.add(kv);
                    } catch (Exception e) {
                        log.warn("Knowledge vector search failed", e);
                    }

                    try {
                        List<ChunkResult> kkChunks = knowledgeVectorStore.keywordSearchChunks(query, topK);
                        List<RetrievalResult> kk = aggregateByArticle(kkChunks);
                        if (!kk.isEmpty()) retrievalLists.add(kk);
                    } catch (Exception e) {
                        log.warn("Knowledge keyword search failed", e);
                    }

                    try {
                        List<RetrievalResult> pv = postVectorStore.search(queryVec, topK);
                        if (!pv.isEmpty()) retrievalLists.add(pv);
                    } catch (Exception e) {
                        log.warn("Post vector search failed", e);
                    }

                    List<RetrievalResult> fused = rrfFusion(retrievalLists, rerankTopK);
                    applyQualityWeight(fused);
                    asyncIncrementRecall(fused);
                    return fused;
                })
                .onErrorResume(e -> {
                    log.warn("Retrieval failed, degrading to empty context", e);
                    return Mono.just(Collections.emptyList());
                });
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
}
