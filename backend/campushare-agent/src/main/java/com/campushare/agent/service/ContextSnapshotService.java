package com.campushare.agent.service;

import com.campushare.agent.dto.ContextSnapshot;
import com.campushare.agent.entity.AgentContextSnapshot;
import com.campushare.agent.mapper.AgentContextSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 上下文快照入库服务（ADR-076）。
 *
 * 每次 ContextAssembler 组装完上下文后，异步写入 agent_context_snapshots 表。
 * 快照是复盘"为什么答错"的唯一证据，实时写入虽有延迟（≈2ms），但可接受。
 *
 * 写入失败不阻塞主流程，仅 log 告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextSnapshotService {

    private final AgentContextSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    /**
     * 异步写入上下文快照（fire-and-forget）。
     *
     * @param snapshot 上下文快照
     */
    public void saveSnapshot(ContextSnapshot snapshot) {
        Mono.fromRunnable(() -> doSave(snapshot))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> {},
                        e -> log.warn("Failed to save context snapshot: sessionId={}, turnId={}, error={}",
                                snapshot.sessionId(), snapshot.turnId(), e.getMessage())
                );
    }

    private void doSave(ContextSnapshot snapshot) {
        try {
            AgentContextSnapshot entity = AgentContextSnapshot.builder()
                    .sessionId(snapshot.sessionId())
                    .turnId(snapshot.turnId())
                    .messagesJson(serializeMessages(snapshot))
                    .layerTokens(serializeLayerTokens(snapshot.layerTokens()))
                    .totalInputTokens(snapshot.totalInputTokens())
                    .usedMemoryIds(snapshot.usedMemoryIds() != null
                            ? objectMapper.writeValueAsString(snapshot.usedMemoryIds()) : null)
                    .truncated(snapshot.truncated() ? 1 : 0)
                    .truncationReason(snapshot.truncationReason())
                    .build();
            snapshotMapper.insert(entity);
            log.debug("Context snapshot saved: sessionId={}, turnId={}, tokens={}, truncated={}",
                    snapshot.sessionId(), snapshot.turnId(), snapshot.totalInputTokens(),
                    snapshot.truncated());
        } catch (Exception e) {
            log.warn("Failed to serialize/insert context snapshot: sessionId={}, turnId={}, error={}",
                    snapshot.sessionId(), snapshot.turnId(), e.getMessage());
        }
    }

    private String serializeMessages(ContextSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot.messages());
        } catch (Exception e) {
            log.warn("Failed to serialize messages for snapshot: {}", e.getMessage());
            return "[]";
        }
    }

    private String serializeLayerTokens(java.util.Map<String, Integer> layerTokens) {
        try {
            return objectMapper.writeValueAsString(layerTokens);
        } catch (Exception e) {
            log.warn("Failed to serialize layerTokens: {}", e.getMessage());
            return "{}";
        }
    }
}
