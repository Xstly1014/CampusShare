package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.config.IntentMetricsConfig;
import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.RouteDecision;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.mapper.AgentSessionMapper;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.campushare.agent.prompt.ConstitutionalAIValidator;
import com.campushare.agent.prompt.PromptAssembler;
import com.campushare.agent.prompt.PromptVersionManager;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final DeepSeekClient deepSeekClient;
    private final AgentSessionMapper sessionMapper;
    private final AgentTurnMapper turnMapper;
    private final RetrievalService retrievalService;
    private final PromptAssembler promptAssembler;
    private final ConstitutionalAIValidator constitutionalAIValidator;
    private final RuleShortCircuitFilter ruleShortCircuitFilter;
    private final IntentClassifier intentClassifier;
    private final IntentRouter intentRouter;
    private final IntentMetricsConfig intentMetrics;
    private final PromptVersionManager promptVersionManager;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = ENCODING_REGISTRY.getEncodingForModel(ModelType.GPT_3_5_TURBO);

    private volatile Counter violationCounter;
    private volatile Counter injectionDetectedCounter;

    @Value("${app.agent.history-limit:10}")
    private int historyLimit;

    @Value("${app.llm.deepseek.model:deepseek-v4-flash}")
    private String modelName;

    @jakarta.annotation.PostConstruct
    void initCounters() {
        violationCounter = Counter.builder("agent.prompt.violation")
                .description("Number of Constitutional AI violations detected in LLM output")
                .register(meterRegistry);
        injectionDetectedCounter = Counter.builder("agent.prompt.injection.detected")
                .description("Number of injection patterns detected in user prompt (soft block)")
                .register(meterRegistry);
    }

    public Flux<ChatEvent> chat(String userId, ChatRequest request) {
        return Mono.fromCallable(() -> prepareContext(userId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> {
                    String sessionJson = buildSessionJson(ctx);
                    Flux<ChatEvent> sessionEvent = Flux.just(new ChatEvent("session", sessionJson));

                    // 快路径：模板回复（OUT_OF_SCOPE/NAVIGATE），不调 LLM
                    if (ctx.routeDecision() != null && ctx.routeDecision().isShortCircuit()) {
                        String templateReply = ctx.routeDecision().getTemplateReply();
                        Flux<ChatEvent> deltaFlux = Flux.just(new ChatEvent("delta", templateReply))
                                .doFinally(signal -> {
                                    long elapsed = System.currentTimeMillis() - ctx.startTime();
                                    Mono.fromRunnable(() ->
                                            completeShortCircuitTurn(ctx, templateReply, elapsed))
                                            .subscribeOn(Schedulers.boundedElastic()).subscribe();
                                });
                        return Flux.concat(sessionEvent, deltaFlux);
                    }

                    // 慢路径：RAG + LLM 流式
                    StringBuilder assistantContent = new StringBuilder();
                    AtomicReference<DeepSeekResponse.Usage> usageRef = new AtomicReference<>();

                    Flux<ChatEvent> deltaStream = deepSeekClient.chatCompletionStream(ctx.messages())
                            .doOnNext(chunk -> {
                                if (chunk.content() != null) {
                                    assistantContent.append(chunk.content());
                                }
                                if (chunk.usage() != null) {
                                    usageRef.set(chunk.usage());
                                }
                            })
                            .filter(chunk -> chunk.content() != null)
                            .map(chunk -> new ChatEvent("delta", chunk.content()))
                            .doFinally(signalType -> {
                                long elapsed = System.currentTimeMillis() - ctx.startTime();
                                String content = assistantContent.toString();
                                Mono.fromRunnable(() -> {
                                    if (signalType == SignalType.ON_COMPLETE) {
                                        completeTurn(ctx.turn(), ctx.session(), content, elapsed,
                                                usageRef.get(), ctx.promptTokens(), ctx.retrievalContext(),
                                                ctx.intentResult());
                                    } else if (signalType == SignalType.ON_ERROR) {
                                        errorTurn(ctx.turn(), "Stream terminated with error");
                                    }
                                }).subscribeOn(Schedulers.boundedElastic()).subscribe();
                            });

                    return Flux.concat(sessionEvent, deltaStream);
                });
    }

    private String buildSessionJson(ChatContext ctx) {
        try {
            Map<String, String> sessionPayload = new HashMap<>();
            sessionPayload.put("sessionId", ctx.session().getId());
            return objectMapper.writeValueAsString(sessionPayload);
        } catch (JsonProcessingException e) {
            return "{\"sessionId\":\"" + ctx.session().getId() + "\"}";
        }
    }

    private ChatContext prepareContext(String userId, ChatRequest request) {
        AgentSession session = getOrCreateSession(userId, request);

        String userMessage = request.getMessage();

        // ① 意图识别（三层漏斗：规则 → LLM → Embedding → SEARCH 兜底）
        IntentResult intentResult = recognizeIntent(userMessage, session.getId());

        // ② 预生成注入检测（硬拦截 Prompt 泄露；软拦截其他注入仅 log + meter）
        if (constitutionalAIValidator.shouldHardBlock(userMessage)) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_FORBIDDEN, "该请求包含不允许的内容");
        }
        if (constitutionalAIValidator.detectInjection(userMessage)) {
            injectionDetectedCounter.increment();
        }

        // ③ 意图路由：尝试快路径
        Optional<RouteDecision> shortCircuit = intentRouter.tryShortCircuit(intentResult);
        if (shortCircuit.isPresent()) {
            intentMetrics.recordRoute(true, intentResult.getIntent().name());
            AgentTurn turn = createTurn(session, userMessage);
            return new ChatContext(session, null, turn, System.currentTimeMillis(),
                    0, null, intentResult, shortCircuit.get());
        }

        // ④ HOW_TO/SEARCH/CLARIFY → 走 RAG 管线（用改写后的 query 检索）
        intentMetrics.recordRoute(false, intentResult.getIntent().name());
        String retrieveQuery = intentResult.getRewrittenQuery() != null
                ? intentResult.getRewrittenQuery() : userMessage;
        List<RetrievalResult> retrievalResults = retrievalService.retrieve(retrieveQuery).block();
        String retrievalContextJson = formatRetrievalContextJson(retrievalResults);

        PromptVersion promptVersion = promptVersionManager.getCurrentVersion(userId);
        String systemPrompt = promptAssembler.assemble(intentResult.getIntent(), retrievalResults, promptVersion);

        List<DeepSeekRequest.Message> messages = buildMessages(session, userMessage, systemPrompt);
        int promptTokens = countPromptTokens(messages);
        AgentTurn turn = createTurn(session, userMessage);
        return new ChatContext(session, messages, turn, System.currentTimeMillis(),
                promptTokens, retrievalContextJson, intentResult, null);
    }

    /**
     * 意图识别主入口（三层漏斗）。
     *
     * Layer 1: 规则短路（RuleShortCircuitFilter）
     * Layer 2: LLM 分类（IntentClassifier）
     * Layer 3: Embedding 兜底（EmbeddingIntentFallback，由 IntentClassifier 内部调用）
     * Default: SEARCH 兜底（全部失败时）
     */
    private IntentResult recognizeIntent(String query, String sessionId) {
        // Layer 1: 规则短路
        Optional<IntentResult> ruleResult = ruleShortCircuitFilter.filter(query);
        if (ruleResult.isPresent()) {
            IntentResult r = ruleResult.get();
            intentMetrics.recordClassification(r.getIntent(), r.getSubIntent(), r.getClassifyLayer(), "SUCCESS");
            return r;
        }
        // Layer 2/3: LLM 分类 + Embedding 兜底 + Default SEARCH
        IntentResult result = intentClassifier.classify(query, sessionId)
                .defaultIfEmpty(buildDefaultSearchIntent(query))
                .block();
        intentMetrics.recordClassification(result.getIntent(), result.getSubIntent(),
                result.getClassifyLayer(), "SUCCESS");
        return result;
    }

    private IntentResult buildDefaultSearchIntent(String query) {
        return IntentResult.builder()
                .intent(Intent.SEARCH)
                .subIntent(Intent.SubIntent.RESOURCE)
                .confidence(0.0)
                .rewrittenQuery(query != null ? query : "")
                .classifyLayer("DEFAULT")
                .build();
    }

    private int countPromptTokens(List<DeepSeekRequest.Message> messages) {
        int total = 0;
        for (DeepSeekRequest.Message msg : messages) {
            if (msg.getContent() != null) {
                total += ENCODING.countTokens(msg.getContent());
            }
            total += 4;
        }
        return total;
    }

    private AgentSession getOrCreateSession(String userId, ChatRequest request) {
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            AgentSession session = sessionMapper.selectById(request.getSessionId());
            if (session == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
            }
            if (!session.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.USER_ACCOUNT_FORBIDDEN, "无权访问此会话");
            }
            return session;
        }

        String title = request.getTitle();
        if (title == null || title.isEmpty()) {
            String msg = request.getMessage();
            title = msg.length() > 50 ? msg.substring(0, 50) + "..." : msg;
        }

        AgentSession session = AgentSession.builder()
                .userId(userId)
                .title(title)
                .status("ACTIVE")
                .messageCount(0)
                .totalTokens(0)
                .totalCost(BigDecimal.ZERO)
                .lastMessageAt(LocalDateTime.now())
                .build();
        sessionMapper.insert(session);
        return session;
    }

    private List<DeepSeekRequest.Message> buildMessages(AgentSession session, String currentMessage,
            String systemPrompt) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(systemPrompt)
                .build());

        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, session.getId())
                .eq(AgentTurn::getStatus, "COMPLETED")
                .orderByDesc(AgentTurn::getTurnNumber)
                .last("LIMIT " + historyLimit);
        List<AgentTurn> history = turnMapper.selectList(wrapper);
        Collections.reverse(history);

        for (AgentTurn t : history) {
            messages.add(DeepSeekRequest.Message.builder()
                    .role("user")
                    .content(t.getUserMessage())
                    .build());
            messages.add(DeepSeekRequest.Message.builder()
                    .role("assistant")
                    .content(t.getAssistantMessage())
                    .build());
        }

        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(currentMessage)
                .build());

        return messages;
    }

    private AgentTurn createTurn(AgentSession session, String userMessage) {
        int turnNumber = (session.getMessageCount() != null ? session.getMessageCount() : 0) + 1;
        AgentTurn turn = AgentTurn.builder()
                .sessionId(session.getId())
                .turnNumber(turnNumber)
                .userMessage(userMessage)
                .messageRole("user")
                .status("STREAMING")
                .modelName(modelName)
                .build();
        turnMapper.insert(turn);
        return turn;
    }

    private void completeTurn(AgentTurn turn, AgentSession session, String content, long elapsedMs,
            DeepSeekResponse.Usage usage, int promptTokens, String retrievalContextJson,
            IntentResult intentResult) {
        try {
            int completionTokens;
            int totalTokens;

            if (usage != null && usage.getTotalTokens() != null) {
                totalTokens = usage.getTotalTokens();
                completionTokens = usage.getCompletionTokens() != null
                        ? usage.getCompletionTokens()
                        : ENCODING.countTokens(content);
            } else {
                completionTokens = ENCODING.countTokens(content);
                totalTokens = promptTokens + completionTokens;
            }

            // 输出后 Constitutional AI 验证（ADR-SP-03）
            // 流式场景用户已看到内容，不替换；仅 log + meter + 写入 tools_used 字段记录违规
            String violation = constitutionalAIValidator.validate(content);
            if (violation != null) {
                violationCounter.increment();
                log.warn("Constitutional AI violation detected: turnId={}, violation={}", turn.getId(), violation);
            }

            // 合并 violation + intent 到 tools_used（复用字段，不新增 DB 列）
            turn.setToolsUsed(buildToolsUsedJson(violation, intentResult));

            turn.setAssistantMessage(content);
            turn.setStatus("COMPLETED");
            turn.setResponseTimeMs((int) elapsedMs);
            turn.setTokensUsed(totalTokens);
            turn.setRetrievalContext(retrievalContextJson);
            turnMapper.updateById(turn);

            session.setMessageCount(turn.getTurnNumber());
            session.setTotalTokens((session.getTotalTokens() != null ? session.getTotalTokens() : 0) + totalTokens);
            session.setLastMessageAt(LocalDateTime.now());
            sessionMapper.updateById(session);

            log.info(
                    "Turn completed: sessionId={}, turn={}, promptTokens={}, completionTokens={}, totalTokens={}, elapsedMs={}, violation={}",
                    session.getId(), turn.getTurnNumber(), promptTokens, completionTokens, totalTokens, elapsedMs,
                    violation);
        } catch (Exception e) {
            log.error("Failed to complete turn {}", turn.getId(), e);
        }
    }

    /**
     * 快路径 turn 完成（模板回复，不调 LLM）。
     */
    private void completeShortCircuitTurn(ChatContext ctx, String templateReply, long elapsedMs) {
        try {
            String intentJson = objectMapper.writeValueAsString(ctx.intentResult());
            AgentTurn turn = ctx.turn();
            AgentSession session = ctx.session();

            turn.setAssistantMessage(templateReply);
            turn.setStatus("COMPLETED");
            turn.setResponseTimeMs((int) elapsedMs);
            turn.setTokensUsed(0);
            turn.setToolsUsed(intentJson);
            turnMapper.updateById(turn);

            session.setMessageCount(turn.getTurnNumber());
            session.setLastMessageAt(LocalDateTime.now());
            sessionMapper.updateById(session);

            log.info("Short-circuit turn completed: sessionId={}, turn={}, elapsedMs={}, intent={}",
                    session.getId(), turn.getTurnNumber(), elapsedMs,
                    ctx.intentResult() != null ? ctx.intentResult().getIntent() : "null");
        } catch (Exception e) {
            log.error("Failed to complete short-circuit turn {}", ctx.turn().getId(), e);
        }
    }

    /**
     * 构建 tools_used JSON（合并 violation + intent）。
     */
    private String buildToolsUsedJson(String violation, IntentResult intentResult) {
        try {
            Map<String, Object> tools = new HashMap<>();
            if (violation != null) {
                tools.put("violation", violation);
            }
            if (intentResult != null) {
                tools.put("intent", intentResult);
            }
            if (tools.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(tools);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tools_used", e);
            if (violation != null) {
                return "{\"violation\":\"" + violation.replace("\"", "'") + "\"}";
            }
            return null;
        }
    }

    private void errorTurn(AgentTurn turn, String errorMessage) {
        try {
            turn.setStatus("ERROR");
            turn.setErrorMessage(errorMessage);
            turnMapper.updateById(turn);
        } catch (Exception e) {
            log.error("Failed to update error turn {}", turn.getId(), e);
        }
    }

    public record ChatEvent(String type, String data) {
    }

    private record ChatContext(AgentSession session, List<DeepSeekRequest.Message> messages,
            AgentTurn turn, long startTime, int promptTokens, String retrievalContext,
            IntentResult intentResult, RouteDecision routeDecision) {
    }

    private String formatRetrievalContextJson(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        try {
            List<Map<String, Object>> jsonList = new ArrayList<>();
            for (RetrievalResult r : results) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.id());
                item.put("title", r.title());
                item.put("source", r.source().name());
                item.put("score", r.score());
                jsonList.add(item);
            }
            return objectMapper.writeValueAsString(jsonList);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize retrieval context", e);
            return null;
        }
    }
}
