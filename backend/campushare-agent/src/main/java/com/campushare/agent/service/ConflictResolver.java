package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.entity.UserMemoryHistory;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.mapper.UserMemoryHistoryMapper;
import com.campushare.agent.mapper.UserMemoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictResolver {

    private final UserMemoryMapper userMemoryMapper;
    private final UserMemoryHistoryMapper historyMapper;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    private static final double CONFLICT_SIMILARITY_THRESHOLD = 0.3;

    @Value("${app.memory-conflict.llm-arbitration-enabled:true}")
    private boolean llmArbitrationEnabled;

    private static final String CONFLICT_ARBITRATION_PROMPT = """
            你是一个记忆冲突仲裁专家。请分析以下两个用户记忆是否冲突，如果冲突，请决定保留哪个。

            现有记忆:
            - 类型: %s
            - 键: %s
            - 值: %s
            - 来源: %s
            - 置信度: %s
            - 更新时间: %s

            新记忆:
            - 类型: %s
            - 键: %s
            - 值: %s
            - 来源: %s
            - 置信度: %s
            - 更新时间: %s

            请输出JSON格式的仲裁结果:
            {
                "isConflict": true/false,
                "keepExisting": true/false,
                "reason": "简要说明原因"
            }

            规则:
            1. EXPLICIT(用户明确声明)优先于INFERRED(行为推断)
            2. 相同来源时，置信度高的优先
            3. 相同来源和置信度时，较新的优先
            4. 如果内容语义相同或高度相似，则不算冲突
            """.trim();

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
        if (llmArbitrationEnabled) {
            try {
                Map<String, Object> arbitrationResult = arbitrateWithLlm(existing, incoming);
                boolean isConflict = arbitrationResult.get("isConflict") != null
                        && (Boolean) arbitrationResult.get("isConflict");
                
                if (isConflict) {
                    boolean keepExisting = arbitrationResult.get("keepExisting") != null
                            && (Boolean) arbitrationResult.get("keepExisting");
                    String reason = (String) arbitrationResult.get("reason");
                    
                    if (keepExisting) {
                        markConflicting(incoming, "LLM arbitration: keep existing, reason=" + reason);
                    } else {
                        markConflicting(existing, "LLM arbitration: keep incoming, reason=" + reason);
                    }
                    return;
                }
            } catch (Exception e) {
                log.warn("LLM arbitration failed, falling back to rule-based resolution: userId={}, key={}",
                        existing.getUserId(), existing.getMemoryKey(), e);
            }
        }

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

    private Map<String, Object> arbitrateWithLlm(UserMemory existing, UserMemory incoming) {
        String prompt = String.format(CONFLICT_ARBITRATION_PROMPT,
                existing.getMemoryType(), existing.getMemoryKey(),
                existing.getMemoryValue(), existing.getSource(),
                existing.getConfidence(), existing.getUpdatedAt(),
                incoming.getMemoryType(), incoming.getMemoryKey(),
                incoming.getMemoryValue(), incoming.getSource(),
                incoming.getConfidence(), incoming.getUpdatedAt());

        try {
            List<DeepSeekRequest.Message> messages = List.of(
                    DeepSeekRequest.Message.builder()
                            .role("system")
                            .content("你是一个专业的记忆冲突仲裁专家，只输出JSON格式的仲裁结果。")
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
                        log.warn("Failed to parse LLM arbitration result: {}", content, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM arbitration call failed", e);
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("isConflict", true);
        fallback.put("keepExisting", true);
        fallback.put("reason", "LLM unavailable, fallback to keep existing");
        return fallback;
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
            logHistory(memory, "CONFLICT_RESOLVED", reason);
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
            logHistory(existing, "CONFLICT_RESOLVED",
                    "Downweighted by higher-confidence INFERRED memory id=" + incoming.getId());
        }
    }

    private void logHistory(UserMemory memory, String action, String reason) {
        try {
            UserMemoryHistory h = UserMemoryHistory.builder()
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
            historyMapper.insert(h);
        } catch (Exception e) {
            log.warn("Failed to write conflict history: userId={}, key={}, error={}",
                    memory.getUserId(), memory.getMemoryKey(), e.getMessage());
        }
    }
}
