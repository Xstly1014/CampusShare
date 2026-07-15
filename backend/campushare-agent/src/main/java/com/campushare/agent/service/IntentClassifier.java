package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.prompt.PromptConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * LLM 意图分类器（Layer 2，核心）。
 *
 * 调用 DeepSeek 非流式 API 输出结构化 JSON，同时完成：
 *  - 意图分类（5 大 + 14 子）
 *  - 查询改写（rewritten_query）
 *  - 槽位抽取（school/category/postType/sort）
 *
 * ADR-011：三合一合并为一次 LLM 调用，省 ~500ms 延迟和 1 次 API 成本。
 *
 * 降级链：
 *  1. 查 Redis 缓存（命中跳过 LLM）
 *  2. 调 LLM 分类（temperature=0, max_tokens=200, 3s 超时）
 *  3. LLM 失败/熔断 → Embedding 兜底（Layer 3）
 *  4. 解析失败/低置信度 → 兜底 SEARCH（ADR-010）
 */
@Service
@Slf4j
public class IntentClassifier {

    private final DeepSeekClient deepSeekClient;
    private final IntentCacheService cacheService;
    private final EmbeddingIntentFallback embeddingFallback;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker intentClassifierCircuitBreaker;
    private final IntentPolicyService policyService;

    private static final Duration CLASSIFY_TIMEOUT = Duration.ofSeconds(3);
    private static final double CONFIDENCE_THRESHOLD = 0.6;
    private static final double LLM_TEMPERATURE = 0.0;
    private static final int LLM_MAX_TOKENS = 200;

    public IntentClassifier(DeepSeekClient deepSeekClient,
                            IntentCacheService cacheService,
                            EmbeddingIntentFallback embeddingFallback,
                            ObjectMapper objectMapper,
                            @Qualifier("intentClassifierCircuitBreaker") CircuitBreaker circuitBreaker,
                            IntentPolicyService policyService) {
        this.deepSeekClient = deepSeekClient;
        this.cacheService = cacheService;
        this.embeddingFallback = embeddingFallback;
        this.objectMapper = objectMapper;
        this.intentClassifierCircuitBreaker = circuitBreaker;
        this.policyService = policyService;
    }

    /**
     * 意图分类主入口。
     *
     * @param query     用户原始查询
     * @param sessionId 会话 ID（用于多轮上下文，MVP 阶段未使用）
     * @return Mono<IntentResult> 分类结果
     */
    public Mono<IntentResult> classify(String query, String sessionId) {
        if (query == null || query.isBlank()) {
            return Mono.just(buildDefaultSearchIntent(query));
        }

        // 1. 查 Redis 缓存
        return cacheService.get(query)
                .cast(IntentResult.class)
                .map(cached -> {
                    log.debug("Intent cache hit for query='{}'", query);
                    return policyService.applyPolicy(cached, query, sessionId);
                })
                .switchIfEmpty(Mono.defer(() ->
                        // 2. 缓存未命中，调用 LLM 分类
                        classifyByLLM(query)
                                .timeout(CLASSIFY_TIMEOUT)
                                .transform(CircuitBreakerOperator.of(intentClassifierCircuitBreaker))
                                .onErrorResume(e -> {
                                    // 3. LLM 失败 → Embedding 兜底
                                    log.warn("LLM classify failed, fallback to embedding: {}", e.getMessage());
                                    return embeddingFallback.classify(query);
                                })
                                .map(result -> policyService.applyPolicy(result, query, sessionId))
                                .doOnNext(result -> {
                                    // 4. 写入缓存（异步，不阻塞主流程）
                                    cacheService.put(query, result).subscribe();
                                })
                ));
    }

    /**
     * LLM 分类：调用 DeepSeek 输出 JSON。
     */
    private Mono<IntentResult> classifyByLLM(String query) {
        String prompt = PromptConstants.buildIntentClassificationPrompt(query);

        DeepSeekRequest.Message systemMessage = DeepSeekRequest.Message.builder()
                .role("system")
                .content(prompt)
                .build();
        DeepSeekRequest.Message userMessage = DeepSeekRequest.Message.builder()
                .role("user")
                .content(query)
                .build();

        return deepSeekClient.chatCompletion(List.of(systemMessage, userMessage),
                        LLM_TEMPERATURE, LLM_MAX_TOKENS)
                .map(this::extractContent)
                .map(content -> parseIntentResult(query, content))
                .doOnNext(result -> {
                    // ADR-010：低置信度兜底为 SEARCH
                    if (result.isLowConfidence()) {
                        log.info("Low confidence ({}), fallback to SEARCH for query='{}'",
                                result.getConfidence(), query);
                        result.setIntent(Intent.SEARCH);
                        result.setSubIntent(Intent.SubIntent.RESOURCE);
                    }
                    log.info("Intent classified: query='{}' → {} / {} (conf={}, layer=LLM)",
                            query, result.getIntent(), result.getSubIntent(),
                            result.getConfidence());
                });
    }

    /**
     * 从 DeepSeek 响应中提取文本内容。
     */
    private String extractContent(DeepSeekResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new RuntimeException("DeepSeek response has no choices");
        }
        DeepSeekResponse.Message message = response.getChoices().get(0).getMessage();
        if (message == null || message.getContent() == null) {
            throw new RuntimeException("DeepSeek response message is null");
        }
        return message.getContent();
    }

    /**
     * 解析 LLM 返回的 JSON 为 IntentResult。
     *
     * 解析失败时兜底为 SEARCH（ADR-010）。
     */
    private IntentResult parseIntentResult(String query, String llmResponse) {
        try {
            // 清理可能的 Markdown 代码块标记
            String json = llmResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            Intent intent = Intent.valueOf(root.path("intent").asText("SEARCH"));
            String subIntent = root.path("sub_intent").asText(Intent.SubIntent.RESOURCE);
            double confidence = root.path("confidence").asDouble(0.5);
            String rewrittenQuery = root.path("rewritten_query").asText(query);

            // 解析槽位
            IntentResult.SlotResult slots = null;
            JsonNode slotsNode = root.path("slots");
            if (!slotsNode.isMissingNode() && !slotsNode.isNull()) {
                slots = IntentResult.SlotResult.builder()
                        .school(slotsNode.path("school").asText(null))
                        .category(slotsNode.path("category").asText(null))
                        .postType(slotsNode.path("post_type").asText(null))
                        .sort(slotsNode.path("sort").asText(null))
                        .build();
            }

            return IntentResult.builder()
                    .intent(intent)
                    .subIntent(subIntent)
                    .confidence(confidence)
                    .rewrittenQuery(rewrittenQuery)
                    .slots(slots)
                    .hydeDoc(root.path("hyde_doc").asText(null))
                    .classifyLayer("LLM")
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse intent JSON: {}", llmResponse, e);
            return buildDefaultSearchIntent(query);
        }
    }

    /**
     * 构建默认 SEARCH 兜底意图（Layer Default）。
     */
    private IntentResult buildDefaultSearchIntent(String query) {
        return IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.0)
                .rewrittenQuery(query != null ? query : "")
                .classifyLayer("DEFAULT")
                .build();
    }

}
