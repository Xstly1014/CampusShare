package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.EmbeddingClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 兜底层（Layer 3）。
 *
 * LLM 熔断/超时/失败时兜底，用向量相似度匹配意图，保证可用性。
 * 准确率 ~82%，总比失败好。
 *
 * 流程：
 *  1. @PostConstruct 预计算 5 个意图描述向量（异步，不阻塞启动）
 *  2. classify(query): embed(query) → 与 5 个意图向量算余弦相似度 → 返回最相似意图
 *  3. Embedding 失败/未初始化 → 返回 SEARCH 默认
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingIntentFallback {

    private final EmbeddingClient embeddingClient;

    /** 5 大意图的描述文本（用于 embedding 相似度匹配） */
    private static final Map<Intent, String> INTENT_DESCRIPTIONS = Map.of(
            Intent.HOW_TO, "用户询问怎么使用功能、如何操作、在哪设置、教程、步骤指引",
            Intent.SEARCH, "用户想找资料、求资源、找帖子、有没有、求一份、找一下",
            Intent.NAVIGATE, "用户想知道某个功能在哪、入口在哪、怎么找自己的内容、我点赞的、我收藏的",
            Intent.CLARIFY, "用户使用指代词、追问、修正上一轮的问题、那个、它、刚才那个",
            Intent.OUT_OF_SCOPE, "闲聊、开放域问题、写操作请求、敏感话题、你好、你是谁"
    );

    /** 预计算的意图描述向量 */
    private final Map<Intent, float[]> intentVectors = new ConcurrentHashMap<>();

    /** 预加载是否完成（volatile 保证可见性） */
    private volatile boolean initialized = false;

    @PostConstruct
    void preloadIntentVectors() {
        // 异步预计算，不阻塞 Spring 启动
        Flux.fromIterable(INTENT_DESCRIPTIONS.entrySet())
                .flatMap(e -> embeddingClient.embed(e.getValue())
                        .flatMap(vec -> {
                            if (vec == null || vec.length == 0) {
                                log.warn("Failed to preload intent vector for {}: empty embedding", e.getKey());
                                return Mono.empty();
                            }
                            intentVectors.put(e.getKey(), vec);
                            return Mono.just(e.getKey());
                        })
                        .onErrorResume(err -> {
                            log.warn("Failed to preload intent vector for {}: {}",
                                    e.getKey(), err.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .subscribe(
                        v -> {
                            initialized = !intentVectors.isEmpty();
                            log.info("Intent vectors preloaded: {}/{} succeeded, initialized={}",
                                    intentVectors.size(), INTENT_DESCRIPTIONS.size(), initialized);
                        },
                        err -> log.warn("Intent vector preload failed entirely, will use SEARCH fallback", err)
                );
    }

    /**
     * Embedding 兜底分类。
     *
     * @param query 用户原始查询
     * @return Mono<IntentResult> 最相似意图，或 SEARCH 兜底
     */
    public Mono<IntentResult> classify(String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(buildDefaultSearch(query));
        }

        if (!initialized || intentVectors.isEmpty()) {
            log.debug("Embedding fallback not initialized, returning SEARCH default");
            return Mono.just(buildDefaultSearch(query));
        }

        return embeddingClient.embed(query)
                .map(qVec -> findBestMatch(query, qVec))
                .defaultIfEmpty(buildDefaultSearch(query))
                .onErrorResume(e -> {
                    log.warn("Embedding fallback failed, returning SEARCH default: {}", e.getMessage());
                    return Mono.just(buildDefaultSearch(query));
                });
    }

    /**
     * 在 5 个意图向量中找余弦相似度最高的。
     */
    private IntentResult findBestMatch(String query, float[] queryVector) {
        if (queryVector == null || queryVector.length == 0) {
            return buildDefaultSearch(query);
        }

        Intent bestIntent = Intent.SEARCH;
        double bestScore = -1.0;

        for (Map.Entry<Intent, float[]> entry : intentVectors.entrySet()) {
            double score = cosineSimilarity(queryVector, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestIntent = entry.getKey();
            }
        }

        // 余弦相似度范围 [-1, 1]，归一化到 [0, 1] 作为 confidence
        double confidence = Math.max(0.0, bestScore);

        log.info("Embedding fallback: query='{}' → {} (cosine={}, conf={})",
                query, bestIntent, bestScore, confidence);

        return IntentResult.builder()
                .intent(bestIntent)
                .subIntent(defaultSubIntent(bestIntent))
                .confidence(confidence)
                .rewrittenQuery(query)
                .classifyLayer("EMBEDDING")
                .build();
    }

    /**
     * 余弦相似度：a·b / (|a|·|b|)。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 按意图返回默认子意图。
     */
    private String defaultSubIntent(Intent intent) {
        return switch (intent) {
            case HOW_TO -> Intent.SubIntent.FEATURE_HELP;
            case SEARCH -> Intent.SubIntent.RESOURCE;
            case NAVIGATE -> Intent.SubIntent.SECTION_LOC;
            case CLARIFY -> Intent.SubIntent.COREFERENCE;
            case OUT_OF_SCOPE -> Intent.SubIntent.OPEN_DOMAIN;
        };
    }

    /**
     * 构建 SEARCH 兜底意图（Embedding 失败时）。
     */
    private IntentResult buildDefaultSearch(String query) {
        return IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.0)
                .rewrittenQuery(query != null ? query : "")
                .classifyLayer("EMBEDDING")
                .build();
    }
}
