package com.campushare.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.entity.EvalResult;
import com.campushare.agent.entity.EvalTestCase;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.mapper.EvalResultMapper;
import com.campushare.agent.mapper.EvalTestCaseMapper;
import com.campushare.agent.service.AgentChatService;
import com.campushare.agent.service.EvalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvalServiceImpl extends ServiceImpl<EvalTestCaseMapper, EvalTestCase> implements EvalService {

    private final EvalTestCaseMapper testCaseMapper;
    private final EvalResultMapper resultMapper;
    private final AgentChatService chatService;
    private final ObjectMapper objectMapper;
    private final DeepSeekClient deepSeekClient;

    @Value("${app.llm.deepseek.model:deepseek-v4-flash}")
    private String modelName;

    @Value("${app.eval.llm-judge-enabled:true}")
    private boolean llmJudgeEnabled;

    private final Map<String, String> currentRunIds = new ConcurrentHashMap<>();

    private static final String LLM_JUDGE_PROMPT = """
            你是一个专业的AI评估专家。请根据以下用户查询、预期答案和实际答案，对实际答案的质量进行评分。

            评分标准：
            - 相关性：答案是否与用户查询相关（0-30分）
            - 准确性：答案是否正确、事实准确（0-30分）
            - 完整性：答案是否完整覆盖了预期要点（0-20分）
            - 表达质量：语言表达是否清晰、逻辑是否通顺（0-20分）

            总分 = 相关性 + 准确性 + 完整性 + 表达质量

            用户查询：{userQuery}
            预期答案：{expectedAnswer}
            实际答案：{actualAnswer}

            请按照以下JSON格式输出评分结果：
            {
                "score": 总分(0-100),
                "reason": "评分理由，简要说明各维度得分依据",
                "dimensions": {
                    "relevance": 相关性得分,
                    "accuracy": 准确性得分,
                    "completeness": 完整性得分,
                    "expression": 表达质量得分
                }
            }
            """.trim();

    @Override
    @Transactional
    public EvalTestCase createTestCase(EvalTestCase testCase) {
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.insert(testCase);
        log.info("Created eval test case: id={}, name={}", testCase.getId(), testCase.getName());
        return testCase;
    }

    @Override
    @Transactional
    public EvalTestCase updateTestCase(String id, EvalTestCase testCase) {
        EvalTestCase existing = testCaseMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Test case not found: " + id);
        }
        testCase.setId(id);
        testCase.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(testCase);
        log.info("Updated eval test case: id={}", id);
        return testCase;
    }

    @Override
    @Transactional
    public void deleteTestCase(String id) {
        testCaseMapper.deleteById(id);
        log.info("Deleted eval test case: id={}", id);
    }

    @Override
    public EvalTestCase getTestCase(String id) {
        return testCaseMapper.selectById(id);
    }

    @Override
    public List<EvalTestCase> listTestCases(String category, Boolean isGolden, Integer priority) {
        LambdaQueryWrapper<EvalTestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(category != null, EvalTestCase::getCategory, category);
        wrapper.eq(isGolden != null, EvalTestCase::getIsGolden, isGolden);
        wrapper.eq(priority != null, EvalTestCase::getPriority, priority);
        wrapper.isNull(EvalTestCase::getDeletedAt);
        wrapper.orderByDesc(EvalTestCase::getPriority).orderByAsc(EvalTestCase::getName);
        return testCaseMapper.selectList(wrapper);
    }

    @Override
    public List<EvalTestCase> getGoldenCases() {
        return testCaseMapper.findGoldenCases();
    }

    @Override
    public String runEval(List<String> testCaseIds, String userId) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        currentRunIds.put(runId, userId);

        List<EvalTestCase> testCases = testCaseMapper.selectBatchIds(testCaseIds);
        log.info("Starting eval run: runId={}, testCaseCount={}", runId, testCases.size());

        for (EvalTestCase testCase : testCases) {
            try {
                EvalResult result = evaluateSingle(testCase, userId);
                result.setRunId(runId);
                resultMapper.insert(result);
            } catch (Exception e) {
                log.error("Failed to evaluate test case: id={}, name={}", testCase.getId(), testCase.getName(), e);
                EvalResult errorResult = EvalResult.builder()
                        .testCaseId(testCase.getId())
                        .testCaseName(testCase.getName())
                        .runId(runId)
                        .passed(false)
                        .overallScore(0)
                        .createdAt(LocalDateTime.now())
                        .build();
                resultMapper.insert(errorResult);
            }
        }

        currentRunIds.remove(runId);
        log.info("Completed eval run: runId={}", runId);
        return runId;
    }

    @Override
    public String runGoldenEval(String userId) {
        List<EvalTestCase> goldenCases = getGoldenCases();
        List<String> ids = goldenCases.stream().map(EvalTestCase::getId).collect(Collectors.toList());
        return runEval(ids, userId);
    }

    @Override
    public List<EvalResult> getRunResults(String runId) {
        return resultMapper.findByRunId(runId);
    }

    @Override
    public List<EvalResult> getFailedResults(String runId) {
        return resultMapper.findFailedByRunId(runId);
    }

    @Override
    public Map<String, Object> getRunSummary(String runId) {
        return resultMapper.getRunSummary(runId);
    }

    @Override
    public Map<String, Object> getOverallStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> categoryStats = resultMapper.getCategoryStats(startTime, endTime);
        stats.put("categoryStats", categoryStats);
        return stats;
    }

    @Override
    public List<Map<String, Object>> getTrendByDay(LocalDateTime startTime, LocalDateTime endTime) {
        return resultMapper.getTrendByDay(startTime, endTime);
    }

    @Override
    public EvalResult evaluateSingle(EvalTestCase testCase, String userId) {
        long startTime = System.currentTimeMillis();

        ChatRequest request = new ChatRequest();
        request.setMessage(testCase.getUserQuery());

        StringBuilder actualAnswer = new StringBuilder();
        String actualIntent = null;
        String actualSubIntent = null;
        String actualNavigateRoute = null;
        List<Map<String, Object>> actualRefs = new ArrayList<>();

        try {
            List<?> events = chatService.chat(userId, request).collectList().block();
            if (events != null && !events.isEmpty()) {
                for (Object event : events) {
                    if (event instanceof com.campushare.agent.service.AgentChatService.ChatEvent chatEvent) {
                        if ("delta".equals(chatEvent.type())) {
                            actualAnswer.append(chatEvent.data());
                        } else if ("refs".equals(chatEvent.type())) {
                            try {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> refs = objectMapper.readValue(chatEvent.data(), List.class);
                                actualRefs.addAll(refs);
                            } catch (Exception e) {
                                log.debug("Failed to parse refs event: {}", chatEvent.data());
                            }
                        } else if ("navigate".equals(chatEvent.type())) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, String> nav = objectMapper.readValue(chatEvent.data(), Map.class);
                                if (nav.containsKey("route")) {
                                    actualNavigateRoute = nav.get("route");
                                }
                            } catch (Exception e) {
                                log.debug("Failed to parse navigate event: {}", chatEvent.data());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Eval chat failed for test case: {}", testCase.getId(), e);
        }

        long responseTime = System.currentTimeMillis() - startTime;

        boolean intentMatch = Objects.equals(actualIntent, testCase.getExpectedIntent());
        boolean subIntentMatch = Objects.equals(actualSubIntent, testCase.getExpectedSubIntent());
        boolean answerMatch = matchesAnswer(actualAnswer.toString(), testCase.getExpectedAnswer());
        boolean refsMatch = matchesRefs(actualRefs, testCase.getExpectedRefs());
        boolean navigateMatch = Objects.equals(actualNavigateRoute, testCase.getExpectedNavigateRoute());

        int overallScore = calculateOverallScore(intentMatch, subIntentMatch, answerMatch, refsMatch, navigateMatch);
        boolean passed = overallScore >= 80;

        Integer llmJudgeScore = null;
        String llmJudgeReason = null;
        if (llmJudgeEnabled && testCase.getExpectedAnswer() != null && !testCase.getExpectedAnswer().isEmpty()
                && actualAnswer.length() > 0) {
            Map<String, Object> judgeResult = llmJudge(testCase.getUserQuery(), testCase.getExpectedAnswer(), actualAnswer.toString());
            if (judgeResult != null) {
                llmJudgeScore = (Integer) judgeResult.get("score");
                llmJudgeReason = (String) judgeResult.get("reason");
            }
        }

        return EvalResult.builder()
                .testCaseId(testCase.getId())
                .testCaseName(testCase.getName())
                .actualIntent(actualIntent)
                .actualSubIntent(actualSubIntent)
                .actualAnswer(actualAnswer.toString())
                .actualRefs(serializeRefs(actualRefs))
                .actualNavigateRoute(actualNavigateRoute)
                .intentMatch(intentMatch)
                .subIntentMatch(subIntentMatch)
                .answerMatch(answerMatch)
                .refsMatch(refsMatch)
                .navigateMatch(navigateMatch)
                .llmJudgeScore(llmJudgeScore)
                .llmJudgeReason(llmJudgeReason)
                .overallScore(overallScore)
                .passed(passed)
                .modelName(modelName)
                .responseTimeMs((int) responseTime)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public Map<String, Object> llmJudge(String userQuery, String expectedAnswer, String actualAnswer) {
        try {
            String prompt = LLM_JUDGE_PROMPT
                    .replace("{userQuery}", userQuery != null ? userQuery : "")
                    .replace("{expectedAnswer}", expectedAnswer != null ? expectedAnswer : "")
                    .replace("{actualAnswer}", actualAnswer != null ? actualAnswer : "");

            List<DeepSeekRequest.Message> messages = List.of(
                    DeepSeekRequest.Message.builder()
                            .role("system")
                            .content("你是一个专业的AI评估专家，只输出JSON格式的评分结果。")
                            .build(),
                    DeepSeekRequest.Message.builder()
                            .role("user")
                            .content(prompt)
                            .build()
            );

            DeepSeekResponse response = deepSeekClient.chatCompletion(messages).block();
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String content = response.getChoices().get(0).getMessage().getContent();
                if (content != null && !content.isEmpty()) {
                    String json = content.trim();
                    if (json.startsWith("```")) {
                        int start = json.indexOf('\n');
                        int end = json.lastIndexOf("```");
                        if (start > 0 && end > start) {
                            json = json.substring(start + 1, end).trim();
                        }
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = objectMapper.readValue(json, Map.class);
                        return result;
                    } catch (Exception e) {
                        log.warn("Failed to parse LLM judge result: {}", content, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM judge failed", e);
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("score", 70);
        fallback.put("reason", "LLM judge unavailable, using default score");
        return fallback;
    }

    private boolean matchesAnswer(String actual, String expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return actual.toLowerCase().contains(expected.toLowerCase())
                || cosineSimilarity(actual, expected) >= 0.7;
    }

    private boolean matchesRefs(List<Map<String, Object>> actual, String expectedJson) {
        if (expectedJson == null || expectedJson.isEmpty()) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        try {
            List<Map<String, Object>> expected = objectMapper.readValue(expectedJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            if (expected == null || expected.isEmpty()) {
                return true;
            }
            Set<String> expectedIds = expected.stream()
                    .map(e -> String.valueOf(e.get("id")))
                    .collect(Collectors.toSet());
            Set<String> actualIds = actual.stream()
                    .map(a -> String.valueOf(a.get("id")))
                    .collect(Collectors.toSet());
            return expectedIds.stream().anyMatch(actualIds::contains);
        } catch (Exception e) {
            log.warn("Failed to parse expected refs: {}", e.getMessage());
            return false;
        }
    }

    private int calculateOverallScore(boolean intentMatch, boolean subIntentMatch,
                                      boolean answerMatch, boolean refsMatch, boolean navigateMatch) {
        int score = 0;
        if (intentMatch) score += 25;
        if (subIntentMatch) score += 15;
        if (answerMatch) score += 35;
        if (refsMatch) score += 15;
        if (navigateMatch) score += 10;
        return score;
    }

    private double cosineSimilarity(String s1, String s2) {
        Map<String, Integer> v1 = tokenize(s1);
        Map<String, Integer> v2 = tokenize(s2);
        Set<String> common = new HashSet<>(v1.keySet());
        common.retainAll(v2.keySet());
        double dotProduct = 0, mag1 = 0, mag2 = 0;
        for (String key : common) {
            dotProduct += v1.get(key) * v2.get(key);
        }
        for (int val : v1.values()) mag1 += val * val;
        for (int val : v2.values()) mag2 += val * val;
        return dotProduct / (Math.sqrt(mag1) * Math.sqrt(mag2));
    }

    private Map<String, Integer> tokenize(String s) {
        Map<String, Integer> tokens = new HashMap<>();
        for (String word : s.toLowerCase().split("\\W+")) {
            if (word.length() > 2) {
                tokens.merge(word, 1, Integer::sum);
            }
        }
        return tokens;
    }

    private String serializeRefs(List<Map<String, Object>> refs) {
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            return "[]";
        }
    }
}