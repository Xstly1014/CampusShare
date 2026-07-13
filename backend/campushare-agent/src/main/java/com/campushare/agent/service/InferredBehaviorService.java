package com.campushare.agent.service;

import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.entity.UserMemoryEvidence;
import com.campushare.agent.mapper.UserMemoryEvidenceMapper;
import com.campushare.agent.mapper.UserMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class InferredBehaviorService {

    private final UserMemoryMapper userMemoryMapper;
    private final UserMemoryEvidenceMapper evidenceMapper;
    private final LongTermMemoryService longTermMemoryService;

    private static final Map<String, String> BEHAVIOR_KEY_MAP = Map.of(
            "search_posts", "top_search_category",
            "search_knowledge", "top_knowledge_topic",
            "navigate_to_page", "frequent_page",
            "download_resource", "preferred_resource_type"
    );

    @Transactional(rollbackFor = Exception.class)
    public UserMemory inferFromBehavior(String userId, String behaviorType,
                                        String behaviorValue, String sessionId) {
        String memoryKey = BEHAVIOR_KEY_MAP.getOrDefault(behaviorType, "behavior_" + behaviorType);

        UserMemory existing = userMemoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserMemory>()
                        .eq(UserMemory::getUserId, userId)
                        .eq(UserMemory::getMemoryType, "BEHAVIOR")
                        .eq(UserMemory::getMemoryKey, memoryKey)
                        .isNull(UserMemory::getDeletedAt)
        );

        if (existing != null) {
            BigDecimal newConfidence = existing.getConfidence() != null
                    ? existing.getConfidence().add(BigDecimal.valueOf(0.05))
                    : BigDecimal.valueOf(0.3);
            if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                newConfidence = BigDecimal.ONE;
            }

            boolean valueChanged = !behaviorValue.equals(existing.getMemoryValue());
            if (valueChanged) {
                existing.setMemoryValue(behaviorValue);
                existing.setVolatileFlag(existing.getVolatileFlag() != null
                        ? existing.getVolatileFlag() + 1 : 1);
            }

            existing.setConfidence(newConfidence);
            existing.setEvidenceCount(existing.getEvidenceCount() != null
                    ? existing.getEvidenceCount() + 1 : 1);
            userMemoryMapper.updateById(existing);

            logHistory(existing, "INFERRED_UPDATE", "behavior observed");
            recordEvidence(userId, memoryKey, behaviorType, behaviorValue, sessionId);

            return existing;
        } else {
            UserMemory memory = UserMemory.builder()
                    .userId(userId)
                    .memoryType("BEHAVIOR")
                    .memoryKey(memoryKey)
                    .memoryValue(behaviorValue)
                    .confidence(BigDecimal.valueOf(0.3))
                    .source("INFERRED")
                    .evidenceCount(1)
                    .conflictFlag(0)
                    .volatileFlag(0)
                    .build();
            userMemoryMapper.insert(memory);

            logHistory(memory, "INFERRED_CREATE", "new behavior pattern");
            recordEvidence(userId, memoryKey, behaviorType, behaviorValue, sessionId);

            return memory;
        }
    }

    private void recordEvidence(String userId, String memoryKey,
                                String behaviorType, String behaviorValue, String sessionId) {
        UserMemoryEvidence evidence = UserMemoryEvidence.builder()
                .userId(userId)
                .memoryKey(memoryKey)
                .evidenceType(behaviorType)
                .evidenceValue(behaviorValue)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            evidenceMapper.insert(evidence);
        } catch (Exception e) {
            log.warn("Failed to record memory evidence: userId={}, key={}", userId, memoryKey);
        }
    }

    private void logHistory(UserMemory memory, String action, String reason) {
        try {
            UserMemoryHistory history = com.campushare.agent.entity.UserMemoryHistory.builder()
                    .userId(memory.getUserId())
                    .memoryType(memory.getMemoryType())
                    .memoryKey(memory.getMemoryKey())
                    .memoryValue(memory.getMemoryValue())
                    .confidence(memory.getConfidence())
                    .source(memory.getSource())
                    .action(action)
                    .reason(reason)
                    .createdAt(LocalDateTime.now())
                    .build();
            evidenceMapper.getClass().getPackage().getName();
        } catch (Exception e) {
            log.warn("Failed to log memory history", e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public UserMemory recoverMemory(String userId, String memoryKey) {
        UserMemory deleted = userMemoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserMemory>()
                        .eq(UserMemory::getUserId, userId)
                        .eq(UserMemory::getMemoryKey, memoryKey)
                        .isNotNull(UserMemory::getDeletedAt)
        );

        if (deleted != null) {
            deleted.setDeletedAt(null);
            deleted.setConfidence(deleted.getConfidence() != null
                    ? deleted.getConfidence().add(BigDecimal.valueOf(0.2))
                    : BigDecimal.valueOf(0.5));
            if (deleted.getConfidence().compareTo(BigDecimal.ONE) > 0) {
                deleted.setConfidence(BigDecimal.ONE);
            }
            userMemoryMapper.updateById(deleted);

            log.info("Recovered memory: userId={}, key={}", userId, memoryKey);
            return deleted;
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public int physicalCleanup(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<UserMemory> deletedMemories = userMemoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserMemory>()
                        .isNotNull(UserMemory::getDeletedAt)
                        .lt(UserMemory::getDeletedAt, cutoff)
        );

        int deleted = 0;
        for (UserMemory memory : deletedMemories) {
            try {
                userMemoryMapper.deleteById(memory.getId());
                deleted++;
            } catch (Exception e) {
                log.warn("Failed to physically delete memory: id={}", memory.getId());
            }
        }

        log.info("Physical cleanup completed: deleted {} memories (older than {} days)",
                deleted, retentionDays);
        return deleted;
    }
}
