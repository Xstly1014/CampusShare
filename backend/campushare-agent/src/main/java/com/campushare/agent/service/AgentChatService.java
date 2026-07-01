package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.llm.PromptConstants;
import com.campushare.agent.mapper.AgentSessionMapper;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
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
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final DeepSeekClient deepSeekClient;
    private final AgentSessionMapper sessionMapper;
    private final AgentTurnMapper turnMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final EncodingRegistry ENCODING_REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = ENCODING_REGISTRY.getEncodingForModel(ModelType.GPT_3_5_TURBO);

    @Value("${app.agent.history-limit:10}")
    private int historyLimit;

    public Flux<ChatEvent> chat(String userId, ChatRequest request) {
        return Mono.fromCallable(() -> prepareContext(userId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> {
                    StringBuilder assistantContent = new StringBuilder();
                    AtomicReference<DeepSeekResponse.Usage> usageRef = new AtomicReference<>();

                    String sessionJson;
                    try {
                        Map<String, String> sessionPayload = new HashMap<>();
                        sessionPayload.put("sessionId", ctx.session().getId());
                        sessionJson = objectMapper.writeValueAsString(sessionPayload);
                    } catch (JsonProcessingException e) {
                        sessionJson = "{\"sessionId\":\"" + ctx.session().getId() + "\"}";
                    }

                    Flux<ChatEvent> sessionEvent = Flux.just(new ChatEvent("session", sessionJson));

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
                                                usageRef.get(), ctx.promptTokens());
                                    } else if (signalType == SignalType.ON_ERROR) {
                                        errorTurn(ctx.turn(), "Stream terminated with error");
                                    }
                                }).subscribeOn(Schedulers.boundedElastic()).subscribe();
                            });

                    return Flux.concat(sessionEvent, deltaStream);
                });
    }

    private ChatContext prepareContext(String userId, ChatRequest request) {
        AgentSession session = getOrCreateSession(userId, request);
        List<DeepSeekRequest.Message> messages = buildMessages(session, request.getMessage());
        int promptTokens = countPromptTokens(messages);
        AgentTurn turn = createTurn(session, request.getMessage());
        return new ChatContext(session, messages, turn, System.currentTimeMillis(), promptTokens);
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

    private List<DeepSeekRequest.Message> buildMessages(AgentSession session, String currentMessage) {
        List<DeepSeekRequest.Message> messages = new ArrayList<>();

        messages.add(DeepSeekRequest.Message.builder()
                .role("system")
                .content(PromptConstants.SYSTEM_PROMPT)
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
                .modelName("deepseek-chat")
                .build();
        turnMapper.insert(turn);
        return turn;
    }

    private void completeTurn(AgentTurn turn, AgentSession session, String content, long elapsedMs,
                              DeepSeekResponse.Usage usage, int promptTokens) {
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

            turn.setAssistantMessage(content);
            turn.setStatus("COMPLETED");
            turn.setResponseTimeMs((int) elapsedMs);
            turn.setTokensUsed(totalTokens);
            turnMapper.updateById(turn);

            session.setMessageCount(turn.getTurnNumber());
            session.setTotalTokens((session.getTotalTokens() != null ? session.getTotalTokens() : 0) + totalTokens);
            session.setLastMessageAt(LocalDateTime.now());
            sessionMapper.updateById(session);

            log.info("Turn completed: sessionId={}, turn={}, promptTokens={}, completionTokens={}, totalTokens={}, elapsedMs={}",
                    session.getId(), turn.getTurnNumber(), promptTokens, completionTokens, totalTokens, elapsedMs);
        } catch (Exception e) {
            log.error("Failed to complete turn {}", turn.getId(), e);
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

    public record ChatEvent(String type, String data) {}

    private record ChatContext(AgentSession session, List<DeepSeekRequest.Message> messages,
                               AgentTurn turn, long startTime, int promptTokens) {}
}
