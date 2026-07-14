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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Value("${app.orchestrator.max-clarify-rounds:3}")
    private int maxClarifyRounds;

    @Value("${app.orchestrator.reflexion-threshold:0.5}")
    private double reflexionThreshold;

    @Override
    public Mono<TurnResponse> orchestrate(String userId, String sessionId, String userMessage,
                                           IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        int turnNumber = getTurnNumber(sessionId);
        OrchestrationMode mode = selectMode(intentResult, turnNumber);

        log.info("Selected orchestration mode: {} for intent: {}, turn: {}",
                mode, intentResult.getIntent(), turnNumber);

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
        int clarifyCount = getClarifyCount(sessionId);
        if (clarifyCount >= maxClarifyRounds) {
            return Mono.just(TurnResponse.builder()
                    .content("抱歉，我已经多次尝试确认信息，但仍未能获取足够的细节。请您提供更完整的信息后再试。")
                    .build());
        }

        List<DeepSeekRequest.Message> messages = buildClarifyMessages(sessionId, userMessage, intentResult);

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
                        .isClarification(true)
                        .build());
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
        if (intentResult.getIntent() == Intent.CLARIFY ||
            intentResult.getSlots() == null || hasMissingSlots(intentResult)) {
            return OrchestrationMode.CLARIFY;
        }

        if (isComplexTask(intentResult)) {
            return OrchestrationMode.PLAN_AND_EXECUTE;
        }

        if (turnNumber > 1 && hasFailedAttempts(intentResult.getIntent())) {
            return OrchestrationMode.REFLEXION;
        }

        if (isReasoningRequired(intentResult)) {
            return OrchestrationMode.COT;
        }

        return OrchestrationMode.REACT;
    }

    private Mono<TurnResponse> react(String userId, String sessionId, String userMessage,
                                      IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = buildReactMessages(sessionId, userMessage, intentResult);
        List<Map<String, Object>> toolSchemas = toolRegistry.getToolSchemas(intentResult.getIntent());

        return runToolCallLoop(messages, toolSchemas, userId, 0)
                .flatMap(finalMessages -> {
                    return deepSeekClient.chatCompletion(finalMessages, Collections.emptyList())
                            .map(response -> TurnResponse.builder()
                                    .content(response.getContent())
                                    .build());
                });
    }

    private Mono<TurnResponse> chainOfThought(String userId, String sessionId, String userMessage,
                                               IntentResult intentResult, List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = buildCotMessages(sessionId, userMessage, intentResult);

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
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
        AtomicReference<String> executionLog = new AtomicReference<>();
        AtomicInteger indexRef = new AtomicInteger(0);

        return Flux.fromIterable(plan)
                .take(maxPlanSteps)
                .flatMapSequential(step -> {
                    int index = indexRef.getAndIncrement();
                    log.info("Executing plan step {}/{}: {}", index + 1, plan.size(), step);

                    IntentResult stepIntent = IntentResult.builder()
                            .intent(intentResultFromStep(step))
                            .rewrittenQuery(step)
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
                        turn.getUserMessage(), turn.getAssistantMessage(), turn.getIntent()));
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
                                                                IntentResult intentResult) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个对话澄清助手。你的任务是向用户提问以获取完成任务所需的缺失信息。" +
                        "请以自然、友好的方式提问，每次只问一个问题。" +
                        "\n\n重要：请根据历史对话上下文进行追问，不要忘记之前讨论的主题。")
                .build());

        List<AgentTurn> recentTurns = loadRecentTurns(sessionId, 5);
        for (AgentTurn turn : recentTurns) {
            if (turn.getUserMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(turn.getUserMessage())
                        .build());
            }
            if (turn.getAssistantMessage() != null) {
                messages.add(DeepSeekRequest.Message.builder()
                        .role("assistant")
                        .content(turn.getAssistantMessage())
                        .build());
            }
        }

        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content("用户问题: " + userMessage + "\n当前意图: " + intentResult.getIntent() +
                        "\n已识别的槽位: " + (intentResult.getSlots() != null ? intentResult.getSlots() : "无"))
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildReactMessages(String sessionId, String userMessage,
                                                               IntentResult intentResult) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个ReAct风格的AI助手。使用思考-行动-观察循环来解决问题。")
                .build());
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());
        return messages;
    }

    private List<DeepSeekRequest.Message> buildCotMessages(String sessionId, String userMessage,
                                                            IntentResult intentResult) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();
        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content("你是一个善于推理的AI助手。请逐步思考你的答案，展示你的推理过程。")
                .build());
        messages.add(DeepSeekRequest.Message.builder()
                .role("user")
                .content(userMessage)
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
        sb.append("\n执行详情:\n").append(executionLog);
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

    private Intent intentResultFromStep(String step) {
        return Intent.SEARCH;
    }

    private boolean hasMissingSlots(IntentResult intentResult) {
        if (intentResult.getSlots() == null) {
            return true;
        }
        return intentResult.getSlots().getSchool() == null ||
                intentResult.getSlots().getSchool().isBlank();
    }

    private boolean isComplexTask(IntentResult intentResult) {
        String subIntent = intentResult.getSubIntent();
        return "feature_help".equals(subIntent) ||
                "rule_explain".equals(subIntent) ||
                "write_action".equals(subIntent);
    }

    private boolean isReasoningRequired(IntentResult intentResult) {
        return intentResult.getIntent() == Intent.HOW_TO ||
                intentResult.getIntent() == Intent.SEARCH;
    }

    private boolean hasFailedAttempts(Intent intent) {
        return false;
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

    private record ReflexionAnalysis(boolean confident, String reflection) {}
}
