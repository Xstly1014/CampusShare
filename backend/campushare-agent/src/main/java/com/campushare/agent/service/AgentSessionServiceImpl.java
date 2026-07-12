package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.SessionCreateRequest;
import com.campushare.agent.dto.SessionResponse;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.entity.AgentSession;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.enums.SessionStatus;
import com.campushare.agent.mapper.AgentSessionMapper;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionServiceImpl implements AgentSessionService {

    private final AgentSessionMapper sessionMapper;
    private final AgentTurnMapper turnMapper;
    private final SessionArchivalService sessionArchivalService;
    private final ConversationMemoryService conversationMemoryService;
    private final SessionStateMachine sessionStateMachine;
    private final ObjectMapper objectMapper;

    @Override
    public SessionResponse createSession(String userId, SessionCreateRequest request) {
        AgentSession session = AgentSession.builder()
                .userId(userId)
                .title(request != null && request.getTitle() != null ? request.getTitle() : "新对话")
                .status(SessionStatus.INIT.name())
                .messageCount(0)
                .totalTokens(0)
                .totalCost(BigDecimal.ZERO)
                .lastMessageAt(LocalDateTime.now())
                .build();
        sessionMapper.insert(session);

        // 初始化 Redis 短期记忆 + 状态机 INIT
        conversationMemoryService.initSession(session.getId(), userId, null, null);
        sessionStateMachine.setStatus(session.getId(), SessionStatus.INIT, "Session created");

        return toResponse(session);
    }

    @Override
    public SessionResponse getSession(String userId, String sessionId) {
        return toResponse(getSessionAndVerifyOwner(userId, sessionId));
    }

    @Override
    public List<SessionResponse> getUserSessions(String userId) {
        LambdaQueryWrapper<AgentSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentSession::getUserId, userId)
               .ne(AgentSession::getStatus, "DELETED")
               .orderByDesc(AgentSession::getLastMessageAt);
        return sessionMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void archiveSession(String userId, String sessionId) {
        AgentSession session = getSessionAndVerifyOwner(userId, sessionId);
        // 调用归档服务完成完整归档流程（状态转移+记忆抽取+Redis清理）
        sessionArchivalService.archiveSession(sessionId, "User initiated");
    }

    @Override
    public void deleteSession(String userId, String sessionId) {
        AgentSession session = getSessionAndVerifyOwner(userId, sessionId);
        // 先归档（抽取记忆），再标记删除
        sessionArchivalService.archiveSession(sessionId, "User deleted");
        session.setStatus("DELETED");
        sessionMapper.updateById(session);
    }

    @Override
    public SessionResponse moveSessionCategory(String userId, String sessionId, String categoryId) {
        AgentSession session = getSessionAndVerifyOwner(userId, sessionId);
        session.setCategoryId(categoryId);
        sessionMapper.updateById(session);
        return toResponse(session);
    }

    @Override
    public List<TurnResponse> getSessionTurns(String userId, String sessionId) {
        getSessionAndVerifyOwner(userId, sessionId);
        LambdaQueryWrapper<AgentTurn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTurn::getSessionId, sessionId)
               .orderByAsc(AgentTurn::getTurnNumber);
        return turnMapper.selectList(wrapper).stream()
                .map(this::toTurnResponse)
                .collect(Collectors.toList());
    }

    private AgentSession getSessionAndVerifyOwner(String userId, String sessionId) {
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.USER_ACCOUNT_FORBIDDEN, "无权访问此会话");
        }
        return session;
    }

    private SessionResponse toResponse(AgentSession session) {
        return SessionResponse.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .title(session.getTitle())
                .status(session.getStatus())
                .messageCount(session.getMessageCount())
                .totalTokens(session.getTotalTokens())
                .lastMessageAt(session.getLastMessageAt())
                .categoryId(session.getCategoryId())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private TurnResponse toTurnResponse(AgentTurn turn) {
        return TurnResponse.builder()
                .id(turn.getId())
                .sessionId(turn.getSessionId())
                .turnNumber(turn.getTurnNumber())
                .userMessage(turn.getUserMessage())
                .assistantMessage(turn.getAssistantMessage())
                .tokensUsed(turn.getTokensUsed())
                .status(turn.getStatus())
                .createdAt(turn.getCreatedAt())
                .refs(buildRefsFromContext(turn.getRetrievalContext()))
                .navigate(buildNavigateFromInfo(turn.getNavigateInfo()))
                .build();
    }

    private List<Map<String, Object>> buildRefsFromContext(String retrievalContextJson) {
        if (retrievalContextJson == null || retrievalContextJson.isBlank()) {
            return null;
        }
        try {
            JsonNode arr = objectMapper.readTree(retrievalContextJson);
            if (!arr.isArray()) return null;
            List<Map<String, Object>> refs = new ArrayList<>();
            int index = 1;
            for (JsonNode r : arr) {
                Map<String, Object> ref = new HashMap<>();
                String source = r.path("source").asText("");
                String id = r.path("id").asText("");
                String title = r.path("title").asText("");
                ref.put("index", index);
                ref.put("id", id);
                ref.put("type", source);
                ref.put("title", title);
                if ("POST".equals(source)) {
                    ref.put("url", "/post/" + id);
                } else {
                    ref.put("url", null);
                }
                refs.add(ref);
                index++;
                if (index > 10) break;
            }
            return refs.isEmpty() ? null : refs;
        } catch (Exception e) {
            log.warn("Failed to build refs from retrievalContext: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> buildNavigateFromInfo(String navigateInfoJson) {
        if (navigateInfoJson == null || navigateInfoJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(navigateInfoJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            log.warn("Failed to build navigate from navigateInfo: {}", e.getMessage());
            return null;
        }
    }
}
