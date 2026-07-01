package com.campushare.agent.service;

import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.store.PostVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索服务：向量检索 + pg_trgm 关键词检索 + RRF 融合。
 *
 * 检索流程：
 * 1. 用户 query → embedding → 1024 维向量
 * 2. 知识库向量检索 top-10（HNSW + 余弦距离）
 * 3. 知识库关键词检索 top-10（pg_trgm + GIN）
 * 4. 帖子向量检索 top-10
 * 5. RRF 融合三路结果 → top-5
 *
 * RRF 公式：score = Σ 1/(k + rank)，k=60，rank 从 1 开始
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
                        List<RetrievalResult> kv = knowledgeVectorStore.search(queryVec, topK);
                        if (!kv.isEmpty()) retrievalLists.add(kv);
                    } catch (Exception e) {
                        log.warn("Knowledge vector search failed", e);
                    }

                    try {
                        List<RetrievalResult> kk = knowledgeVectorStore.keywordSearch(query, topK);
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

                    return rrfFusion(retrievalLists, rerankTopK);
                })
                .onErrorResume(e -> {
                    log.warn("Retrieval failed, degrading to empty context", e);
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * RRF 融合多路检索结果。
     *
     * 对每路检索结果，按排名计算 RRF 分数：score = Σ 1/(k + rank)，rank 从 1 开始。
     * 同一结果出现在多路检索中时，分数累加。
     *
     * @param retrievalLists 多路检索结果列表
     * @param finalTopK 最终返回的数量
     * @return RRF 融合后的 top-K 结果
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
}
