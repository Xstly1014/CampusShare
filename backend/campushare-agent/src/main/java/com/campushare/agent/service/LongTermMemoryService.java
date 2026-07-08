package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.entity.UserMemory;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.campushare.agent.mapper.UserMemoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期用户画像记忆服务（ADR-059~063）。
 *
 * 跨会话沉淀用户偏好与行为模式，让 Agent 在新会话首轮就能个性化。
 *
 * 记忆分类（四象限）：
 *   - EXPLICIT PREFERENCE/FACT — 用户明说，不衰减（如"我喜欢 PDF"）
 *   - INFERRED BEHAVIOR — 行为推断，周衰减 0.1（如主要访问分类）
 *   - TASK — 当前任务，4 周未更新删除
 *
 * 核心方法：
 *   - loadUserProfile：装载 Top-K 记忆为画像文本（L1 层）
 *   - extractMemories：会话归档时 LLM 抽取显式偏好
 *   - upsertMemory：UPSERT 记忆（已存在则 confidence 累加）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final UserMemoryMapper userMemoryMapper;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    /** 装载 Top-K 条记忆 */
    private static final int TOP_K = 5;

    /** 画像文本最大长度（≈200 token） */
    private static final int MAX_PROFILE_LENGTH = 300;

    /** 单条记忆摘要最大长度 */
    private static final int MAX_MEMORY_SUMMARY_LENGTH = 30;

    /** 记忆类型优先级（PREFERENCE > FACT > BEHAVIOR > TASK） */
    private static final Map<String, Integer> TYPE_PRIORITY = Map.of(
            "PREFERENCE", 1,
            "FACT", 2,
            "BEHAVIOR", 3,
            "TASK", 4
    );

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            你是用户画像抽取器。请从以下对话摘要中抽取用户的显式偏好与事实声明。

            要求：
            1. 只抽取用户明确声明的偏好/事实，不推断。
            2. 每项含 type（PREFERENCE/FACT/TASK）、key（英文蛇形命名）、value（值）、evidence_quote（原文引用）。
            3. 若没有显式声明，输出空数组 []。

            输出 JSON 数组（不要输出其他内容）：
            [{"type":"PREFERENCE","key":"preferred_format","value":"PDF","evidence_quote":"我比较喜欢 PDF"}]
            """;

    /**
     * 装载用户画像（L1 层，ADR-059 5.1 装载策略）。
     *
     * 装载顺序：
     *   1. 强相关：memory_key 与当前意图/槽位匹配
     *   2. 高置信 + 近期使用：confidence ≥0.7 且 last_used_at 在 7 天内
     *   3. 偏好类优先于行为类：PREFERENCE > FACT > BEHAVIOR > TASK
     *
     * @param userId       用户ID
     * @param intentResult 当前意图（用于强相关匹配）
     * @param slots        当前槽位（用于强相关匹配）
     * @return 画像文本（≤300 字），null 表示无记忆
     */
    public String loadUserProfile(String userId, IntentResult intentResult, Map<String, String> slots) {
        try {
            List<UserMemory> allMemories = userMemoryMapper.selectList(
                    new LambdaQueryWrapper<UserMemory>()
                            .eq(UserMemory::getUserId, userId)
                            .orderByDesc(UserMemory::getConfidence)
                            .orderByDesc(UserMemory::getUpdatedAt)
            );

            if (allMemories.isEmpty()) {
                return null;
            }

            // 按相关性 + 优先级排序
            List<UserMemory> ranked = rankMemories(allMemories, intentResult, slots);

            // 取 Top-K
            List<UserMemory> topK = ranked.stream().limit(TOP_K).collect(Collectors.toList());

            // 格式化为画像文本
            String profile = formatProfile(topK);

            // 更新 last_used_at（异步，不阻塞主流程）
            touchMemories(topK);

            log.debug("Loaded user profile: userId={}, memories={}, profileLen={}",
                    userId, topK.size(), profile != null ? profile.length() : 0);
            return profile;
        } catch (Exception e) {
            log.warn("Failed to load user profile: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 对记忆按相关性 + 优先级排序。
     *
     * 排序规则（优先级从高到低）：
     *   1. 强相关（memory_key 或 memory_value 匹配意图/槽位）
     *   2. 高置信（confidence ≥0.7）
     *   3. 近期使用（last_used_at 在 7 天内）
     *   4. 类型优先级（PREFERENCE > FACT > BEHAVIOR > TASK）
     */
    private List<UserMemory> rankMemories(List<UserMemory> memories,
            IntentResult intentResult, Map<String, String> slots) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        String intentStr = intentResult != null ? intentResult.getIntent().name() : "";
        String subIntentStr = intentResult != null && intentResult.getSubIntent() != null
                ? intentResult.getSubIntent() : "";

        return memories.stream()
                .sorted(Comparator
                        // 1. 强相关优先
                        .comparing((UserMemory m) -> isRelevant(m, intentStr, subIntentStr, slots) ? 0 : 1)
                        // 2. 高置信优先
                        .thenComparing(m -> m.getConfidence() != null
                                && m.getConfidence().compareTo(BigDecimal.valueOf(0.7)) >= 0 ? 0 : 1)
                        // 3. 近期使用优先
                        .thenComparing(m -> m.getLastUsedAt() != null
                                && m.getLastUsedAt().isAfter(sevenDaysAgo) ? 0 : 1)
                        // 4. 类型优先级
                        .thenComparing(m -> TYPE_PRIORITY.getOrDefault(m.getMemoryType(), 5))
                        // 5. 置信度降序
                        .thenComparing(m -> m.getConfidence() != null
                                ? m.getConfidence().negate() : BigDecimal.ZERO)
                )
                .collect(Collectors.toList());
    }

    /**
     * 判断记忆是否与当前意图/槽位强相关。
     */
    private boolean isRelevant(UserMemory m, String intent, String subIntent, Map<String, String> slots) {
        String key = m.getMemoryKey() != null ? m.getMemoryKey().toLowerCase() : "";
        String value = m.getMemoryValue() != null ? m.getMemoryValue().toLowerCase() : "";

        // 意图匹配
        if (intent != null && !intent.isEmpty()) {
            if (key.contains(intent.toLowerCase()) || value.contains(intent.toLowerCase())) {
                return true;
            }
        }
        // 子意图匹配
        if (subIntent != null && !subIntent.isEmpty()) {
            if (key.contains(subIntent.toLowerCase()) || value.contains(subIntent.toLowerCase())) {
                return true;
            }
        }
        // 槽位匹配
        if (slots != null) {
            for (Map.Entry<String, String> entry : slots.entrySet()) {
                if (entry.getValue() != null) {
                    String slotValue = entry.getValue().toLowerCase();
                    if (key.contains(slotValue) || value.contains(slotValue)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 格式化记忆为画像文本（ADR-059 5.2 装载格式）。
     *
     * 格式：
     *   [用户画像]
     *   - 偏好格式: PDF（置信 1.0，用户明确声明）
     *   - 专业: 计算机科学（置信 1.0）
     */
    private String formatProfile(List<UserMemory> memories) {
        if (memories.isEmpty()) return null;

        StringBuilder sb = new StringBuilder("[用户画像]\n");
        for (UserMemory m : memories) {
            String label = formatMemoryLabel(m.getMemoryKey());
            String value = truncate(m.getMemoryValue(), MAX_MEMORY_SUMMARY_LENGTH);
            String source = "EXPLICIT".equals(m.getSource()) ? "，用户明确声明" : "，行为推断";
            String confidence = m.getConfidence() != null
                    ? String.format("%.1f", m.getConfidence()) : "1.0";

            sb.append("- ").append(label).append(": ").append(value)
              .append("（置信 ").append(confidence).append(source).append("）\n");

            // 总长超限则停止
            if (sb.length() > MAX_PROFILE_LENGTH) {
                break;
            }
        }
        return sb.toString().trim();
    }

    /**
     * 将 memory_key 转为可读标签（如 preferred_format → 偏好格式）。
     */
    private String formatMemoryLabel(String key) {
        if (key == null) return "未知";
        return switch (key) {
            case "preferred_format" -> "偏好格式";
            case "major" -> "专业";
            case "top_category" -> "主要兴趣分类";
            case "current_task" -> "最近任务";
            case "preferred_language" -> "偏好语言";
            default -> key;
        };
    }

    /**
     * 更新记忆的 last_used_at（被装载入上下文后调用，ADR-063）。
     */
    private void touchMemories(List<UserMemory> memories) {
        LocalDateTime now = LocalDateTime.now();
        for (UserMemory m : memories) {
            try {
                m.setLastUsedAt(now);
                userMemoryMapper.updateById(m);
            } catch (Exception e) {
                log.debug("Failed to touch memory: id={}, error={}", m.getId(), e.getMessage());
            }
        }
    }

    /**
     * 从会话摘要抽取显式记忆（会话归档时调用，ADR-059 4.1 显式抽取）。
     *
     * @param sessionId       会话ID
     * @param userId          用户ID
     * @param rollingSummary  会话滚动摘要
     * @return 抽取的记忆列表（空列表表示无显式声明或失败）
     */
    public List<UserMemory> extractMemories(String sessionId, String userId, String rollingSummary) {
        if (rollingSummary == null || rollingSummary.isBlank()) {
            return Collections.emptyList();
        }

        try {
            DeepSeekResponse response = callExtractionLLM(rollingSummary);
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                return Collections.emptyList();
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            List<ExtractedMemory> extracted = parseExtractionResult(content);
            if (extracted.isEmpty()) {
                return Collections.emptyList();
            }

            // UPSERT 每条抽取的记忆
            List<UserMemory> result = new ArrayList<>();
            for (ExtractedMemory em : extracted) {
                UserMemory saved = upsertMemory(userId, em.type(), em.key(), em.value(),
                        "EXPLICIT", BigDecimal.ONE, em.evidenceQuote());
                if (saved != null) {
                    result.add(saved);
                }
            }

            log.info("Extracted memories: sessionId={}, userId={}, count={}", sessionId, userId, result.size());
            return result;
        } catch (Exception e) {
            log.warn("Failed to extract memories: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * UPSERT 用户记忆（ADR-059 4.1）。
     *
     * 已存在同 user_id + type + key：
     *   - confidence = min(1, confidence + 0.1)
     *   - 更新 value
     *   - evidence_count += 1
     * 不存在：
     *   - 插入，confidence=1.0, source=EXPLICIT
     *
     * @return 保存后的 UserMemory
     */
    public UserMemory upsertMemory(String userId, String type, String key, String value,
            String source, BigDecimal confidence, String evidenceQuote) {
        try {
            UserMemory existing = userMemoryMapper.selectOne(
                    new LambdaQueryWrapper<UserMemory>()
                            .eq(UserMemory::getUserId, userId)
                            .eq(UserMemory::getMemoryType, type)
                            .eq(UserMemory::getMemoryKey, key)
            );

            if (existing != null) {
                // 已存在：累加置信度
                BigDecimal newConfidence = existing.getConfidence() != null
                        ? existing.getConfidence().add(BigDecimal.valueOf(0.1))
                        : confidence;
                if (newConfidence.compareTo(BigDecimal.ONE) > 0) {
                    newConfidence = BigDecimal.ONE;
                }
                existing.setMemoryValue(value);
                existing.setConfidence(newConfidence);
                existing.setEvidenceCount((existing.getEvidenceCount() != null
                        ? existing.getEvidenceCount() : 0) + 1);
                userMemoryMapper.updateById(existing);
                return existing;
            } else {
                // 新增
                UserMemory memory = UserMemory.builder()
                        .userId(userId)
                        .memoryType(type)
                        .memoryKey(key)
                        .memoryValue(value)
                        .confidence(confidence)
                        .source(source)
                        .evidenceCount(1)
                        .conflictFlag(0)
                        .volatileFlag(0)
                        .build();
                userMemoryMapper.insert(memory);
                return memory;
            }
        } catch (Exception e) {
            log.warn("Failed to upsert memory: userId={}, key={}, error={}", userId, key, e.getMessage());
            return null;
        }
    }

    /**
     * 调用抽取 LLM。
     */
    private DeepSeekResponse callExtractionLLM(String rollingSummary) {
        List<DeepSeekRequest.Message> messages = List.of(
                DeepSeekRequest.Message.builder()
                        .role("system")
                        .content(EXTRACTION_SYSTEM_PROMPT)
                        .build(),
                DeepSeekRequest.Message.builder()
                        .role("user")
                        .content("摘要: " + rollingSummary)
                        .build()
        );
        return deepSeekClient.chatCompletion(messages).block();
    }

    /**
     * 解析 LLM 返回的抽取结果 JSON 数组。
     */
    @SuppressWarnings("unchecked")
    private List<ExtractedMemory> parseExtractionResult(String content) {
        if (content == null || content.isBlank()) return Collections.emptyList();

        try {
            String json = content.trim();
            // 处理 markdown 代码块包裹
            if (json.startsWith("```")) {
                int start = json.indexOf('\n');
                int end = json.lastIndexOf("```");
                if (start > 0 && end > start) {
                    json = json.substring(start + 1, end).trim();
                }
            }
            // 提取 JSON 数组
            int arrStart = json.indexOf('[');
            int arrEnd = json.lastIndexOf(']');
            if (arrStart < 0 || arrEnd <= arrStart) return Collections.emptyList();
            json = json.substring(arrStart, arrEnd + 1);

            List<Map<String, Object>> parsed = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            List<ExtractedMemory> result = new ArrayList<>();
            for (Map<String, Object> item : parsed) {
                String type = (String) item.get("type");
                String key = (String) item.get("key");
                String value = item.get("value") != null ? item.get("value").toString() : null;
                String quote = (String) item.get("evidence_quote");
                if (type != null && key != null && value != null) {
                    result.add(new ExtractedMemory(type, key, value, quote));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse extraction result: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 截断文本到指定长度。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ========== 内部 DTO ==========

    /**
     * LLM 抽取的记忆项。
     */
    private record ExtractedMemory(String type, String key, String value, String evidenceQuote) {
    }
}
