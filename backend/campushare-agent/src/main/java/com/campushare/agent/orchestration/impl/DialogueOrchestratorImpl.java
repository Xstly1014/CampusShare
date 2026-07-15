package com.campushare.agent.orchestration.impl;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.campushare.agent.orchestration.DialogueOrchestrator;
import com.campushare.agent.orchestration.OrchestrationMode;
import com.campushare.agent.prompt.PromptAssembler;
import com.campushare.agent.service.ContextAssembler;
import com.campushare.agent.service.ConversationMemoryService;
import com.campushare.agent.tool.ToolExecutor;
import com.campushare.agent.tool.ToolRegistry;
import com.campushare.agent.tool.ToolResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DialogueOrchestratorImpl implements DialogueOrchestrator {

    private final DeepSeekClient deepSeekClient;
    private final AgentTurnMapper turnMapper;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ContextAssembler contextAssembler;
    private final ConversationMemoryService conversationMemoryService;
    private final PromptAssembler promptAssembler;
    private final ObjectMapper objectMapper;

    @Value("${app.orchestrator.max-plan-steps:5}")
    private int maxPlanSteps;

    @Value("${app.orchestrator.max-clarify-rounds:2}")
    private int maxClarifyRounds;

    @Value("${app.orchestrator.reflexion-threshold:0.5}")
    private double reflexionThreshold;

    private static final Pattern SURRENDER_PATTERN = Pattern.compile(
            "^(随便|都行|都可以|无所谓|随便吧|都行吧|你看着办|你定|随便你|不用了|没事|算了|不用问了|直接给|直接说|你推荐|随便推荐|你觉得呢)$"
    );

    @Override
    public Mono<TurnResponse> orchestrate(String userId, String sessionId, String userMessage,
                                           IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        int turnNumber = getTurnNumber(sessionId);
        OrchestrationMode mode = selectMode(intentResult, turnNumber, userMessage, sessionId);

        log.info("Selected orchestration mode: {} for intent: {}, turn: {}, message: '{}'",
                mode, intentResult.getIntent(), turnNumber, truncate(userMessage, 30));

        return switch (mode) {
            case CLARIFY -> clarify(userId, sessionId, userMessage, intentResult, retrievalResults);
            case PLAN_AND_EXECUTE -> planAndExecute(userId, sessionId, userMessage, intentResult, retrievalResults);
            case REFLEXION -> reflexion(userId, sessionId, userMessage, intentResult, retrievalResults);
            case COT -> chainOfThought(userId, sessionId, userMessage, intentResult, retrievalResults);
            case REACT -> react(userId, sessionId, userMessage, intentResult, retrievalResults);
        };
    }

    @Override
    public Mono<TurnResponse> clarify(String userId, String sessionId, String userMessage,
                                      IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        if (isSurrenderMessage(userMessage)) {
            log.info("User surrendered (said '{}'), proceeding with defaults and existing results", userMessage);
            return directAnswerWithDefaults(userId, sessionId, userMessage, intentResult, retrievalResults);
        }

        int clarifyCount = getClarifyCount(sessionId);
        if (clarifyCount >= maxClarifyRounds) {
            log.info("Clarify rounds exhausted ({}), proceeding with defaults for query='{}'",
                    clarifyCount, truncate(userMessage, 30));
            return directAnswerWithDefaults(userId, sessionId, userMessage, intentResult, retrievalResults);
        }

        List<RetrievalResult> resultsToUse = retrievalResults;
        if (resultsToUse == null || resultsToUse.isEmpty()) {
            resultsToUse = loadPreviousRetrievalSync(sessionId);
        }

        // If we have retrieval results, answer directly instead of asking another clarification.
        // The retrieval service already merged previous results for CLARIFY intent (ADR-026).
        boolean hasResults = resultsToUse != null && !resultsToUse.isEmpty();
        if (hasResults) {
            log.info("Have retrieval results ({}), answering directly instead of clarifying", resultsToUse.size());
            return directAnswerWithRetrieval(sessionId, userMessage, intentResult, resultsToUse);
        }

        List<DeepSeekRequest.Message> messages = buildClarifyMessages(sessionId, userMessage, intentResult, resultsToUse);

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
                        .isClarification(true)
                        .build());
    }

    private Mono<TurnResponse> directAnswerWithDefaults(String userId, String sessionId, String userMessage,
                                                         IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        fillDefaultSlots(intentResult);

        List<RetrievalResult> resultsToUse = retrievalResults;
        if (resultsToUse == null || resultsToUse.isEmpty()) {
            resultsToUse = loadPreviousRetrievalSync(sessionId);
        }

        return buildRagAnswer(sessionId, userMessage, intentResult, resultsToUse);
    }

    private Mono<TurnResponse> directAnswerWithRetrieval(String sessionId, String userMessage,
                                                          IntentResult intentResult, List<RetrievalResult> results) {
        fillDefaultSlots(intentResult);
        return buildRagAnswer(sessionId, userMessage, intentResult, results);
    }

    private Mono<TurnResponse> buildRagAnswer(String sessionId, String userMessage,
                                               IntentResult intentResult, List<RetrievalResult> results) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        StringBuilder systemContent = new StringBuilder();
        systemContent.append("你是CampusShare校园资源共享平台的AI助手小享。请根据提供的参考资料回答用户问题。\n");
        systemContent.append("回答要求：\n");
        systemContent.append("1. 基于参考资料回答，如资料不足请诚实说明\n");
        systemContent.append("2. 回答自然流畅，不要提到\"参考资料\"或\"检索\"等技术术语\n");
        systemContent.append("3. 如果用户没有指定具体条件（如校区、排序等），直接给出最相关的推荐即可\n");
        systemContent.append("4. 使用中文回答，语气亲切友好，用Markdown格式\n");
        systemContent.append("5. **只要检索结果非空，就不要说\"未找到相关资源\"**，列出找到的内容\n");
        systemContent.append("6. 引用资料用 [1][2] 编号标注\n");
        systemContent.append("7. **不要反复追问**，基于已有信息给出最佳推荐\n\n");

        if (results != null && !results.isEmpty()) {
            systemContent.append("=== 参考资料 ===\n");
            for (int i = 0; i < Math.min(results.size(), 8); i++) {
                RetrievalResult r = results.get(i);
                systemContent.append(String.format("[%d] (%s) %s\n", i + 1, r.source(), r.title()));
                if (r.content() != null && !r.content().isBlank()) {
                    String snippet = r.content().length() > 200 ? r.content().substring(0, 200) + "..." : r.content();
                    systemContent.append(snippet).append("\n");
                }
            }
        } else {
            systemContent.append("=== 无参考资料 ===\n请基于你的知识为用户提供帮助，注意你是CampusShare平台助手。\n");
        }

        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(systemContent.toString())
                .build());

        List<AgentTurn> recentTurns = loadRecentTurns(sessionId, 5);
        for (AgentTurn turn : recentTurns) {
            if (turn.getUserMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(turn.getUserMessage())
                        .build());
            }
            if (turn.getAssistantMessage() != null && !"[CLARIFY]".equals(turn.getAssistantMessage())) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("assistant")
                        .content(turn.getAssistantMessage())
                        .build());
            }
        }

        String effectiveQuery = buildEffectiveQuery(userMessage, intentResult);
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(effectiveQuery)
                .build());

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
                        .isClarification(false)
                        .refs(buildRefsFromResults(results))
                        .build());
    }

    /**
     * Build the effective user query for the LLM.
     * - If the user surrendered (said "随便" etc.), replace with a contextual prompt
     *   so the LLM knows to recommend from existing results without asking more questions.
     * - Otherwise, prefer the rewritten query if available and different from the original.
     */
    private String buildEffectiveQuery(String userMessage, IntentResult intentResult) {
        if (isSurrenderMessage(userMessage)) {
            return "请基于已有资料给我推荐，不需要再追问细节";
        }
        String rewritten = intentResult.getRewrittenQuery();
        return (rewritten != null && !rewritten.equals(userMessage)) ? rewritten : userMessage;
    }

    private List<Map<String, Object>> buildRefsFromResults(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        int index = 1;
        Set<String> seenIds = new HashSet<>();
        for (RetrievalResult r : results) {
            if (r.id() == null || seenIds.contains(r.id())) continue;
            seenIds.add(r.id());
            Map<String, Object> ref = new HashMap<>();
            ref.put("index", index);
            ref.put("id", r.id());
            ref.put("type", r.source() != null ? r.source().name() : "POST");
            ref.put("title", r.title());
            if (r.source() == RetrievalResult.Source.POST) {
                ref.put("url", "/post/" + r.id());
            }
            refs.add(ref);
            index++;
            if (index > 10) break;
        }
        return refs;
    }

    private void fillDefaultSlots(IntentResult intentResult) {
        if (intentResult.getSlots() == null) {
            intentResult.setSlots(IntentResult.SlotResult.builder()
                    .sort("latest")
                    .postType("all")
                    .build());
        } else {
            if (intentResult.getSlots().getSort() == null || intentResult.getSlots().getSort().isBlank()) {
                intentResult.getSlots().setSort("latest");
            }
            if (intentResult.getSlots().getPostType() == null || intentResult.getSlots().getPostType().isBlank()) {
                intentResult.getSlots().setPostType("all");
            }
        }
        if (intentResult.getIntent() == Intent.CLARIFY) {
            intentResult.setIntent(Intent.SEARCH);
            if (intentResult.getSubIntent() == null || intentResult.getSubIntent().isBlank()) {
                intentResult.setSubIntent(Intent.SubIntent.RESOURCE);
            }
        }
    }

    private boolean isSurrenderMessage(String message) {
        if (message == null || message.isBlank()) return false;
        return SURRENDER_PATTERN.matcher(message.trim()).matches();
    }

    @Override
    public Mono<TurnResponse> summarize(String userId, String sessionId) {
        return Mono.fromCallable(() -> loadSessionHistory(sessionId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(history -> {
                    if (history.isEmpty()) {
                        return Mono.just(TurnResponse.builder()
                                .content("当前会话还没有消息可以总结。")
                                .build());
                    }

                    String historyText = buildHistorySummary(history);
                    List<DeepSeekRequest.Message> messages = buildSummarizeMessages(historyText);

                    return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                            .map(response -> TurnResponse.builder()
                                    .content(response.getContent())
                                    .isSummary(true)
                                    .build());
                });
    }

    @Override
    public Mono<TurnResponse> planAndExecute(String userId, String sessionId, String userMessage,
                                              IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        return buildPlan(sessionId, userMessage, intentResult, retrievalResults)
                .flatMap(plan -> {
                    log.info("Generated plan: {}", plan);
                    return executePlan(userId, sessionId, plan);
                });
    }

    @Override
    public Mono<TurnResponse> reflexion(String userId, String sessionId, String userMessage,
                                         IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        return analyzePastAttempts(sessionId, userMessage)
                .flatMap(analysis -> {
                    if (analysis.confident()) {
                        return react(userId, sessionId, userMessage, intentResult, retrievalResults);
                    }

                    List<DeepSeekRequest.Message> messages = buildReflexionMessages(
                            userMessage, analysis.reflection(), intentResult);

                    return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                            .flatMap(response -> {
                                String refinedQuery = response.getContent();
                                log.info("Refined query after reflexion: {}", refinedQuery);
                                return react(userId, sessionId, refinedQuery, intentResult, retrievalResults);
                            });
                });
    }

    @Override
    public OrchestrationMode selectMode(IntentResult intentResult, int turnNumber) {
        return selectMode(intentResult, turnNumber, null, null);
    }

    private OrchestrationMode selectMode(IntentResult intentResult, int turnNumber, String userMessage, String sessionId) {
        if (intentResult.getIntent() == Intent.CLARIFY) {
            if (userMessage != null && isSurrenderMessage(userMessage)) {
                return OrchestrationMode.REACT;
            }
            if (sessionId != null) {
                int clarifyCount = getClarifyCount(sessionId);
                if (clarifyCount >= maxClarifyRounds) {
                    return OrchestrationMode.REACT;
                }
            }
            return OrchestrationMode.CLARIFY;
        }

        // HOW_TO → COT (includes retrieval results for step-by-step guidance)
        // PLAN_AND_EXECUTE omitted for MVP: it doesn't pass retrieval results to steps
        if (isReasoningRequired(intentResult)) {
            return OrchestrationMode.COT;
        }

        return OrchestrationMode.REACT;
    }

    private Mono<TurnResponse> react(String userId, String sessionId, String userMessage,
                                      IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = buildReactMessages(sessionId, userMessage, intentResult, retrievalResults);
        List<Map<String, Object>> toolSchemas = toolRegistry.getToolSchemas(intentResult.getIntent());

        return runToolCallLoop(messages, toolSchemas, userId, 0)
                .flatMap(finalMessages -> {
                    return deepSeekClient.chatCompletion(finalMessages, Collections.emptyList())
                            .map(response -> TurnResponse.builder()
                                    .content(response.getContent())
                                    .refs(buildRefsFromResults(retrievalResults))
                                    .build());
                });
    }

    private Mono<TurnResponse> chainOfThought(String userId, String sessionId, String userMessage,
                                               IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = buildCotMessages(sessionId, userMessage, intentResult, retrievalResults);

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
                        .refs(buildRefsFromResults(retrievalResults))
                        .build());
    }

    private Mono<List<String>> buildPlan(String sessionId, String userMessage,
                                          IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = buildPlanMessages(userMessage, intentResult, retrievalResults);

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> parsePlan(response.getContent()))
                .onErrorResume(e -> {
                    log.warn("Failed to build plan, using default steps", e);
                    return Mono.just(Collections.singletonList(userMessage));
                });
    }

    private Mono<TurnResponse> executePlan(String userId, String sessionId, List<String> plan) {
        AtomicReference<String> executionLog = new AtomicReference<>("");
        AtomicInteger indexRef = new AtomicInteger(0);

        return Flux.fromIterable(plan)
                .take(maxPlanSteps)
                .flatMapSequential(step -> {
                    int index = indexRef.getAndIncrement();
                    log.info("Executing plan step {}/{}: {}", index + 1, plan.size(), step);

                    IntentResult stepIntent = IntentResult.builder()
                            .intent(Intent.SEARCH)
                            .subIntent(Intent.SubIntent.RESOURCE)
                            .rewrittenQuery(step)
                            .slots(IntentResult.SlotResult.builder().sort("latest").postType("all").build())
                            .build();

                    return react(userId, sessionId, step, stepIntent, Collections.emptyList())
                            .doOnNext(response -> {
                                executionLog.updateAndGet(log ->
                                        log + String.format("步骤 %d: %s\n结果: %s\n\n",
                                                index + 1, step, response.getContent()));
                            });
                })
                .collectList()
                .flatMap(results -> {
                    String summary = buildPlanExecutionSummary(plan, executionLog.get());
                    return Mono.just(TurnResponse.builder()
                            .content(summary)
                            .build());
                });
    }

    private Mono<ReflexionAnalysis> analyzePastAttempts(String sessionId, String userMessage) {
        return Mono.fromCallable(() -> {
            List<AgentTurn> recentTurns = loadRecentTurns(sessionId, 3);
            if (recentTurns.isEmpty()) {
                return new ReflexionAnalysis(true, "");
            }

            StringBuilder analysis = new StringBuilder();
            analysis.append("过去的尝试：\n");
            for (AgentTurn turn : recentTurns) {
                analysis.append(String.format("- 用户: %s\n  助手: %s\n  意图: %s\n",
                        turn.getUserMessage(),
                        truncate(turn.getAssistantMessage(), 100),
                        turn.getIntent()));
            }

            return new ReflexionAnalysis(false, analysis.toString());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<List<DeepSeekRequest.Message>> runToolCallLoop(List<DeepSeekRequest.Message> messages,
                                                                 List<Map<String, Object>> toolSchemas,
                                                                 String userId, int round) {
        if (round >= 5 || toolSchemas == null || toolSchemas.isEmpty()) {
            return Mono.just(messages);
        }

        return deepSeekClient.chatCompletion(messages, toolSchemas)
                .flatMap(response -> {
                    if (!response.hasToolCalls()) {
                        return Mono.just(messages);
                    }

                    return executeToolCalls(response.getToolCalls(), userId)
                            .flatMap(results -> {
                                for (ToolResult result : results) {
                                    messages.add(DeepSeekRequest.Message.builder()
                                            .role("tool")
                                            .content(toolExecutor.resultToJson(result))
                                            .build());
                                }
                                return runToolCallLoop(messages, toolSchemas, userId, round + 1);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Tool call loop error at round {}", round, e);
                    return Mono.just(messages);
                });
    }

    private Mono<List<ToolResult>> executeToolCalls(List<com.campushare.agent.llm.DeepSeekResponse.ToolCall> toolCalls,
                                                     String userId) {
        return Flux.fromIterable(toolCalls)
                .flatMap(toolCall -> {
                    String toolName = toolCall.getFunction().getName();
                    String argumentsStr = toolCall.getFunction().getArguments();

                    return Mono.fromCallable(() -> {
                        Map<String, Object> arguments = new HashMap<>();
                        if (argumentsStr != null && !argumentsStr.isBlank()) {
                            arguments = objectMapper.readValue(argumentsStr,
                                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                        }
                        return arguments;
                    })
                    .flatMap(arguments -> toolExecutor.execute(toolName, arguments, userId))
                    .onErrorResume(e -> {
                        log.error("Tool execution failed: {}", toolName, e);
                        return Mono.just(ToolResult.builder()
                                .status(ToolResult.Status.ERROR)
                                .errorMessage(e.getMessage())
                                .build());
                    });
                })
                .collectList();
    }

    private List<DeepSeekRequest.Message> buildClarifyMessages(String sessionId, String userMessage,
                                                                IntentResult intentResult,
                                                                List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个对话澄清助手。你的任务是向用户提问以获取完成任务所需的关键缺失信息。" +
                        "请以自然、友好的方式提问，每次只问一个最关键的问题。\n\n" +
                        "重要规则：\n" +
                        "1. 只问真正影响结果的关键信息（如校区、分类），不要问无关紧要的细节\n" +
                        "2. 如果已经有检索结果，优先基于结果内容提问\n" +
                        "3. 不要重复问同样的问题\n" +
                        "4. 如果用户之前已经回答过某个问题，不要再次询问\n" +
                        "5. 最多追问1-2个问题，如果用户仍然无法提供，直接基于已有信息给出最佳推荐\n" +
                        "6. 根据历史对话上下文进行追问，不要忘记之前讨论的主题")
                .build());

        List<AgentTurn> recentTurns = loadRecentTurns(sessionId, 5);
        for (AgentTurn turn : recentTurns) {
            if (turn.getUserMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(turn.getUserMessage())
                        .build());
            }
            if (turn.getAssistantMessage() != null && !turn.getAssistantMessage().isBlank()
                    && !"[CLARIFY]".equals(turn.getAssistantMessage())) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("assistant")
                        .content(turn.getAssistantMessage())
                        .build());
            }
        }

        StringBuilder userContent = new StringBuilder();
        userContent.append("用户最新回复: ").append(userMessage).append("\n");
        userContent.append("当前意图: ").append(intentResult.getIntent()).append("/").append(intentResult.getSubIntent()).append("\n");
        userContent.append("已识别的槽位: ").append(intentResult.getSlots() != null ? intentResult.getSlots() : "无").append("\n");
        if (retrievalResults != null && !retrievalResults.isEmpty()) {
            userContent.append("已检索到 ").append(retrievalResults.size()).append(" 条相关结果：\n");
            for (int i = 0; i < Math.min(retrievalResults.size(), 5); i++) {
                userContent.append(String.format("  - %s\n", retrievalResults.get(i).title()));
            }
        }
        userContent.append("\n请基于以上信息，提出一个最关键的澄清问题。如果已经有足够信息或检索结果，请直接回答用户问题。");

        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(userContent.toString())
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildReactMessages(String sessionId, String userMessage,
                                                               IntentResult intentResult,
                                                               List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        StringBuilder systemContent = new StringBuilder();
        systemContent.append("你是CampusShare校园资源共享平台的AI助手小享，擅长帮助同学找资料、解答平台使用问题。\n");
        systemContent.append("回答要求：\n");
        systemContent.append("1. 基于提供的参考资料回答问题，引用资料时用 [1][2] 编号标注\n");
        systemContent.append("2. 回答自然流畅，不要提到\"参考资料\"或\"检索\"等技术术语\n");
        systemContent.append("3. 语气亲切友好，像学长学姐一样帮助同学\n");
        systemContent.append("4. 使用中文回答，用Markdown格式（关键词**加粗**，步骤用有序列表）\n");
        systemContent.append("5. 如果参考资料中有相关帖子，推荐给用户\n");
        systemContent.append("6. **只要检索结果非空，就不要说\"未找到相关资源\"**，而是列出找到的内容\n");
        systemContent.append("7. 如果信息不足，基于已有信息给出最佳建议，**不要反复追问**\n");
        systemContent.append("8. 简单问题50-150字，复杂问题150-300字\n\n");

        if (retrievalResults != null && !retrievalResults.isEmpty()) {
            systemContent.append("=== 参考资料 ===\n");
            systemContent.append("（注意：检索结果是语义匹配的，即使标题不完全包含搜索词也可能高度相关，请信任检索结果）\n");
            for (int i = 0; i < Math.min(retrievalResults.size(), 8); i++) {
                RetrievalResult r = retrievalResults.get(i);
                systemContent.append(String.format("[%d] (%s) %s\n", i + 1, r.source(), r.title()));
                if (r.content() != null && !r.content().isBlank()) {
                    String snippet = r.content().length() > 300 ? r.content().substring(0, 300) + "..." : r.content();
                    systemContent.append(snippet).append("\n");
                }
            }
        }

        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(systemContent.toString())
                .build());

        List<AgentTurn> recentTurns = loadRecentTurns(sessionId, 5);
        for (AgentTurn turn : recentTurns) {
            if (turn.getUserMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(turn.getUserMessage())
                        .build());
            }
            if (turn.getAssistantMessage() != null && !turn.getAssistantMessage().isBlank()
                    && !"[CLARIFY]".equals(turn.getAssistantMessage())) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("assistant")
                        .content(turn.getAssistantMessage())
                        .build());
            }
        }

        String effectiveQuery = buildEffectiveQuery(userMessage, intentResult);
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(effectiveQuery)
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildCotMessages(String sessionId, String userMessage,
                                                            IntentResult intentResult,
                                                            List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        StringBuilder systemContent = new StringBuilder();
        systemContent.append("你是CampusShare校园资源共享平台的AI助手小享。用户在询问平台使用方法，请逐步思考后给出答案。\n");
        systemContent.append("回答要求：\n");
        systemContent.append("1. 基于参考资料回答，步骤要具体可操作，用有序列表\n");
        systemContent.append("2. 关键词用**加粗**，引用资料用 [1][2] 编号\n");
        systemContent.append("3. 不要提到\"参考资料\"或\"检索\"等技术术语\n");
        systemContent.append("4. 使用中文回答，语气亲切友好\n");
        systemContent.append("5. **只要检索结果非空，就不要说\"未找到\"**，基于已有信息给出最佳指引\n");
        systemContent.append("6. 不要反复追问，直接给出操作步骤\n\n");

        if (retrievalResults != null && !retrievalResults.isEmpty()) {
            systemContent.append("=== 参考资料 ===\n");
            for (int i = 0; i < Math.min(retrievalResults.size(), 8); i++) {
                RetrievalResult r = retrievalResults.get(i);
                systemContent.append(String.format("[%d] (%s) %s\n", i + 1, r.source(), r.title()));
                if (r.content() != null && !r.content().isBlank()) {
                    String snippet = r.content().length() > 300 ? r.content().substring(0, 300) + "..." : r.content();
                    systemContent.append(snippet).append("\n");
                }
            }
        }

        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(systemContent.toString())
                .build());

        List<AgentTurn> recentTurns = loadRecentTurns(sessionId, 5);
        for (AgentTurn turn : recentTurns) {
            if (turn.getUserMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(turn.getUserMessage())
                        .build());
            }
            if (turn.getAssistantMessage() != null && !turn.getAssistantMessage().isBlank()
                    && !"[CLARIFY]".equals(turn.getAssistantMessage())) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("assistant")
                        .content(turn.getAssistantMessage())
                        .build());
            }
        }

        String effectiveQuery = buildEffectiveQuery(userMessage, intentResult);
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(effectiveQuery)
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildPlanMessages(String userMessage, IntentResult intentResult,
                                                             List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个任务规划助手。请将复杂任务分解为多个步骤。" +
                        "返回一个步骤列表，每行一个步骤，以数字开头。")
                .build());
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content("任务: " + userMessage + "\n意图: " + intentResult.getIntent())
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildReflexionMessages(String userMessage, String reflection,
                                                                  IntentResult intentResult) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个反思助手。根据过去的尝试分析，优化用户的查询。")
                .build());
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content("原始查询: " + userMessage + "\n过去尝试分析: " + reflection +
                        "\n请优化查询以提高成功率:")
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildSummarizeMessages(String historyText) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个会话总结助手。请用简洁的语言总结对话内容。")
                .build());
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content("对话历史:\n" + historyText + "\n\n请总结以上对话。")
                .build());
        return messages;
    }

    private List<String> parsePlan(String planText) {
        List<String> steps = new ArrayList<>();
        String[] lines = planText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+\\..*")) {
                steps.add(trimmed.replaceAll("^\\d+\\.\\s*", ""));
            } else if (!trimmed.isEmpty()) {
                steps.add(trimmed);
            }
        }
        return steps;
    }

    private String buildPlanExecutionSummary(List<String> plan, String executionLog) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行完成！\n\n计划步骤:\n");
        for (int i = 0; i < plan.size(); i++) {
            sb.append((i + 1)).append(". ").append(plan.get(i)).append("\n");
        }
        if (executionLog != null && !executionLog.isBlank()) {
            sb.append("\n执行详情:\n").append(executionLog);
        }
        return sb.toString();
    }

    private String buildHistorySummary(List<AgentTurn> history) {
        StringBuilder sb = new StringBuilder();
        for (AgentTurn turn : history) {
            sb.append("用户: ").append(turn.getUserMessage()).append("\n");
            sb.append("助手: ").append(turn.getAssistantMessage()).append("\n\n");
        }
        return sb.toString();
    }

    private boolean isReasoningRequired(IntentResult intentResult) {
        return intentResult.getIntent() == Intent.HOW_TO;
    }

    private int getTurnNumber(String sessionId) {
        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, sessionId);
        return turnMapper.selectCount(wrapper).intValue() + 1;
    }

    private int getClarifyCount(String sessionId) {
        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, sessionId)
                .eq(AgentTurn::getIntent, "CLARIFY");
        return turnMapper.selectCount(wrapper).intValue();
    }

    private List<AgentTurn> loadSessionHistory(String sessionId) {
        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, sessionId)
                .eq(AgentTurn::getStatus, "COMPLETED")
                .orderByAsc(AgentTurn::getTurnNumber);
        return turnMapper.selectList(wrapper);
    }

    private List<AgentTurn> loadRecentTurns(String sessionId, int limit) {
        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, sessionId)
                .eq(AgentTurn::getStatus, "COMPLETED")
                .orderByDesc(AgentTurn::getTurnNumber)
                .last("LIMIT " + limit);
        List<AgentTurn> turns = turnMapper.selectList(wrapper);
        Collections.reverse(turns);
        return turns;
    }

    private List<RetrievalResult> loadPreviousRetrievalSync(String sessionId) {
        try {
            AgentTurn lastTurn = turnMapper.selectOne(
                    new LambdaQueryWrapper<AgentTurn>()
                            .eq(AgentTurn::getSessionId, sessionId)
                            .eq(AgentTurn::getStatus, "COMPLETED")
                            .orderByDesc(AgentTurn::getTurnNumber)
                            .last("LIMIT 1"));
            if (lastTurn == null || lastTurn.getRetrievalContext() == null) {
                return Collections.emptyList();
            }
            List<RetrievalResult> previous = objectMapper.readValue(
                    lastTurn.getRetrievalContext(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RetrievalResult.class));
            log.debug("Loaded previous retrieval: {} results for sessionId={}", previous.size(), sessionId);
            return previous;
        } catch (Exception e) {
            log.warn("Failed to load previous retrieval context for sessionId={}", sessionId, e);
            return Collections.emptyList();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private record ReflexionAnalysis(boolean confident, String reflection) {}
}
