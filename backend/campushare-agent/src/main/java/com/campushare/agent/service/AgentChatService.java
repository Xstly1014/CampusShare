package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.config.IntentMetricsConfig;
import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.MemoryMessage;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.RouteDecision;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.enums.SessionStatus;
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
import org.slf4j.MDC;
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
import java.util.UUID;
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
    private final ContextAssembler contextAssembler;
    private final ContextSnapshotService contextSnapshotService;
    private final ConversationMemoryService conversationMemoryService;
    private final ContextCompressionService contextCompressionService;
    private final LongTermMemoryService longTermMemoryService;
    private final SessionStateMachine sessionStateMachine;
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
                                                usageRef.get(), ctx.inputTokens(), ctx.retrievalContext(),
                                                ctx.intentResult(), ctx.promptVersion());
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
        // MDC 链路追踪：traceId 贯穿意图识别→检索→prompt 装配日志（ADR-030）
        MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        try {
        AgentSession session = getOrCreateSession(userId, request);

        // 状态转移：INIT → ACTIVE（首条消息时触发，ADR-064）
        SessionStatus currentStatus = sessionStateMachine.getCurrentStatus(session.getId());
        if (currentStatus == SessionStatus.INIT) {
            sessionStateMachine.transition(session.getId(), SessionStatus.INIT, SessionStatus.ACTIVE, "First message");
        }

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
            AgentTurn turn = createTurn(session, userMessage, intentResult);
            return new ChatContext(session, null, turn, System.currentTimeMillis(),
                    0, null, intentResult, shortCircuit.get(), null);
        }

        // ④ HOW_TO/SEARCH/CLARIFY → 走 RAG 管线（用改写后的 query 检索，意图驱动检索策略 ADR-024）
        intentMetrics.recordRoute(false, intentResult.getIntent().name());
        String retrieveQuery = intentResult.getRewrittenQuery() != null
                ? intentResult.getRewrittenQuery() : userMessage;

        // CLARIFY 意图时加载上一轮检索结果，用于上下文合并（ADR-026）
        List<RetrievalResult> previousResults = null;
        if (intentResult.getIntent() == Intent.CLARIFY) {
            previousResults = loadPreviousRetrieval(session.getId());
        }

        List<RetrievalResult> retrievalResults = retrievalService.retrieve(
                retrieveQuery, intentResult, previousResults).block();
        String retrievalContextJson = formatRetrievalContextJson(retrievalResults);

        PromptVersion promptVersion = promptVersionManager.getCurrentVersion(userId);
        String systemPrompt = promptAssembler.assemble(intentResult.getIntent(), retrievalResults, promptVersion);

        // L4 对话历史（优先从 Redis 短期记忆加载，含滚动摘要/槽位/Pin 前缀；Redis 为空时降级到 DB）
        List<AgentTurn> history = loadHistoryWithMemory(session);

        // L1 用户画像（长期记忆，ADR-059，按意图/槽位匹配 Top-K 记忆）
        Map<String, String> slots = conversationMemoryService.loadSlots(session.getId());
        String userProfile = longTermMemoryService.loadUserProfile(userId, intentResult, slots);

        // 创建当前 turn（先创建以获取 turnNumber，供快照使用）
        AgentTurn turn = createTurn(session, userMessage, intentResult);

        // 上下文工程：L0-L5 分层装载 + Token 预算 + 降级链（ADR-070~072）
        ContextAssembler.AssembledContext assembled = contextAssembler.assemble(
                session.getId(), turn.getTurnNumber(), userMessage,
                intentResult, systemPrompt, history, userProfile);

        // 异步写入上下文快照（ADR-076，fire-and-forget，失败不阻塞主流程）
        contextSnapshotService.saveSnapshot(assembled.snapshot());

        String promptVersionStr = promptVersion != null ? promptVersion.getVersion() : null;
        return new ChatContext(session, assembled.messages(), turn, System.currentTimeMillis(),
                assembled.totalTokens(), retrievalContextJson, intentResult, null, promptVersionStr);
        } finally {
            MDC.remove("traceId");
        }
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
                .status(SessionStatus.INIT.name())
                .messageCount(0)
                .totalTokens(0)
                .totalCost(BigDecimal.ZERO)
                .lastMessageAt(LocalDateTime.now())
                .build();
        sessionMapper.insert(session);

        // 初始化 Redis 短期记忆（ADR-054，promptVersion/llmModel 后续在 completeTurn 中更新）
        conversationMemoryService.initSession(session.getId(), userId, null, null);

        // 初始化会话状态机为 INIT（ADR-064）
        sessionStateMachine.setStatus(session.getId(), SessionStatus.INIT, "Session created");

        return session;
    }

    /**
     * 加载对话历史（L4 层，按时间正序）。
     *
     * 只取 COMPLETED 状态的轮次，按 turnNumber 倒序取最近 N 条后反转为正序。
     * 实际截断由 ContextAssembler 按 Token 预算执行。
     */
    private List<AgentTurn> loadHistory(AgentSession session) {
        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, session.getId())
                .eq(AgentTurn::getStatus, "COMPLETED")
                .orderByDesc(AgentTurn::getTurnNumber)
                .last("LIMIT " + historyLimit);
        List<AgentTurn> history = turnMapper.selectList(wrapper);
        Collections.reverse(history);
        return history;
    }

    /**
     * 加载对话历史（Redis 短期记忆优先，DB 降级）。
     *
     * 装配顺序（ADR-054 读写时序）：
     *   1. 滚动摘要 → 虚拟轮次（user="[历史对话摘要]", assistant=summary）
     *   2. 槽位     → 虚拟轮次（user="[已确认约束]", assistant=slots JSON）
     *   3. Pin 消息 → 虚拟轮次（user="[用户偏好]", assistant=pinned 内容）
     *   4. 最近 N 轮原文消息（从 Redis 加载，Redis 为空时降级到 DB）
     *
     * 滚动摘要/槽位/Pin 作为虚拟 AgentTurn 注入 history 头部，
     * 由 ContextAssembler 自然纳入 L4 token 预算管理。
     */
    private List<AgentTurn> loadHistoryWithMemory(AgentSession session) {
        String sessionId = session.getId();
        List<AgentTurn> history = new ArrayList<>();

        // 1. 滚动摘要（L4 前缀）
        String rollingSummary = conversationMemoryService.loadSummary(sessionId);
        if (rollingSummary != null && !rollingSummary.isBlank()) {
            history.add(AgentTurn.builder()
                    .userMessage("[历史对话摘要]")
                    .assistantMessage(rollingSummary)
                    .build());
        }

        // 2. 槽位（L4 前缀）
        Map<String, String> slots = conversationMemoryService.loadSlots(sessionId);
        if (slots != null && !slots.isEmpty()) {
            history.add(AgentTurn.builder()
                    .userMessage("[已确认约束]")
                    .assistantMessage(slots.toString())
                    .build());
        }

        // 3. Pin 消息（L4 前缀）
        List<MemoryMessage> pinned = conversationMemoryService.loadPinned(sessionId);
        if (pinned != null && !pinned.isEmpty()) {
            StringBuilder pinContent = new StringBuilder();
            for (MemoryMessage msg : pinned) {
                if (msg.getContent() != null) {
                    pinContent.append(msg.getContent()).append("\n");
                }
            }
            if (pinContent.length() > 0) {
                history.add(AgentTurn.builder()
                        .userMessage("[用户偏好]")
                        .assistantMessage(pinContent.toString().trim())
                        .build());
            }
        }

        // 4. 最近 N 轮原文消息（Redis 优先，DB 降级）
        List<AgentTurn> recentTurns = conversationMemoryService.loadHistoryAsTurns(sessionId);
        if (recentTurns.isEmpty()) {
            // Redis 为空（新会话或 Redis 故障），降级到 DB
            recentTurns = loadHistory(session);
        }
        history.addAll(recentTurns);

        return history;
    }

    private AgentTurn createTurn(AgentSession session, String userMessage, IntentResult intentResult) {
        int turnNumber = (session.getMessageCount() != null ? session.getMessageCount() : 0) + 1;
        AgentTurn turn = AgentTurn.builder()
                .sessionId(session.getId())
                .turnNumber(turnNumber)
                .userMessage(userMessage)
                .messageRole("user")
                .status("STREAMING")
                .modelName(modelName)
                .intent(intentResult != null ? intentResult.getIntent().name() : null)
                .intentConfidence(intentResult != null ? BigDecimal.valueOf(intentResult.getConfidence()) : null)
                .feedback("NONE")
                .interrupted(0)
                .build();
        turnMapper.insert(turn);
        return turn;
    }

    private void completeTurn(AgentTurn turn, AgentSession session, String content, long elapsedMs,
            DeepSeekResponse.Usage usage, int inputTokens, String retrievalContextJson,
            IntentResult intentResult, String promptVersion) {
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
                totalTokens = inputTokens + completionTokens;
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
            turn.setInputTokens(inputTokens);
            turn.setOutputTokens(completionTokens);
            turnMapper.updateById(turn);

            session.setMessageCount(turn.getTurnNumber());
            session.setTotalTokens((session.getTotalTokens() != null ? session.getTotalTokens() : 0) + totalTokens);
            session.setTotalInputTokens((session.getTotalInputTokens() != null ? session.getTotalInputTokens() : 0) + inputTokens);
            session.setTotalOutputTokens((session.getTotalOutputTokens() != null ? session.getTotalOutputTokens() : 0) + completionTokens);
            if (promptVersion != null && session.getPromptVersion() == null) {
                session.setPromptVersion(promptVersion);
            }
            if (session.getLlmModel() == null) {
                session.setLlmModel(modelName);
            }
            session.setLastMessageAt(LocalDateTime.now());
            sessionMapper.updateById(session);

            // 写入 Redis 短期记忆（ADR-054 读写时序：LLM 回答后写）
            String userMessage = turn.getUserMessage();
            writeToMemory(session.getId(), turn.getTurnNumber(), userMessage, content,
                    intentResult, inputTokens, completionTokens);

            // 检查是否需要压缩（messages 长度 > 10 时触发三级压缩 ADR-050）
            if (conversationMemoryService.needsCompression(session.getId())) {
                triggerCompression(session.getId());
            }

            log.info(
                    "Turn completed: sessionId={}, turn={}, inputTokens={}, outputTokens={}, totalTokens={}, elapsedMs={}, violation={}",
                    session.getId(), turn.getTurnNumber(), inputTokens, completionTokens, totalTokens, elapsedMs,
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
            turn.setInputTokens(0);
            turn.setOutputTokens(0);
            turnMapper.updateById(turn);

            session.setMessageCount(turn.getTurnNumber());
            session.setLastMessageAt(LocalDateTime.now());
            if (session.getLlmModel() == null) {
                session.setLlmModel(modelName);
            }
            sessionMapper.updateById(session);

            // 写入 Redis 短期记忆（模板回复也记录，保持上下文连贯；不触发压缩）
            writeToMemory(session.getId(), turn.getTurnNumber(), turn.getUserMessage(), templateReply,
                    ctx.intentResult(), 0, 0);

            log.info("Short-circuit turn completed: sessionId={}, turn={}, elapsedMs={}, intent={}",
                    session.getId(), turn.getTurnNumber(), elapsedMs,
                    ctx.intentResult() != null ? ctx.intentResult().getIntent() : "null");
        } catch (Exception e) {
            log.error("Failed to complete short-circuit turn {}", ctx.turn().getId(), e);
        }
    }

    /**
     * 写入 Redis 短期记忆（ADR-054 读写时序：LLM 回答后写）。
     *
     * 写入内容：
     *   - user 消息 + assistant 消息 → messages List
     *   - turn_count + 1, last_active_at 更新, current_intent 更新 → meta Hash
     *   - 5 Key TTL 续期 2 小时
     */
    private void writeToMemory(String sessionId, int turnNumber, String userMessage, String assistantContent,
            IntentResult intentResult, int inputTokens, int outputTokens) {
        try {
            long ts = System.currentTimeMillis() / 1000;

            // 写入 user 消息
            if (userMessage != null) {
                conversationMemoryService.appendMessage(sessionId, MemoryMessage.builder()
                        .turnId(turnNumber)
                        .role("user")
                        .content(userMessage)
                        .tokens(ENCODING.countTokens(userMessage))
                        .ts(ts)
                        .build());
            }

            // 写入 assistant 消息
            if (assistantContent != null) {
                conversationMemoryService.appendMessage(sessionId, MemoryMessage.builder()
                        .turnId(turnNumber)
                        .role("assistant")
                        .content(assistantContent)
                        .tokens(ENCODING.countTokens(assistantContent))
                        .ts(ts)
                        .build());
            }

            // 更新 meta
            conversationMemoryService.incrementTurnCount(sessionId);
            conversationMemoryService.recordIntent(sessionId,
                    intentResult != null ? intentResult.getIntent().name() : "UNKNOWN");
            conversationMemoryService.updateMeta(sessionId, "last_active_at", String.valueOf(ts));

            // TTL 续期
            conversationMemoryService.renewTTL(sessionId);
        } catch (Exception e) {
            log.warn("Failed to write to memory: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 触发三级压缩（ADR-050~053）。
     *
     * 流程：
     *   1. 加载旧摘要 + 已有槽位
     *   2. 获取需要压缩的旧消息（保留最近 5 条）
     *   3. 调用 ContextCompressionService 三合一 LLM 压缩
     *   4. 更新 Redis summary/slots/pinned
     *   5. LTRIM messages 保留最近 5 条
     *
     * 降级（ADR-053）：LLM 失败时截断保留最近 4 条（2 轮），不生成摘要。
     */
    private void triggerCompression(String sessionId) {
        try {
            String oldSummary = conversationMemoryService.loadSummary(sessionId);
            Map<String, String> existingSlots = conversationMemoryService.loadSlots(sessionId);

            int keepCount = 5;
            List<MemoryMessage> toCompress = conversationMemoryService.getMessagesToCompress(sessionId, keepCount);
            if (toCompress.isEmpty()) {
                log.debug("No messages to compress: sessionId={}", sessionId);
                return;
            }

            ContextCompressionService.CompressionResult result = contextCompressionService.compress(
                    oldSummary, toCompress, existingSlots);

            // 更新滚动摘要
            if (result.summary() != null) {
                conversationMemoryService.updateSummary(sessionId, result.summary());
            }

            // 更新槽位
            if (result.slots() != null && !result.slots().isEmpty()) {
                conversationMemoryService.updateSlots(sessionId, result.slots());
            }

            // Pin 新消息
            if (result.pins() != null && !result.pins().isEmpty()) {
                for (ContextCompressionService.PinnedMessage pin : result.pins()) {
                    conversationMemoryService.pinMessage(sessionId, MemoryMessage.builder()
                            .role("user")
                            .content(pin.content())
                            .ts(System.currentTimeMillis() / 1000)
                            .build());
                }
            }

            // 截断 messages（降级模式保留更少）
            if (result.fallback()) {
                conversationMemoryService.trimMessages(sessionId, 4);
            } else {
                conversationMemoryService.trimMessages(sessionId, keepCount);
            }

            log.info("Compression completed: sessionId={}, fallback={}, messagesCompressed={}, summaryLen={}, pins={}",
                    sessionId, result.fallback(), toCompress.size(),
                    result.summary() != null ? result.summary().length() : 0,
                    result.pins() != null ? result.pins().size() : 0);
        } catch (Exception e) {
            log.warn("Compression failed: sessionId={}, error={}", sessionId, e.getMessage());
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

    /**
     * 标记会话为 ERROR 状态（连续失败或致命错误时调用）。
     */
    private void markSessionError(AgentSession session, String reason) {
        try {
            session.setStatus("ERROR");
            session.setErrorReason(reason);
            sessionMapper.updateById(session);
        } catch (Exception e) {
            log.error("Failed to mark session error {}", session.getId(), e);
        }
    }

    public record ChatEvent(String type, String data) {
    }

    private record ChatContext(AgentSession session, List<DeepSeekRequest.Message> messages,
            AgentTurn turn, long startTime, int inputTokens, String retrievalContext,
            IntentResult intentResult, RouteDecision routeDecision, String promptVersion) {
    }

    /**
     * 加载上一轮 COMPLETED turn 的检索结果（CLARIFY 意图用，ADR-026）。
     *
     * 从 AgentTurn.retrievalContext 字段反序列化 List<RetrievalResult>。
     * 失败时降级为空列表，不影响主流程。
     */
    private List<RetrievalResult> loadPreviousRetrieval(String sessionId) {
        try {
            AgentTurn lastTurn = turnMapper.selectOne(
                    new LambdaQueryWrapper<AgentTurn>()
                            .eq(AgentTurn::getSessionId, sessionId)
                            .eq(AgentTurn::getStatus, "COMPLETED")
                            .orderByDesc(AgentTurn::getTurnNumber)
                            .last("LIMIT 1")
            );
            if (lastTurn == null || lastTurn.getRetrievalContext() == null) {
                return Collections.emptyList();
            }
            List<RetrievalResult> previous = objectMapper.readValue(
                    lastTurn.getRetrievalContext(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RetrievalResult.class)
            );
            log.debug("Loaded previous retrieval: {} results for sessionId={}", previous.size(), sessionId);
            return previous;
        } catch (Exception e) {
            log.warn("Failed to load previous retrieval context for sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 序列化检索结果为 JSON（存入 AgentTurn.retrievalContext）。
     *
     * 序列化完整 RetrievalResult（含 content + metadata），供 CLARIFY 时
     * loadPreviousRetrieval 反序列化后用于上下文合并。
     */
    private String formatRetrievalContextJson(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize retrieval context", e);
            return null;
        }
    }
}
