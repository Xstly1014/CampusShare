package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campushare.agent.dto.PostVectorDTO;
import com.campushare.agent.feign.PostFeignClient;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.PostVectorStore;
import com.campushare.common.result.Result;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 帖子向量同步服务。
 *
 * 流程：
 * 1. syncPost: 接收 post-service 通知 → Feign 拉取单帖 → embedding → upsert
 * 2. syncAll: 分页拉取全量帖子 → 批量 embedding → 批量 upsert
 *
 * 降级策略：
 * - post-service 不可用：CircuitBreaker 快速失败，log.warn，不影响 agent 对话
 * - embedding 失败：EmbeddingClient 内部已降级返回 Mono.empty()，跳过该帖
 */
@Slf4j
@Service
public class PostVectorService {

    private final PostFeignClient postFeignClient;
    private final EmbeddingClient embeddingClient;
    private final PostVectorStore postVectorStore;
    private final CircuitBreaker postSyncCircuitBreaker;

    public PostVectorService(PostFeignClient postFeignClient,
                             EmbeddingClient embeddingClient,
                             PostVectorStore postVectorStore,
                             @Qualifier("postSyncCircuitBreaker") CircuitBreaker postSyncCircuitBreaker) {
        this.postFeignClient = postFeignClient;
        this.embeddingClient = embeddingClient;
        this.postVectorStore = postVectorStore;
        this.postSyncCircuitBreaker = postSyncCircuitBreaker;
    }

    /**
     * 单帖同步（接收 post-service 通知后调用）。
     */
    public Mono<Void> syncPost(String postId, String action) {
        if ("DELETE".equals(action)) {
            return Mono.fromRunnable(() -> postVectorStore.delete(postId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(e -> {
                        log.warn("Failed to delete post vector {}: {}", postId, e.getMessage());
                        return Mono.empty();
                    })
                    .then();
        }

        return Mono.fromCallable(() -> {
                    Result<PostVectorDTO> result = postFeignClient.getVectorData(postId);
                    return result != null ? result.getData() : null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dto -> {
                    if (dto == null) {
                        log.warn("Post not found for vector sync: {}", postId);
                        return Mono.empty();
                    }
                    String text = dto.getTitle() + "\n" +
                            (dto.getContentExcerpt() != null ? dto.getContentExcerpt() : "");
                    return embeddingClient.embed(text)
                            .doOnNext(vec -> upsertPostVector(dto, vec))
                            .switchIfEmpty(Mono.fromRunnable(() ->
                                    log.warn("Embedding empty for post {}, skipping upsert", postId)))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("Failed to sync post vector {}: {}", postId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 全量同步（调度器 + 手动触发）。
     */
    public Mono<Map<String, Object>> syncAll() {
        return Mono.fromCallable(this::doSyncAll)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Full post sync failed", e);
                    Map<String, Object> err = new HashMap<>();
                    err.put("error", e.getMessage());
                    return Mono.just(err);
                });
    }

    private Map<String, Object> doSyncAll() {
        int page = 1;
        int size = 100;
        int total = 0, success = 0, failed = 0;

        while (true) {
            Result<IPage<PostVectorDTO>> result;
            try {
                result = postFeignClient.getAllForVector(page, size);
            } catch (Exception e) {
                log.warn("Failed to fetch posts page {} for vector sync: {}", page, e.getMessage());
                break;
            }

            if (result == null || result.getData() == null || result.getData().getRecords().isEmpty()) {
                break;
            }

            List<PostVectorDTO> posts = result.getData().getRecords();
            total += posts.size();

            List<String> texts = posts.stream()
                    .map(dto -> dto.getTitle() + "\n" +
                            (dto.getContentExcerpt() != null ? dto.getContentExcerpt() : ""))
                    .collect(Collectors.toList());

            List<float[]> embeddings = embeddingClient.embedBatch(texts).block();

            if (embeddings == null || embeddings.isEmpty()) {
                log.warn("Embedding batch empty for page {}, skipping {} posts", page, posts.size());
                failed += posts.size();
                if (posts.size() < size) break;
                page++;
                continue;
            }

            for (int i = 0; i < posts.size() && i < embeddings.size(); i++) {
                try {
                    PostVectorDTO dto = posts.get(i);
                    float[] vec = embeddings.get(i);
                    if (vec == null || vec.length == 0) {
                        failed++;
                        continue;
                    }
                    upsertPostVector(dto, vec);
                    success++;
                } catch (Exception e) {
                    log.warn("Failed to upsert post vector: {}", e.getMessage());
                    failed++;
                }
            }

            if (posts.size() < size) break;
            page++;
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("postVectorCount", postVectorStore.count());
        log.info("Post vector full sync complete: total={}, success={}, failed={}", total, success, failed);
        return stats;
    }

    private void upsertPostVector(PostVectorDTO dto, float[] vec) {
        postVectorStore.upsert(
                dto.getId(),
                dto.getTitle(),
                dto.getContentExcerpt(),
                dto.getPostType(),
                dto.getCategoryId(),
                dto.getSchoolId(),
                dto.getAuthorId(),
                false,
                dto.getLikeCount() != null ? dto.getLikeCount() : 0,
                dto.getViewCount() != null ? dto.getViewCount() : 0,
                dto.getCreateTime(),
                vec
        );
    }
}
