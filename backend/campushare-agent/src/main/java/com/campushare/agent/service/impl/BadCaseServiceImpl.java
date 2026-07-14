package com.campushare.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campushare.agent.entity.BadCase;
import com.campushare.agent.entity.EvalTestCase;
import com.campushare.agent.mapper.AgentTurnMapper;
import com.campushare.agent.mapper.BadCaseMapper;
import com.campushare.agent.mapper.EvalTestCaseMapper;
import com.campushare.agent.service.BadCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadCaseServiceImpl extends ServiceImpl<BadCaseMapper, BadCase> implements BadCaseService {

    private final BadCaseMapper badCaseMapper;
    private final AgentTurnMapper turnMapper;
    private final EvalTestCaseMapper evalTestCaseMapper;

    private static final double SIMILARITY_THRESHOLD = 0.8;

    @Override
    @Transactional
    public BadCase recordBadCase(String sessionId, String turnId, String userId, String traceId,
                                  String userQuery, String actualIntent, String actualAnswer,
                                  String badCaseType, Integer severity) {
        BadCase existing = findSimilarCase(userQuery);
        if (existing != null) {
            existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
            existing.setLastSeenAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            if (severity != null && severity > existing.getSeverity()) {
                existing.setSeverity(severity);
            }
            badCaseMapper.updateById(existing);
            log.debug("Updated existing bad case: id={}, occurrenceCount={}", existing.getId(), existing.getOccurrenceCount());
            return existing;
        }

        BadCase badCase = BadCase.builder()
                .sessionId(sessionId)
                .turnId(turnId)
                .userId(userId)
                .traceId(traceId)
                .userQuery(truncate(userQuery, 2000))
                .actualIntent(actualIntent)
                .actualAnswer(truncate(actualAnswer, 5000))
                .badCaseType(badCaseType)
                .severity(severity != null ? severity : 1)
                .firstSeenAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();
        badCaseMapper.insert(badCase);
        log.info("Created new bad case: id={}, type={}, severity={}", badCase.getId(), badCaseType, severity);
        return badCase;
    }

    @Override
    @Transactional
    public BadCase recordFromEvalFailure(String sessionId, String userId, String userQuery,
                                          String actualIntent, String actualAnswer, String expectedIntent,
                                          String expectedAnswer) {
        String badCaseType = inferBadCaseType(actualIntent, expectedIntent);
        Integer severity = inferSeverity(actualIntent, expectedIntent);

        BadCase badCase = BadCase.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userQuery(truncate(userQuery, 2000))
                .actualIntent(actualIntent)
                .actualAnswer(truncate(actualAnswer, 5000))
                .expectedIntent(expectedIntent)
                .expectedAnswer(truncate(expectedAnswer, 5000))
                .badCaseType(badCaseType)
                .severity(severity)
                .firstSeenAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .build();
        badCaseMapper.insert(badCase);
        log.info("Created bad case from eval failure: id={}, type={}", badCase.getId(), badCaseType);
        return badCase;
    }

    @Override
    public void autoCollectBadCases() {
        log.info("Starting bad case auto collection...");

        List<BadCase> errorCases = badCaseMapper.findFromErrorTurns(50);
        for (BadCase badCase : errorCases) {
            try {
                recordBadCase(badCase.getSessionId(), badCase.getTurnId(), badCase.getUserId(),
                        badCase.getTraceId(), badCase.getUserQuery(), badCase.getActualIntent(),
                        badCase.getActualAnswer(), "SYSTEM_ERROR", 3);
            } catch (Exception e) {
                log.warn("Failed to record error bad case: {}", e.getMessage());
            }
        }

        List<BadCase> dislikeCases = badCaseMapper.findFromDislikedTurns(50);
        for (BadCase badCase : dislikeCases) {
            try {
                recordBadCase(badCase.getSessionId(), badCase.getTurnId(), badCase.getUserId(),
                        badCase.getTraceId(), badCase.getUserQuery(), badCase.getActualIntent(),
                        badCase.getActualAnswer(), "USER_DISLIKE", 2);
            } catch (Exception e) {
                log.warn("Failed to record dislike bad case: {}", e.getMessage());
            }
        }

        log.info("Bad case auto collection complete");
    }

    @Override
    public List<BadCase> findByStatus(String status) {
        return badCaseMapper.findByStatus(status);
    }

    @Override
    public List<BadCase> findByUserId(String userId) {
        return badCaseMapper.findByUserId(userId);
    }

    @Override
    public List<BadCase> findNewCases(int limit) {
        LambdaQueryWrapper<BadCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BadCase::getStatus, "NEW")
               .orderByDesc(BadCase::getCreatedAt)
               .last("LIMIT " + limit);
        return badCaseMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public BadCase updateStatus(String id, String status, String note) {
        BadCase badCase = badCaseMapper.selectById(id);
        if (badCase == null) {
            throw new RuntimeException("Bad case not found: " + id);
        }
        badCase.setStatus(status);
        if (note != null && !note.isEmpty()) {
            badCase.setNote(note);
        }
        badCase.setUpdatedAt(LocalDateTime.now());
        badCaseMapper.updateById(badCase);
        log.info("Updated bad case status: id={}, status={}", id, status);
        return badCase;
    }

    @Override
    @Transactional
    public BadCase assign(String id, String assignee) {
        BadCase badCase = badCaseMapper.selectById(id);
        if (badCase == null) {
            throw new RuntimeException("Bad case not found: " + id);
        }
        badCase.setAssignee(assignee);
        badCase.setUpdatedAt(LocalDateTime.now());
        badCaseMapper.updateById(badCase);
        return badCase;
    }

    @Override
    @Transactional
    public BadCase addNote(String id, String note) {
        BadCase badCase = badCaseMapper.selectById(id);
        if (badCase == null) {
            throw new RuntimeException("Bad case not found: " + id);
        }
        String existingNote = badCase.getNote();
        badCase.setNote(existingNote != null ? existingNote + "\n" + note : note);
        badCase.setUpdatedAt(LocalDateTime.now());
        badCaseMapper.updateById(badCase);
        return badCase;
    }

    @Override
    @Transactional
    public void convertToTestCase(String id) {
        BadCase badCase = badCaseMapper.selectById(id);
        if (badCase == null) {
            throw new RuntimeException("Bad case not found: " + id);
        }

        EvalTestCase testCase = EvalTestCase.builder()
                .name("Auto-generated from bad case: " + badCase.getId())
                .description("Bad case converted to test case. Type: " + badCase.getBadCaseType())
                .category(badCase.getBadCaseType())
                .userQuery(badCase.getUserQuery())
                .expectedIntent(badCase.getExpectedIntent() != null ? badCase.getExpectedIntent() : badCase.getActualIntent())
                .expectedAnswer(badCase.getExpectedAnswer())
                .isGolden(true)
                .priority(badCase.getSeverity())
                .tags(badCase.getTags())
                .build();
        evalTestCaseMapper.insert(testCase);

        badCase.setConvertedToTestCase(true);
        badCase.setStatus("RESOLVED");
        badCase.setUpdatedAt(LocalDateTime.now());
        badCaseMapper.updateById(badCase);

        log.info("Converted bad case to test case: badCaseId={}, testCaseId={}", id, testCase.getId());
    }

    @Override
    public List<BadCase> getStatsByType(LocalDateTime startTime) {
        return badCaseMapper.findByTimeRange(startTime);
    }

    @Override
    public List<BadCase> getStatsBySeverity(LocalDateTime startTime) {
        return badCaseMapper.findByTimeRange(startTime);
    }

    @Override
    public Map<String, Object> getOverallStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("newCases", badCaseMapper.countNewCases());
        stats.put("unconvertedSince24h", badCaseMapper.countUnconvertedSince(LocalDateTime.now().minusHours(24)));
        stats.put("countByStatus", badCaseMapper.countByStatus());
        stats.put("countByTypeLast7d", badCaseMapper.countByType(LocalDateTime.now().minusDays(7)));
        stats.put("countBySeverityLast7d", badCaseMapper.countBySeverity(LocalDateTime.now().minusDays(7)));
        return stats;
    }

    @Override
    @Transactional
    public void delete(String id) {
        badCaseMapper.deleteById(id);
        log.info("Deleted bad case: id={}", id);
    }

    @Override
    public BadCase getById(String id) {
        return badCaseMapper.selectById(id);
    }

    private BadCase findSimilarCase(String userQuery) {
        List<BadCase> recentCases = badCaseMapper.findByTimeRange(LocalDateTime.now().minusDays(7));
        for (BadCase existing : recentCases) {
            if (existing.getUserQuery() != null && cosineSimilarity(userQuery, existing.getUserQuery()) >= SIMILARITY_THRESHOLD) {
                return existing;
            }
        }
        return null;
    }

    private double cosineSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        Map<String, Integer> tokensA = tokenize(a);
        Map<String, Integer> tokensB = tokenize(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0;

        int dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (Map.Entry<String, Integer> entry : tokensA.entrySet()) {
            String key = entry.getKey();
            int countA = entry.getValue();
            int countB = tokensB.getOrDefault(key, 0);
            dotProduct += countA * countB;
            normA += countA * countA;
        }
        for (int countB : tokensB.values()) {
            normB += countB * countB;
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
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

    private String inferBadCaseType(String actualIntent, String expectedIntent) {
        if (actualIntent == null || expectedIntent == null) {
            return "UNKNOWN";
        }
        if (!actualIntent.equals(expectedIntent)) {
            return "INTENT_MISMATCH";
        }
        return "ANSWER_QUALITY";
    }

    private Integer inferSeverity(String actualIntent, String expectedIntent) {
        if (actualIntent == null || expectedIntent == null) {
            return 2;
        }
        if (!actualIntent.equals(expectedIntent)) {
            return 3;
        }
        return 1;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}