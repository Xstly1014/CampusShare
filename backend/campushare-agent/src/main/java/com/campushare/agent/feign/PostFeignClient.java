package com.campushare.agent.feign;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campushare.agent.dto.PostVectorDTO;
import com.campushare.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Post 服务 Feign 客户端。
 *
 * 用于 agent-service 拉取帖子数据做向量化。
 * 内部接口路径 /internal/posts/...，绕过网关，无 JWT 认证。
 */
@FeignClient(name = "post-service", url = "${service.post.url:http://localhost:8082}")
public interface PostFeignClient {

    @GetMapping("/internal/posts/all-for-vector")
    Result<IPage<PostVectorDTO>> getAllForVector(@RequestParam("page") int page, @RequestParam("size") int size);

    @GetMapping("/internal/posts/{postId}/vector-data")
    Result<PostVectorDTO> getVectorData(@PathVariable("postId") String postId);
}
