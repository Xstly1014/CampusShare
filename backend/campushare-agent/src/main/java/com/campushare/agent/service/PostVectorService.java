package com.campushare.agent.service;

import com.campushare.agent.dto.PostVectorDTO;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.PostVectorStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostVectorService {

    private final WebClient postWebClient;
    private final EmbeddingClient embeddingClient;
    private final PostVectorStore postVectorStore;
    private final CircuitBreaker postSyncCircuitBreaker;
    private final ObjectMapper objectMapper;

    @Value("${app.post-sync.timeout:30000}")
    private int syncTimeoutMs;

    public PostVectorService(@Qualifier("postWebClient") WebClient postWebClient,
                             EmbeddingClient embeddingClient,
                             PostVectorStore postVectorStore,
                             @Qualifier("postSyncCircuitBreaker") CircuitBreaker postSyncCircuitBreaker,
                             ObjectMapper objectMapper) {
        this.postWebClient = postWebClient;
        this.embeddingClient = embeddingClient;
        this.postVectorStore = postVectorStore;
        this.postSyncCircuitBreaker = postSyncCircuitBreaker;
        this.objectMapper = objectMapper;
    }

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

        return Mono.fromCallable(() -> fetchPostVector(postId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dto -> {
                    if (dto == null) {
                        log.warn("Post not found for vector sync: {}", postId);
                        return Mono.empty();
                    }
                    String text = dto.getTitle() + "\n" +
                            (dto.getContentExcerpt() != null ? dto.getContentExcerpt() : "");
                    return embeddingClient.embed(text)
                            .flatMap(vec -> {
                                if (vec == null || vec.length == 0) {
                                    log.warn("Embedding empty for post {}, skipping upsert", postId);
                                    return Mono.empty();
                                }
                                return Mono.fromRunnable(() -> upsertPostVector(dto, vec));
                            })
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("Failed to sync post vector {}: {}", postId, e.getMessage());
                    return Mono.empty();
                });
    }

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
            List<PostVectorDTO> posts;
            try {
                posts = fetchPostsPage(page, size);
            } catch (Exception e) {
                log.warn("Failed to fetch posts page {} for vector sync: {}", page, e.getMessage());
                break;
            }

            if (posts == null || posts.isEmpty()) {
                break;
            }

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

    private PostVectorDTO fetchPostVector(String postId) {
        try {
            String json = postWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/posts/{postId}/vector-data").build(postId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(syncTimeoutMs))
                    .block();
            if (json == null) return null;
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) return null;
            return objectMapper.treeToValue(data, PostVectorDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch post vector data: " + e.getMessage(), e);
        }
    }

    private List<PostVectorDTO> fetchPostsPage(int page, int size) {
        try {
            String json = postWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/posts/all-for-vector")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(syncTimeoutMs))
                    .block();
            if (json == null) return new ArrayList<>();
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) return new ArrayList<>();
            JsonNode records = data.get("records");
            if (records == null || !records.isArray()) return new ArrayList<>();
            return objectMapper.readValue(
                    objectMapper.treeAsTokens(records),
                    new TypeReference<List<PostVectorDTO>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch posts page " + page + ": " + e.getMessage(), e);
        }
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
