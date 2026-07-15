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
import java.util.regex.Pattern;

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

    private static final Duration CLASSIFY_TIMEOUT = Duration.ofSeconds(3);
    private static final double CONFIDENCE_THRESHOLD = 0.6;
    private static final double LLM_TEMPERATURE = 0.0;
    private static final int LLM_MAX_TOKENS = 200;

    /**
     * 资源请求关键词：用户表达想获取学习资料/教程/资源时，即使 LLM 误判为 OUT_OF_SCOPE/open_domain
     * 也应降级为 SEARCH/resource。覆盖 "我想学python" / "求Python教程" / "找C++资料" 等。
     */
    private static final Pattern RESOURCE_REQUEST_PATTERN = Pattern.compile(
            "(想学|想求|求|找|有没有|查).*(资料|教程|笔记|笔记|卷子|试卷|答案|教材|课件|课程|题库|面试题|面经|代码|项目|实验报告|ppt|pdf|word|电子书|书籍|书|课|学习|python|java|c\\+\\+|c语言|js|javascript|前端|后端|算法|数据结构|操作系统|数据库|网络|高数|线代|概率|英语|四六级|考研|期末|期中|复试)"
    );

    public IntentClassifier(DeepSeekClient deepSeekClient,
                            IntentCacheService cacheService,
                            EmbeddingIntentFallback embeddingFallback,
                            ObjectMapper objectMapper,
                            @Qualifier("intentClassifierCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.deepSeekClient = deepSeekClient;
        this.cacheService = cacheService;
        this.embeddingFallback = embeddingFallback;
        this.objectMapper = objectMapper;
        this.intentClassifierCircuitBreaker = circuitBreaker;
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
                .doOnNext(cached -> log.debug("Intent cache hit for query='{}'", query))
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
                    // Guard：学习资源请求不应被识别为 OUT_OF_SCOPE/open_domain
                    if (result.getIntent() == Intent.OUT_OF_SCOPE
                            && Intent.SubIntent.OPEN_DOMAIN.equals(result.getSubIntent())
                            && looksLikeResourceRequest(query)) {
                        log.warn("LLM misclassified resource query as OUT_OF_SCOPE/open_domain: '{}'. Downgrade to SEARCH/resource",
                                query);
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

    /**
     * 判断查询是否为资源请求（学习资料/教程/资源等）。
     */
    private boolean looksLikeResourceRequest(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return RESOURCE_REQUEST_PATTERN.matcher(query.trim().toLowerCase()).find();
    }
}
