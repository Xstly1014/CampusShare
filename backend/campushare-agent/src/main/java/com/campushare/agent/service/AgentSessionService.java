package com.campushare.agent.service;

import com.campushare.agent.dto.SessionCreateRequest;
import com.campushare.agent.dto.SessionResponse;
import com.campushare.agent.dto.TurnResponse;

import java.util.List;

public interface AgentSessionService {

    SessionResponse createSession(String userId, SessionCreateRequest request);

    SessionResponse getSession(String userId, String sessionId);

    List<SessionResponse> getUserSessions(String userId);

    void archiveSession(String userId, String sessionId);

    void deleteSession(String userId, String sessionId);

    List<TurnResponse> getSessionTurns(String userId, String sessionId);
}
