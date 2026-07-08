package com.campushare.agent.service;

import com.campushare.agent.dto.MemoryMessage;
import com.campushare.agent.llm.DeepSeekClient;
import com.campushare.agent.llm.DeepSeekRequest;
import com.campushare.agent.llm.DeepSeekResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 上下文压缩服务（ADR-050~053）。
 *
 * 三级渐进压缩：
 *   L1 滚动摘要 — 把旧对话合并为 ≤300 字摘要
 *   L2 槽位冻结 — 抽取结构化事实（意图/分类/学校/帖子ID/约束）
 *   L3 Pin 消息  — 标记用户偏好/约束消息，永不压缩
 *
 * 三合一 LLM 调用（ADR-051）：一次 prompt 输出三段 JSON，成本降 60%。
 *
 * 触发条件（任一满足）：
 *   - Token 触发：L4 历史 > 2500 token
 *   - 轮次触发：单会话轮次 > 10
 *
 * 降级策略（ADR-053 边界情况）：
 *   - LLM 调用失败/超时 → 直接截断最早 2 轮，不生成摘要
 *   - JSON parse 失败 → 重试 1 次，仍失败则截断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressionService {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    private static final String COMPRESSION_SYSTEM_PROMPT = """
            你是对话摘要器。请把以下旧摘要 + 旧对话合并为一份新摘要，并抽取结构化槽位和关键消息。

            要求：
            1. 摘要保留用户提到的所有具体约束（帖子ID、分类、学校、时间）。
            2. 摘要保留用户已澄清的意图与已否决的选项。
            3. 摘要丢弃寒暄、重复确认、无效试探。
            4. 摘要 ≤300 字。
            5. 槽位抽取已确认的意图、分类、学校、帖子ID、用户约束。
            6. Pin 标记用户明确声明的偏好/约束消息（如"记住我偏好PDF"）。

            输出 JSON 格式（不要输出其他内容）：
            {"summary":"新摘要文本","slots":{"confirmed_intent":"SEARCH","target_category":null,"target_school":null,"mentioned_post_ids":[],"rejected_post_ids":[],"user_constraints":{}},"pins":[{"content":"消息内容","reason":"Pin原因"}]}

            若无需 Pin，pins 输出空数组 []。
            若某槽位无值，输出 null。
            """;

    private static final int MAX_RETRIES = 1;
    private static final int SUMMARY_MAX_CHARS = 300;

    /**
     * 压缩对话历史（三合一 LLM 调用）。
     *
     * @param oldSummary    旧的滚动摘要（可为 null）
     * @param messagesToCompress 需要压缩的消息列表（正序，最旧的在前）
     * @param existingSlots 已有的槽位（可为 null）
     * @return 压缩结果（新摘要 + 更新槽位 + Pin 消息）
     */
    public CompressionResult compress(String oldSummary, List<MemoryMessage> messagesToCompress,
            Map<String, String> existingSlots) {

        if (messagesToCompress == null || messagesToCompress.isEmpty()) {
            return CompressionResult.empty();
        }

        // 构建压缩 prompt
        String userPrompt = buildCompressionPrompt(oldSummary, messagesToCompress, existingSlots);

        // 调用 LLM（带重试）
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                DeepSeekResponse response = callCompressionLLM(userPrompt);
                if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    CompressionResult result = parseCompressionResult(content);
                    if (result != null) {
                        log.info("Compression succeeded: attempt={}, messagesCompressed={}, summaryLen={}",
                                attempt, messagesToCompress.size(),
                                result.summary() != null ? result.summary().length() : 0);
                        return result;
                    }
                }
                log.warn("Compression attempt {} returned empty/invalid response", attempt);
            } catch (Exception e) {
                log.warn("Compression attempt {} failed: {}", attempt, e.getMessage());
            }
        }

        // 降级：截断不生成摘要
        log.warn("Compression LLM failed after {} retries, falling back to truncate", MAX_RETRIES);
        return CompressionResult.fallbackResult();
    }

    /**
     * 构建压缩 prompt。
     */
    private String buildCompressionPrompt(String oldSummary, List<MemoryMessage> messages,
            Map<String, String> existingSlots) {
        StringBuilder sb = new StringBuilder();

        sb.append("旧摘要: ");
        sb.append(oldSummary != null && !oldSummary.isBlank() ? oldSummary : "（无）");
        sb.append("\n\n");

        sb.append("已有槽位: ");
        sb.append(existingSlots != null && !existingSlots.isEmpty()
                ? existingSlots.toString() : "（无）");
        sb.append("\n\n");

        sb.append("旧对话:\n");
        for (MemoryMessage msg : messages) {
            sb.append("[turn=").append(msg.getTurnId())
              .append(", role=").append(msg.getRole())
              .append("] ");
            if (msg.getContent() != null) {
                // 截断过长的单条消息
                String content = msg.getContent();
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                sb.append(content);
            }
            if (msg.isInterrupted()) {
                sb.append(" [此条回复曾中断]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 调用压缩 LLM。
     */
    private DeepSeekResponse callCompressionLLM(String userPrompt) {
        List<DeepSeekRequest.Message> messages = List.of(
                DeepSeekRequest.Message.builder()
                        .role("system")
                        .content(COMPRESSION_SYSTEM_PROMPT)
                        .build(),
                DeepSeekRequest.Message.builder()
                        .role("user")
                        .content(userPrompt)
                        .build()
        );
        return deepSeekClient.chatCompletion(messages).block();
    }

    /**
     * 解析 LLM 返回的压缩结果 JSON。
     *
     * LLM 可能返回 ```json ... ``` 包裹的 JSON，需要提取。
     */
    @SuppressWarnings("unchecked")
    private CompressionResult parseCompressionResult(String content) {
        if (content == null || content.isBlank()) return null;

        try {
            // 去除可能的 markdown 代码块包裹
            String json = extractJson(content);
            if (json == null) return null;

            Map<String, Object> parsed = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

            String summary = (String) parsed.get("summary");
            if (summary != null && summary.length() > SUMMARY_MAX_CHARS) {
                summary = summary.substring(0, SUMMARY_MAX_CHARS) + "...";
            }

            Map<String, String> slots = null;
            Object slotsObj = parsed.get("slots");
            if (slotsObj instanceof Map) {
                slots = new java.util.HashMap<>();
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) slotsObj).entrySet()) {
                    slots.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
                }
            }

            List<PinnedMessage> pins = new ArrayList<>();
            Object pinsObj = parsed.get("pins");
            if (pinsObj instanceof List) {
                for (Object item : (List<Object>) pinsObj) {
                    if (item instanceof Map) {
                        Map<String, Object> pinMap = (Map<String, Object>) item;
                        String pinContent = (String) pinMap.get("content");
                        String pinReason = (String) pinMap.get("reason");
                        if (pinContent != null && !pinContent.isBlank()) {
                            pins.add(new PinnedMessage(pinContent, pinReason));
                        }
                    }
                }
            }

            return new CompressionResult(summary, slots, pins, false);
        } catch (Exception e) {
            log.warn("Failed to parse compression result: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LLM 输出中提取 JSON（处理 markdown 代码块包裹）。
     */
    private String extractJson(String content) {
        String trimmed = content.trim();
        // 处理 ```json ... ``` 格式
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        // 处理直接 JSON 格式
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return null;
    }

    // ========== DTO ==========

    /**
     * 压缩结果。
     *
     * @param summary     新的滚动摘要（≤300 字）
     * @param slots       更新后的槽位
     * @param pins        新增的 Pin 消息
     * @param fallback    是否为降级模式（LLM 失败时的截断 fallback）
     */
    public record CompressionResult(
            String summary,
            Map<String, String> slots,
            List<PinnedMessage> pins,
            boolean fallback
    ) {
        public static CompressionResult empty() {
            return new CompressionResult(null, null, Collections.emptyList(), false);
        }

        public static CompressionResult fallbackResult() {
            return new CompressionResult(null, null, Collections.emptyList(), true);
        }
    }

    /**
     * Pin 消息（用户明确声明的偏好/约束）。
     */
    public record PinnedMessage(String content, String reason) {
    }
}
