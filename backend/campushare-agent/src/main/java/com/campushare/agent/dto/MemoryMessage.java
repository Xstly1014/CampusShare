package com.campushare.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 短期记忆中的单条消息（Redis List 元素）。
 *
 * 对应 agent:session:{sessionId}:messages 的单个元素。
 * 每条消息记录 role/content/tokens/timestamp，tool 消息额外记录 tool_name/tool_args。
 *
 * ADR-054: 5 Key 分离结构，messages 为 List，每个元素是此对象的 JSON 序列化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryMessage {

    /** 轮次ID（同一轮的 user+assistant+tool 消息共享 turnId） */
    private int turnId;

    /** 消息角色：user / assistant / tool */
    private String role;

    /** 消息正文 */
    private String content;

    /** 本条消息的 token 数 */
    private int tokens;

    /** 工具名（仅 role=tool） */
    private String toolName;

    /** 工具参数（仅 role=tool） */
    private Map<String, Object> toolArgs;

    /** 引用资源列表（如 post:uuid1） */
    private List<String> refs;

    /** 是否被 Pin（Pin 的消息不会被压缩丢弃） */
    private boolean pinned;

    /** 时间戳（Unix 秒） */
    private long ts;

    /** 是否被中断（SSE 断连时保留 partial 消息，标记 interrupted=true） */
    private boolean interrupted;
}
