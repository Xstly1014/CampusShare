package com.campushare.agent.dto;

import lombok.Data;

/**
 * 帖子变更通知请求（agent-service 侧）。
 *
 * 字段与 post-service 侧 AgentFeignClient.PostVectorNotifyRequest 对齐。
 */
@Data
public class PostVectorNotifyRequest {
    private String postId;
    private String action;
}
