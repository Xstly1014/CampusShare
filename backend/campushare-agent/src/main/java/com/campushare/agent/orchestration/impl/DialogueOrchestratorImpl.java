package com.campushare.agent.orchestration.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.dto.UserProfile;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.campushare.agent.orchestration.DialogueOrchestrator;
import com.campushare.agent.prompt.PromptConstants;
import com.campushare.agent.tool.ToolExecutor;
import com.campushare.agent.tool.ToolRegistry;
import com.campushare.agent.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM-first 对话编排器。
 *
 * 不再使用 CLARIFY/REACT/COT/PLAN_AND_EXECUTE 等模式选择，而是直接让 LLM 决定：
 * 1. 是否需要调用工具（search_posts / remember_name / navigate_to）
 * 2. 根据工具结果生成最终自然语言回复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogueOrchestratorImpl implements DialogueOrchestrator {

    private final DeepSeekClient deepSeekClient;
    private final AgentTurnMapper turnMapper;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<TurnResponse> orchestrate(String userId, String sessionId, String userMessage,
            IntentResult intentResult, List<RetrievalResult> retrievalResults,
            UserProfile userProfile, List<Map<String, Object>> previousRefs) {
        List<DeepSeekRequest.Message> messages = buildToolUseMessages(
                sessionId, userMessage, userProfile, previousRefs, retrievalResults);

        List<Map<String, Object>> tools = toolRegistry.getAllToolSchemas();

        return deepSeekClient.chatCompletion(messages, tools)
                .flatMap(response -> {
                    if (response.hasToolCalls()) {
                        return executeToolCalls(userId, response.getToolCalls(), retrievalResults)
                                .flatMap(toolResults -> generateFinalAnswer(
                                        sessionId, userMessage, userProfile, previousRefs,
                                        retrievalResults, toolResults, response));
                    }
                    return Mono.just(TurnResponse.builder()
                            .content(response.getContent())
                            .refs(buildRefsFromResults(retrievalResults))
                            .build());
                });
    }

    private List<DeepSeekRequest.Message> buildToolUseMessages(String sessionId, String userMessage,
            UserProfile userProfile,
            List<Map<String, Object>> previousRefs,
            List<RetrievalResult> retrievalResults) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        StringBuilder system = new StringBuilder(PromptConstants.TOOL_USE_SYSTEM);
        system.append("\n\n").append(PromptConstants.PLATFORM_PROMPT);

        if (userProfile != null && userProfile.nickname() != null && !userProfile.nickname().isBlank()) {
            system.append("\n\n用户昵称：").append(userProfile.nickname()).append("\n请使用用户昵称进行友好回应。");
        }

        if (previousRefs != null && !previousRefs.isEmpty()) {
            system.append("\n\n上一轮推荐内容：\n");
            for (Map<String, Object> ref : previousRefs) {
                system.append("- [").append(ref.get("index")).append("] ")
                        .append(ref.get("title")).append("\n");
            }
            system.append("如果用户追问\"刚刚推荐的内容\"，请基于以上列表回答，不要重新检索。");
        }

        if (retrievalResults != null && !retrievalResults.isEmpty()) {
            system.append("\n\n当前检索结果：\n");
            for (int i = 0; i < Math.min(retrievalResults.size(), 8); i++) {
                RetrievalResult r = retrievalResults.get(i);
                system.append(String.format("[%d] (%s) %s\n", i + 1, r.source(), r.title()));
                if (r.content() != null && !r.content().isBlank()) {
                    String snippet = r.content().length() > 200 ? r.content().substring(0, 200) + "..." : r.content();
                    system.append(snippet).append("\n");
                }
            }
        }

        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(system.toString())
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
                .content(userMessage)
                .build());

        return messages;
    }

    private Mono<List<ToolCallResult>> executeToolCalls(String userId, List<DeepSeekResponse.ToolCall> toolCalls,
            List<RetrievalResult> retrievalResults) {
        List<Mono<ToolCallResult>> monos = new ArrayList<>();
        for (DeepSeekResponse.ToolCall tc : toolCalls) {
            String name = tc.getFunction().getName();
            Map<String, Object> args;
            try {
                args = objectMapper.readValue(tc.getFunction().getArguments(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("Failed to parse tool arguments for {}: {}", name, e.getMessage());
                args = Collections.emptyMap();
            }
            Mono<ToolCallResult> mono = toolExecutor.execute(name, args, userId)
                    .map(result -> new ToolCallResult(tc.getId(), name, toolExecutor.resultToJson(result)));
            monos.add(mono);
        }
        return Mono.zip(monos, objects -> {
            List<ToolCallResult> results = new ArrayList<>();
            for (Object o : objects) {
                results.add((ToolCallResult) o);
            }
            return results;
        });
    }

    private Mono<TurnResponse> generateFinalAnswer(String sessionId, String userMessage, UserProfile userProfile,
            List<Map<String, Object>> previousRefs,
            List<RetrievalResult> retrievalResults,
            List<ToolCallResult> toolResults,
            DeepSeekResponse firstResponse) {
        List<DeepSeekRequest.Message> messages = buildToolUseMessages(
                sessionId, userMessage, userProfile, previousRefs, retrievalResults);

        DeepSeekRequest.Message assistantMsg = DeepSeekRequest.Message.builder()
                .role("assistant")
                .content(firstResponse.getContent())
                .toolCalls(convertToolCalls(firstResponse.getToolCalls()))
                .build();
        messages.add(assistantMsg);

        for (ToolCallResult r : toolResults) {
            messages.add(DeepSeekRequest.Message.builder()
                    .role("tool")
                    .toolCallId(r.toolCallId())
                    .name(r.toolName())
                    .content(r.resultJson())
                    .build());
        }

        Map<String, String> navigate = extractNavigate(toolResults);

        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
                        .refs(buildRefsFromResults(retrievalResults))
                        .navigate(navigate)
                        .build());
    }

    private List<DeepSeekRequest.ToolCall> convertToolCalls(List<DeepSeekResponse.ToolCall> toolCalls) {
        List<DeepSeekRequest.ToolCall> result = new ArrayList<>();
        for (DeepSeekResponse.ToolCall tc : toolCalls) {
            result.add(DeepSeekRequest.ToolCall.builder()
                    .id(tc.getId())
                    .type(tc.getType())
                    .function(DeepSeekRequest.FunctionCall.builder()
                            .name(tc.getFunction().getName())
                            .arguments(tc.getFunction().getArguments())
                            .build())
                    .build());
        }
        return result;
    }

    private Map<String, String> extractNavigate(List<ToolCallResult> toolResults) {
        for (ToolCallResult r : toolResults) {
            if ("navigate_to".equals(r.toolName())) {
                try {
                    Map<String, Object> map = objectMapper.readValue(r.resultJson(),
                            new TypeReference<Map<String, Object>>() {
                            });
                    Map<String, String> nav = new HashMap<>();
                    nav.put("route", String.valueOf(map.get("route")));
                    nav.put("label", String.valueOf(map.getOrDefault("label", "点击跳转")));
                    return nav;
                } catch (Exception ignored) {
                    log.warn("Failed to extract navigate info from tool result");
                }
            }
        }
        return null;
    }

    private List<AgentTurn> loadRecentTurns(String sessionId, int limit) {
        List<AgentTurn> turns = turnMapper.selectList(
                new LambdaQueryWrapper<AgentTurn>()
                        .eq(AgentTurn::getSessionId, sessionId)
                        .orderByDesc(AgentTurn::getTurnNumber)
                        .last("LIMIT " + limit));
        Collections.reverse(turns);
        return turns;
    }

    private List<Map<String, Object>> buildRefsFromResults(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        int index = 1;
        Set<String> seenIds = new HashSet<>();
        for (RetrievalResult r : results) {
            if (r.id() == null || seenIds.contains(r.id()))
                continue;
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
            if (index > 10)
                break;
        }
        return refs;
    }

    @Override
    public Mono<TurnResponse> summarize(String userId, String sessionId) {
        List<AgentTurn> history = loadRecentTurns(sessionId, 100);
        if (history.isEmpty()) {
            return Mono.just(TurnResponse.builder()
                    .content("当前会话还没有消息可以总结。")
                    .isSummary(true)
                    .build());
        }
        StringBuilder sb = new StringBuilder();
        for (AgentTurn turn : history) {
            sb.append("用户：").append(turn.getUserMessage()).append("\n");
            sb.append("助手：").append(turn.getAssistantMessage()).append("\n\n");
        }
        List<DeepSeekRequest.Message> messages = List.of(
                DeepSeekRequest.Message.builder().role("system").content("请总结以下对话：").build(),
                DeepSeekRequest.Message.builder().role("user").content(sb.toString()).build());
        return deepSeekClient.chatCompletion(messages, Collections.emptyList())
                .map(response -> TurnResponse.builder()
                        .content(response.getContent())
                        .isSummary(true)
                        .build());
    }

    private record ToolCallResult(String toolCallId, String toolName, String resultJson) {
    }
}
