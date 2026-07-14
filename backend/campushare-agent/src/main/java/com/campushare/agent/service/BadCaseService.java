package com.campushare.agent.service;

import com.campushare.agent.entity.BadCase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface BadCaseService {

    BadCase recordBadCase(String sessionId, String turnId, String userId, String traceId,
                          String userQuery, String actualIntent, String actualAnswer,
                          String badCaseType, Integer severity);

    BadCase recordFromEvalFailure(String sessionId, String userId, String userQuery,
                                  String actualIntent, String actualAnswer, String expectedIntent,
                                  String expectedAnswer);

    void autoCollectBadCases();

    List<BadCase> findByStatus(String status);

    List<BadCase> findByUserId(String userId);

    List<BadCase> findNewCases(int limit);

    BadCase updateStatus(String id, String status, String note);

    BadCase assign(String id, String assignee);

    BadCase addNote(String id, String note);

    void convertToTestCase(String id);

    List<BadCase> getStatsByType(LocalDateTime startTime);

    List<BadCase> getStatsBySeverity(LocalDateTime startTime);

    Map<String, Object> getOverallStats();

    void delete(String id);

    BadCase getById(String id);
}