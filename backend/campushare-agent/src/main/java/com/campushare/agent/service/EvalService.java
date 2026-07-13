package com.campushare.agent.service;

import com.campushare.agent.entity.EvalResult;
import com.campushare.agent.entity.EvalTestCase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface EvalService {

    EvalTestCase createTestCase(EvalTestCase testCase);

    EvalTestCase updateTestCase(String id, EvalTestCase testCase);

    void deleteTestCase(String id);

    EvalTestCase getTestCase(String id);

    List<EvalTestCase> listTestCases(String category, Boolean isGolden, Integer priority);

    List<EvalTestCase> getGoldenCases();

    String runEval(List<String> testCaseIds, String userId);

    String runGoldenEval(String userId);

    List<EvalResult> getRunResults(String runId);

    List<EvalResult> getFailedResults(String runId);

    Map<String, Object> getRunSummary(String runId);

    Map<String, Object> getOverallStats(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> getTrendByDay(LocalDateTime startTime, LocalDateTime endTime);

    EvalResult evaluateSingle(EvalTestCase testCase, String userId);

    Map<String, Object> llmJudge(String userQuery, String expectedAnswer, String actualAnswer);
}