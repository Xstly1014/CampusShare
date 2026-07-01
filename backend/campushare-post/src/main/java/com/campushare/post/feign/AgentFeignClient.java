package com.campushare.post.feign;

import com.campushare.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Agent 服务 Feign 客户端。
 *
 * 用于 post-service 发帖/编辑/删除后异步通知 agent-service 同步帖子向量。
 * 内部接口路径 /internal/agent/...，绕过网关，无 JWT 认证。
 */
@FeignClient(name = "agent-service", url = "${service.agent.url:http://localhost:8083}")
public interface AgentFeignClient {

    /**
     * 通知 agent-service 帖子发生变更（CREATE/UPDATE/DELETE）。
     * agent-service 收到后拉取帖子数据并同步向量库。
     */
    @PostMapping("/internal/agent/posts/sync")
    Result<Void> notifyPostChanged(@RequestBody PostVectorNotifyRequest request);

    @lombok.Data
    class PostVectorNotifyRequest {
        private String postId;
        private String action;
    }
}
