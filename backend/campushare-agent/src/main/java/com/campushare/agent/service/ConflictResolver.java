package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.mapper.UserMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictResolver {

    private final UserMemoryMapper userMemoryMapper;
    private final LongTermMemoryService longTermMemoryService;

    private static final double CONFLICT_SIMILARITY_THRESHOLD = 0.3;

    public void resolveOnInsert(UserMemory newMemory) {
        if (newMemory == null || newMemory.getUserId() == null
                || newMemory.getMemoryType() == null || newMemory.getMemoryKey() == null) {
            return;
        }

        List<UserMemory> existing = userMemoryMapper.selectList(
                new LambdaQueryWrapper<UserMemory>()
                        .eq(UserMemory::getUserId, newMemory.getUserId())
                        .eq(UserMemory::getMemoryKey, newMemory.getMemoryKey())
                        .ne(UserMemory::getId, newMemory.getId() != null ? newMemory.getId() : -1L)
        );

        if (existing.isEmpty()) {
            return;
        }

        for (UserMemory old : existing) {
            resolveConflict(old, newMemory);
        }
    }

    private void resolveConflict(UserMemory existing, UserMemory incoming) {
        String existingSource = existing.getSource();
        String incomingSource = incoming.getSource();
        boolean existingExplicit = "EXPLICIT".equals(existingSource);
        boolean incomingExplicit = "EXPLICIT".equals(incomingSource);

        if (existingExplicit && !incomingExplicit) {
            if (isValueConflicting(existing.getMemoryValue(), incoming.getMemoryValue())) {
                markConflicting(incoming, "Conflicts with EXPLICIT memory id=" + existing.getId());
            }
        } else if (!existingExplicit && incomingExplicit) {
            if (isValueConflicting(existing.getMemoryValue(), incoming.getMemoryValue())) {
                markConflicting(existing, "Conflicts with new EXPLICIT memory id=" + incoming.getId());
            }
        } else if (existingExplicit && incomingExplicit) {
            resolveExplicitConflict(existing, incoming);
        } else {
            resolveImplicitConflict(existing, incoming);
        }
    }

    private boolean isValueConflicting(String val1, String val2) {
        if (val1 == null || val2 == null) return true;
        String norm1 = normalizeValue(val1);
        String norm2 = normalizeValue(val2);
        if (norm1.equals(norm2)) return false;
        double sim = jaccardSimilarity(norm1, norm2);
        return sim < CONFLICT_SIMILARITY_THRESHOLD;
    }

    private String normalizeValue(String val) {
        if (val == null) return "";
        return val.toLowerCase().trim().replaceAll("[\\s,，。.!！？?、]+", "");
    }

    private double jaccardSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int match = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) match++;
        }
        int total = Math.max(a.length(), b.length());
        return total > 0 ? (double) match / total : 1.0;
    }

    private void markConflicting(UserMemory memory, String reason) {
        try {
            memory.setConflictFlag(1);
            memory.setConfidence(memory.getConfidence() != null
                    ? memory.getConfidence().multiply(BigDecimal.valueOf(0.5))
                    : BigDecimal.valueOf(0.5));
            userMemoryMapper.updateById(memory);
            longTermMemoryService.logHistory(memory, "CONFLICT_RESOLVED", reason);
            log.info("Memory conflict resolved: id={}, key={}, reason={}",
                    memory.getId(), memory.getMemoryKey(), reason);
        } catch (Exception e) {
            log.warn("Failed to mark conflicting memory: id={}, error={}", memory.getId(), e.getMessage());
        }
    }

    private void resolveExplicitConflict(UserMemory existing, UserMemory incoming) {
        LocalDateTime existingTime = existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt();
        LocalDateTime incomingTime = incoming.getUpdatedAt() != null ? incoming.getUpdatedAt() : incoming.getCreatedAt();

        if (incomingTime != null && existingTime != null && incomingTime.isAfter(existingTime)) {
            markConflicting(existing, "Replaced by newer EXPLICIT memory id=" + incoming.getId());
        } else {
            markConflicting(incoming, "Older EXPLICIT memory, keeping existing id=" + existing.getId());
        }
    }

    private void resolveImplicitConflict(UserMemory existing, UserMemory incoming) {
        BigDecimal existingConf = existing.getConfidence() != null ? existing.getConfidence() : BigDecimal.ZERO;
        BigDecimal incomingConf = incoming.getConfidence() != null ? incoming.getConfidence() : BigDecimal.ZERO;

        if (incomingConf.compareTo(existingConf) > 0) {
            existing.setConfidence(existingConf.multiply(BigDecimal.valueOf(0.8)));
            userMemoryMapper.updateById(existing);
            longTermMemoryService.logHistory(existing, "CONFLICT_RESOLVED",
                    "Downweighted by higher-confidence INFERRED memory id=" + incoming.getId());
        }
    }
}
