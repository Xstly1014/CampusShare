package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.MemoryMessage;
import com.campushare.agent.entity.AgentTurn;
import com.campushare.agent.entity.ContextSlot;
import com.campushare.agent.entity.ContextSummary;
import com.campushare.agent.entity.PinMessage;
import com.campushare.agent.mapper.ContextSlotMapper;
import com.campushare.agent.mapper.ContextSummaryMapper;
import com.campushare.agent.mapper.PinMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短期对话记忆服务（ADR-054~058）。
 *
 * 每个会话在 Redis 中占用 5 个 Key：
 *   1. agent:session:{sid}:meta             (Hash)   会话元数据
 *   2. agent:session:{sid}:messages         (List)   最近 20 条消息
 *   3. agent:session:{sid}:rolling_summary  (String) 滚动摘要
 *   4. agent:session:{sid}:slots            (Hash)   槽位
 *   5. agent:session:{sid}:pinned           (List)   Pin 消息
 *
 * TTL：2 小时，每次活跃自动续期（pipeline 批量 EXPIRE）。
 *
 * 降级策略：Redis 故障时返回空数据，不阻塞主流程（AgentChatService 会从 MySQL 加载历史）。
 *
 * 并发：单会话串行由 AgentChatService 的 session_lock 保证，本服务不额外加锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ContextSummaryMapper summaryMapper;
    private final ContextSlotMapper slotMapper;
    private final PinMessageMapper pinMapper;

    private static final String KEY_PREFIX = "agent:session:";
    private static final Duration TTL = Duration.ofHours(2);

    /** messages List 最大长度，超出触发压缩 */
    private static final int MAX_MESSAGES = 20;

    /** pinned List 最大长度 */
    private static final int MAX_PINNED = 5;

    /** 触发压缩的阈值（messages 长度超过此值时建议压缩） */
    private static final int COMPRESS_THRESHOLD = 10;

    // ========== Key 构造 ==========

    private String metaKey(String sessionId) {
        return KEY_PREFIX + sessionId + ":meta";
    }

    private String messagesKey(String sessionId) {
        return KEY_PREFIX + sessionId + ":messages";
    }

    private String summaryKey(String sessionId) {
        return KEY_PREFIX + sessionId + ":rolling_summary";
    }

    private String slotsKey(String sessionId) {
        return KEY_PREFIX + sessionId + ":slots";
    }

    private String pinnedKey(String sessionId) {
        return KEY_PREFIX + sessionId + ":pinned";
    }

    // ========== 初始化 ==========

    /**
     * 初始化会话记忆（新会话创建时调用）。
     *
     * 写入 meta Hash，设置 TTL。若已存在则覆盖 meta。
     */
    public void initSession(String sessionId, String userId, String promptVersion, String llmModel) {
        try {
            Map<String, String> meta = new HashMap<>();
            meta.put("user_id", userId);
            meta.put("status", "ACTIVE");
            meta.put("intent_history", "[]");
            meta.put("current_intent", "");
            meta.put("turn_count", "0");
            meta.put("started_at", String.valueOf(System.currentTimeMillis() / 1000));
            meta.put("last_active_at", String.valueOf(System.currentTimeMillis() / 1000));
            if (promptVersion != null) meta.put("prompt_version", promptVersion);
            if (llmModel != null) meta.put("llm_model", llmModel);

            redis.opsForHash().putAll(metaKey(sessionId), meta);
            renewTTL(sessionId);
            log.debug("Session memory initialized: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("Failed to init session memory: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ========== 读操作 ==========

    /**
     * 加载会话元数据。
     */
    public Map<String, String> loadMeta(String sessionId) {
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(metaKey(sessionId));
            if (raw.isEmpty()) return Collections.emptyMap();
            Map<String, String> meta = new HashMap<>();
            raw.forEach((k, v) -> meta.put(k.toString(), v.toString()));
            return meta;
        } catch (Exception e) {
            log.warn("Failed to load meta: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 加载消息列表（按时间正序，最旧的在前）。
     *
     * Redis List 是左进右出（LPUSH + RPUSH），这里用 LRANGE 0 -1 取全部。
     */
    public List<MemoryMessage> loadMessages(String sessionId) {
        try {
            List<String> jsonList = redis.opsForList().range(messagesKey(sessionId), 0, -1);
            if (jsonList == null || jsonList.isEmpty()) return Collections.emptyList();
            List<MemoryMessage> messages = new ArrayList<>(jsonList.size());
            for (String json : jsonList) {
                messages.add(objectMapper.readValue(json, MemoryMessage.class));
            }
            return messages;
        } catch (Exception e) {
            log.warn("Failed to load messages: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 加载滚动摘要。
     * Redis 未命中时从 MySQL 加载（压缩结果持久化）。
     */
    public String loadSummary(String sessionId) {
        try {
            String summary = redis.opsForValue().get(summaryKey(sessionId));
            if (summary != null) return summary;
            // Redis miss → 从 MySQL 加载最新摘要
            ContextSummary latest = summaryMapper.findLatestBySessionId(sessionId);
            if (latest != null) {
                // 回写 Redis 作为热缓存
                redis.opsForValue().set(summaryKey(sessionId), latest.getSummaryText(), TTL);
                return latest.getSummaryText();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to load summary: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 加载槽位。
     * Redis 未命中时从 MySQL 加载（槽位冻结持久化）。
     */
    public Map<String, String> loadSlots(String sessionId) {
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(slotsKey(sessionId));
            if (raw != null && !raw.isEmpty()) {
                Map<String, String> slots = new HashMap<>();
                raw.forEach((k, v) -> slots.put(k.toString(), v.toString()));
                return slots;
            }
            // Redis miss → 从 MySQL 加载
            List<ContextSlot> dbSlots = slotMapper.selectList(
                    new LambdaQueryWrapper<ContextSlot>().eq(ContextSlot::getSessionId, sessionId));
            Map<String, String> slots = new HashMap<>();
            if (!dbSlots.isEmpty()) {
                Map<String, String> redisMap = new HashMap<>();
                for (ContextSlot s : dbSlots) {
                    slots.put(s.getSlotKey(), s.getSlotValue());
                    redisMap.put(s.getSlotKey(), s.getSlotValue());
                }
                // 回写 Redis
                redis.opsForHash().putAll(slotsKey(sessionId), redisMap);
                redis.expire(slotsKey(sessionId), TTL);
            }
            return slots;
        } catch (Exception e) {
            log.warn("Failed to load slots: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 加载 Pin 消息。
     * Redis 未命中时从 MySQL 加载（Pin消息持久化）。
     */
    public List<MemoryMessage> loadPinned(String sessionId) {
        try {
            List<String> jsonList = redis.opsForList().range(pinnedKey(sessionId), 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                // Redis miss → 从 MySQL 加载
                List<PinMessage> dbPins = pinMapper.selectList(
                        new LambdaQueryWrapper<PinMessage>().eq(PinMessage::getSessionId, sessionId)
                                .orderByAsc(PinMessage::getCreatedAt));
                if (dbPins.isEmpty()) return Collections.emptyList();
                List<MemoryMessage> messages = new ArrayList<>(dbPins.size());
                for (PinMessage p : dbPins) {
                    MemoryMessage msg = new MemoryMessage();
                    msg.setRole("assistant");
                    msg.setContent(p.getContent());
                    msg.setPinned(true);
                    messages.add(msg);
                    // 回写 Redis
                    try {
                        redis.opsForList().rightPush(pinnedKey(sessionId), objectMapper.writeValueAsString(msg));
                    } catch (Exception ignored) {}
                }
                redis.expire(pinnedKey(sessionId), TTL);
                return messages;
            }
            List<MemoryMessage> messages = new ArrayList<>(jsonList.size());
            for (String json : jsonList) {
                messages.add(objectMapper.readValue(json, MemoryMessage.class));
            }
            return messages;
        } catch (Exception e) {
            log.warn("Failed to load pinned: sessionId={}, error={}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 加载完整短期记忆（5 Key 一次性读取）。
     */
    public ShortTermMemory loadAll(String sessionId) {
        return new ShortTermMemory(
                loadMeta(sessionId),
                loadMessages(sessionId),
                loadSummary(sessionId),
                loadSlots(sessionId),
                loadPinned(sessionId)
        );
    }

    /**
     * 加载历史消息并转换为 AgentTurn 列表（供 ContextAssembler 使用）。
     *
     * 将 Redis 中的 user+assistant 消息对配对为 AgentTurn。
     * 滚动摘要若有，作为虚拟的第一轮（user="[摘要]", assistant="[见上方摘要]"）注入。
     */
    public List<AgentTurn> loadHistoryAsTurns(String sessionId) {
        List<MemoryMessage> messages = loadMessages(sessionId);
        if (messages.isEmpty()) return Collections.emptyList();

        List<AgentTurn> turns = new ArrayList<>();
        MemoryMessage pendingUser = null;

        for (MemoryMessage msg : messages) {
            if ("user".equals(msg.getRole())) {
                if (pendingUser != null) {
                    // 前一个 user 没有 assistant 配对，跳过
                    turns.add(AgentTurn.builder()
                            .userMessage(pendingUser.getContent())
                            .assistantMessage(null)
                            .build());
                }
                pendingUser = msg;
            } else if ("assistant".equals(msg.getRole())) {
                String userContent = pendingUser != null ? pendingUser.getContent() : "";
                turns.add(AgentTurn.builder()
                        .userMessage(userContent)
                        .assistantMessage(msg.getContent())
                        .build());
                pendingUser = null;
            }
        }
        // 最后一个未配对的 user
        if (pendingUser != null) {
            turns.add(AgentTurn.builder()
                    .userMessage(pendingUser.getContent())
                    .assistantMessage(null)
                    .build());
        }

        return turns;
    }

    // ========== 写操作 ==========

    /**
     * 追加消息到 messages List。
     *
     * 超过 MAX_MESSAGES 时自动 LTRIM 保留最近的。
     * 返回当前消息数量，供调用方判断是否需要压缩。
     */
    public int appendMessage(String sessionId, MemoryMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            Long len = redis.opsForList().rightPush(messagesKey(sessionId), json);
            if (len != null && len > MAX_MESSAGES) {
                redis.opsForList().trim(messagesKey(sessionId), -MAX_MESSAGES, -1);
                len = (long) MAX_MESSAGES;
            }
            return len != null ? len.intValue() : 0;
        } catch (Exception e) {
            log.warn("Failed to append message: sessionId={}, error={}", sessionId, e.getMessage());
            return 0;
        }
    }

    /**
     * 更新滚动摘要（Redis + MySQL异步持久化）。
     */
    public void updateSummary(String sessionId, String summary) {
        updateSummary(sessionId, summary, null, 0);
    }

    /**
     * 更新滚动摘要（Redis + MySQL异步持久化），包含覆盖轮次信息。
     */
    public void updateSummary(String sessionId, String summary, String coveredTurnIds, int tokenCount) {
        try {
            redis.opsForValue().set(summaryKey(sessionId), summary, TTL);
        } catch (Exception e) {
            log.warn("Failed to update summary in Redis: sessionId={}, error={}", sessionId, e.getMessage());
        }
        // 异步写入 MySQL 持久化
        persistSummaryAsync(sessionId, summary, coveredTurnIds, tokenCount);
    }

    private void persistSummaryAsync(String sessionId, String summary, String coveredTurnIds, int tokenCount) {
        Mono.fromRunnable(() -> {
            try {
                ContextSummary entity = ContextSummary.builder()
                        .sessionId(sessionId)
                        .summaryText(summary)
                        .coveredTurnIds(coveredTurnIds != null ? coveredTurnIds : "")
                        .tokenCount(tokenCount)
                        .createdAt(LocalDateTime.now())
                        .build();
                summaryMapper.insert(entity);
            } catch (Exception e) {
                log.warn("Failed to persist summary to MySQL: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 更新槽位（合并写入 Redis + MySQL持久化）。
     */
    public void updateSlots(String sessionId, Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) return;
        try {
            redis.opsForHash().putAll(slotsKey(sessionId), slots);
            redis.expire(slotsKey(sessionId), TTL);
        } catch (Exception e) {
            log.warn("Failed to update slots in Redis: sessionId={}, error={}", sessionId, e.getMessage());
        }
        // 异步写入 MySQL（upsert 语义：INSERT ... ON DUPLICATE KEY UPDATE）
        persistSlotsAsync(sessionId, slots);
    }

    private void persistSlotsAsync(String sessionId, Map<String, String> slots) {
        Mono.fromRunnable(() -> {
            try {
                for (Map.Entry<String, String> entry : slots.entrySet()) {
                    ContextSlot existing = slotMapper.selectOne(
                            new LambdaQueryWrapper<ContextSlot>()
                                    .eq(ContextSlot::getSessionId, sessionId)
                                    .eq(ContextSlot::getSlotKey, entry.getKey()));
                    if (existing != null) {
                        existing.setSlotValue(entry.getValue());
                        slotMapper.updateById(existing);
                    } else {
                        ContextSlot entity = ContextSlot.builder()
                                .sessionId(sessionId)
                                .slotKey(entry.getKey())
                                .slotValue(entry.getValue())
                                .frozenAt(LocalDateTime.now())
                                .build();
                        slotMapper.insert(entity);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to persist slots to MySQL: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * Pin 一条消息（Redis + MySQL异步持久化）。
     */
    public void pinMessage(String sessionId, MemoryMessage message) {
        try {
            message.setPinned(true);
            String json = objectMapper.writeValueAsString(message);
            Long len = redis.opsForList().rightPush(pinnedKey(sessionId), json);
            if (len != null && len > MAX_PINNED) {
                redis.opsForList().trim(pinnedKey(sessionId), -MAX_PINNED, -1);
            }
        } catch (Exception e) {
            log.warn("Failed to pin message in Redis: sessionId={}, error={}", sessionId, e.getMessage());
        }
        // 异步写入 MySQL
        persistPinAsync(sessionId, message);
    }

    private void persistPinAsync(String sessionId, MemoryMessage message) {
        Mono.fromRunnable(() -> {
            try {
                PinMessage entity = PinMessage.builder()
                        .sessionId(sessionId)
                        .content(message.getContent())
                        .pinnedBy("AGENT")
                        .reason("compression")
                        .createdAt(LocalDateTime.now())
                        .build();
                pinMapper.insert(entity);
            } catch (Exception e) {
                log.warn("Failed to persist pin to MySQL: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    /**
     * 更新 meta 字段（turn_count, last_active_at, current_intent, intent_history 等）。
     */
    public void updateMeta(String sessionId, String field, String value) {
        try {
            redis.opsForHash().put(metaKey(sessionId), field, value);
        } catch (Exception e) {
            log.warn("Failed to update meta: sessionId={}, field={}, error={}",
                    sessionId, field, e.getMessage());
        }
    }

    /**
     * 追加意图到 intent_history 并更新 current_intent。
     */
    public void recordIntent(String sessionId, String intent) {
        try {
            // 读取当前 intent_history
            Object historyJson = redis.opsForHash().get(metaKey(sessionId), "intent_history");
            List<String> history;
            if (historyJson != null) {
                history = objectMapper.readValue(historyJson.toString(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } else {
                history = new ArrayList<>();
            }
            history.add(intent);
            // 保留最近 10 个意图
            if (history.size() > 10) {
                history = new ArrayList<>(history.subList(history.size() - 10, history.size()));
            }
            redis.opsForHash().put(metaKey(sessionId), "intent_history", objectMapper.writeValueAsString(history));
            redis.opsForHash().put(metaKey(sessionId), "current_intent", intent);
        } catch (Exception e) {
            log.warn("Failed to record intent: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 增加轮次计数。
     */
    public void incrementTurnCount(String sessionId) {
        try {
            redis.opsForHash().increment(metaKey(sessionId), "turn_count", 1);
            redis.opsForHash().put(metaKey(sessionId), "last_active_at",
                    String.valueOf(System.currentTimeMillis() / 1000));
        } catch (Exception e) {
            log.warn("Failed to increment turn count: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ========== 压缩相关 ==========

    /**
     * 截断 messages 到指定长度（保留最近的 N 条）。
     *
     * 压缩后调用此方法保留最近 5 条原文消息。
     */
    public void trimMessages(String sessionId, int keepCount) {
        try {
            redis.opsForList().trim(messagesKey(sessionId), -keepCount, -1);
        } catch (Exception e) {
            log.warn("Failed to trim messages: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 检查是否需要压缩（messages 长度 > COMPRESS_THRESHOLD）。
     */
    public boolean needsCompression(String sessionId) {
        try {
            Long len = redis.opsForList().size(messagesKey(sessionId));
            return len != null && len > COMPRESS_THRESHOLD;
        } catch (Exception e) {
            log.warn("Failed to check compression: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 获取需要压缩的消息（超出 keepCount 的旧消息）。
     *
     * @param sessionId 会话ID
     * @param keepCount 保留的最近消息数
     * @return 需要压缩的旧消息列表（正序）
     */
    public List<MemoryMessage> getMessagesToCompress(String sessionId, int keepCount) {
        List<MemoryMessage> all = loadMessages(sessionId);
        if (all.size() <= keepCount) return Collections.emptyList();
        return new ArrayList<>(all.subList(0, all.size() - keepCount));
    }

    // ========== TTL 管理 ==========

    /**
     * 续期所有 5 个 Key 的 TTL（每次活跃时调用）。
     */
    public void renewTTL(String sessionId) {
        try {
            redis.expire(metaKey(sessionId), TTL);
            redis.expire(messagesKey(sessionId), TTL);
            redis.expire(summaryKey(sessionId), TTL);
            redis.expire(slotsKey(sessionId), TTL);
            redis.expire(pinnedKey(sessionId), TTL);
        } catch (Exception e) {
            log.warn("Failed to renew TTL: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ========== 会话关闭 ==========

    /**
     * 标记会话为 CLOSED 状态。
     */
    public void closeSession(String sessionId) {
        try {
            redis.opsForHash().put(metaKey(sessionId), "status", "CLOSED");
            redis.opsForHash().put(metaKey(sessionId), "last_active_at",
                    String.valueOf(System.currentTimeMillis() / 1000));
        } catch (Exception e) {
            log.warn("Failed to close session: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 删除所有 5 个 Key（归档后调用）。
     */
    public void deleteAll(String sessionId) {
        try {
            redis.delete(metaKey(sessionId));
            redis.delete(messagesKey(sessionId));
            redis.delete(summaryKey(sessionId));
            redis.delete(slotsKey(sessionId));
            redis.delete(pinnedKey(sessionId));
            log.debug("Session memory deleted: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("Failed to delete session memory: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ========== 内部 DTO ==========

    /**
     * 完整短期记忆（5 Key 一次性加载结果）。
     */
    public record ShortTermMemory(
            Map<String, String> meta,
            List<MemoryMessage> messages,
            String rollingSummary,
            Map<String, String> slots,
            List<MemoryMessage> pinned
    ) {
        /**
         * 获取 turn_count。
         */
        public int turnCount() {
            String tc = meta.get("turn_count");
            return tc != null ? Integer.parseInt(tc) : 0;
        }

        /**
         * 获取 current_intent。
         */
        public String currentIntent() {
            return meta.get("current_intent");
        }

        /**
         * 是否为空（meta 为空表示 Redis 无此会话）。
         */
        public boolean isEmpty() {
            return meta == null || meta.isEmpty();
        }
    }
}
