package com.campushare.agent.enums;

/**
 * Agent 会话状态机枚举（ADR-064）。
 *
 * 8 个状态覆盖会话全生命周期：
 *   INIT           — 会话刚创建，未发首条消息
 *   ACTIVE         — 正常多轮对话中
 *   TOOL_CALLING   — 正在执行工具调用
 *   WAITING_CLARIFY — 等待用户回答澄清问题
 *   REFLECTING     — 后台反思中（伪状态，不阻塞 ACTIVE）
 *   ARCHIVED       — 会话归档，只读
 *   CLOSED         — 用户主动关闭
 *   ERROR          — 不可恢复错误
 */
public enum SessionStatus {
    INIT,
    ACTIVE,
    TOOL_CALLING,
    WAITING_CLARIFY,
    REFLECTING,
    ARCHIVED,
    CLOSED,
    ERROR
}
