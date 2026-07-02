package com.campushare.agent.controller;

import com.campushare.agent.dto.PostVectorNotifyRequest;
import com.campushare.agent.service.KnowledgeIngestionService;
import com.campushare.agent.service.PostVectorService;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.store.PostVectorStore;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 内部 API 控制器（绕过网关，无 /api 前缀）。
 *
 * 路径约定：/internal/agent/...
 * 与 InternalPostController 的 /internal/posts 一致。
 *
 * 暴露：
 * - POST /internal/agent/knowledge/reindex：手动触发知识库重建索引
 * - GET /internal/agent/knowledge/status：查看知识库索引状态
 * - GET /internal/agent/vector/status：查看向量库总体状态
 * - POST /internal/agent/posts/sync：接收 post-service 帖子变更通知
 * - POST /internal/agent/posts/reindex：手动触发帖子全量同步
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class InternalAgentController {

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final PostVectorStore postVectorStore;
    private final PostVectorService postVectorService;

    @PostMapping("/knowledge/reindex")
    public Mono<Result<Map<String, Object>>> reindexKnowledge() {
        log.info("Manual knowledge reindex triggered");
        return Mono.fromCallable(() -> knowledgeIngestionService.ingestAll())
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @GetMapping("/knowledge/status")
    public Mono<Result<Map<String, Object>>> knowledgeStatus() {
        return Mono.fromCallable(() -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("knowledgeVectorCount", knowledgeVectorStore.count());
                    return status;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @GetMapping("/vector/status")
    public Mono<Result<Map<String, Object>>> vectorStatus() {
        return Mono.fromCallable(() -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("knowledgeVectorCount", knowledgeVectorStore.count());
                    status.put("postVectorCount", postVectorStore.count());
                    return status;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @PostMapping("/posts/sync")
    public Mono<Result<Void>> notifyPostChanged(@RequestBody PostVectorNotifyRequest request) {
        log.info("Received post change notification: postId={}, action={}", request.getPostId(), request.getAction());
        return postVectorService.syncPost(request.getPostId(), request.getAction())
                .thenReturn(Result.success(null));
    }

    @PostMapping("/posts/reindex")
    public Mono<Result<Map<String, Object>>> reindexPosts() {
        log.info("Manual post reindex triggered");
        return postVectorService.syncAll().map(Result::success);
    }
}

