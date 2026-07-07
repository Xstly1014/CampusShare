package com.campushare.agent.controller;

import com.campushare.agent.dto.PostVectorNotifyRequest;
import com.campushare.agent.entity.KnowledgeArticleVersion;
import com.campushare.agent.service.KnowledgeIngestionService;
import com.campushare.agent.service.KnowledgeVersionService;
import com.campushare.agent.service.PostVectorService;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.store.PostVectorStore;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 内部 API 控制器（绕过网关，无 /api 前缀）。
 *
 * 路径约定：/internal/agent/...
 *
 * 暴露：
 * - POST /internal/agent/knowledge/reindex：手动触发知识库重建索引
 * - GET  /internal/agent/knowledge/status：查看知识库索引状态
 * - GET  /internal/agent/knowledge/{id}/versions：查询文章版本历史
 * - POST /internal/agent/knowledge/{id}/rollback：回滚到指定版本
 * - POST /internal/agent/knowledge/{id}/feedback：用户反馈（点赞/点踩）
 * - GET  /internal/agent/vector/status：查看向量库总体状态
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
    private final KnowledgeVersionService knowledgeVersionService;

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
                    status.put("knowledgeChunkCount", knowledgeVectorStore.count());
                    status.put("knowledgeArticleCount", knowledgeVectorStore.countArticles());
                    return status;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @GetMapping("/knowledge/{id}/versions")
    public Mono<Result<List<Map<String, Object>>>> listVersions(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
                    List<KnowledgeArticleVersion> versions = knowledgeVersionService.listVersions(id);
                    return versions.stream()
                            .map(this::toVersionSummary)
                            .collect(Collectors.toList());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @PostMapping("/knowledge/{id}/rollback")
    public Mono<Result<Map<String, Object>>> rollback(
            @PathVariable Long id,
            @RequestParam String version) {
        log.info("Rollback article {} to version {}", id, version);
        return Mono.fromCallable(() -> {
                    knowledgeVersionService.rollback(id, version);
                    knowledgeIngestionService.reingestArticle(id);
                    Map<String, Object> result = new HashMap<>();
                    result.put("articleId", id);
                    result.put("rolledBackTo", version);
                    result.put("reingested", true);
                    return result;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @PostMapping("/knowledge/{id}/feedback")
    public Mono<Result<Map<String, Object>>> feedback(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean positive) {
        return Mono.fromCallable(() -> {
                    knowledgeVersionService.updateFeedback(id, positive);
                    Map<String, Object> result = new HashMap<>();
                    result.put("articleId", id);
                    result.put("positive", positive);
                    return result;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @GetMapping("/vector/status")
    public Mono<Result<Map<String, Object>>> vectorStatus() {
        return Mono.fromCallable(() -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("knowledgeChunkCount", knowledgeVectorStore.count());
                    status.put("knowledgeArticleCount", knowledgeVectorStore.countArticles());
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

    private Map<String, Object> toVersionSummary(KnowledgeArticleVersion v) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("version", v.getVersion());
        summary.put("title", v.getTitle());
        summary.put("chunkCount", v.getChunkCount());
        summary.put("snapshotReason", v.getSnapshotReason());
        summary.put("createdAt", v.getCreatedAt());
        return summary;
    }
}
