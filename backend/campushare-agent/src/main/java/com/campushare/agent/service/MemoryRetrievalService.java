package com.campushare.agent.service;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.MemoryVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetrievalService {

    private final MemoryVectorStore memoryVectorStore;
    private final EmbeddingClient embeddingClient;

    @Value("${app.memory-retrieval.vector-top-k:5}")
    private int vectorTopK;

    @Value("${app.memory-retrieval.keyword-top-k:3}")
    private int keywordTopK;

    @Value("${app.memory-retrieval.final-top-k:5}")
    private int finalTopK;

    @Value("${app.memory-retrieval.rrf-k:60}")
    private int rrfK;

    @Value("${app.memory-retrieval.similarity-threshold:0.3}")
    private double similarityThreshold;

    public Mono<List<RetrievalResult>> retrieveRelevantMemories(String userId, String query,
                                                                 IntentResult intent) {
        if (userId == null || userId.isBlank() || query == null || query.isBlank()) {
            return Mono.just(Collections.emptyList());
        }

        return embeddingClient.embed(query)
                .map(queryVec -> {
                    List<List<RetrievalResult>> retrievalLists = new ArrayList<>();
                    boolean vectorAvailable = queryVec != null && queryVec.length > 0;

                    if (vectorAvailable) {
                        try {
                            List<RetrievalResult> vectorResults = memoryVectorStore.search(
                                    userId, queryVec, vectorTopK);
                            List<RetrievalResult> filtered = filterByThreshold(vectorResults);
                            if (!filtered.isEmpty()) {
                                retrievalLists.add(filtered);
                                log.debug("Memory vector search: returned {} results for userId={}",
                                        filtered.size(), userId);
                            }
                        } catch (Exception e) {
                            log.warn("Memory vector search failed for userId={}", userId, e);
                        }
                    }

                    try {
                        List<RetrievalResult> keywordResults = memoryVectorStore.keywordSearch(
                                userId, query, keywordTopK);
                        List<RetrievalResult> filtered = filterByThreshold(keywordResults);
                        if (!filtered.isEmpty()) {
                            retrievalLists.add(filtered);
                            log.debug("Memory keyword search: returned {} results for userId={}",
                                    filtered.size(), userId);
                        }
                    } catch (Exception e) {
                        log.warn("Memory keyword search failed for userId={}", userId, e);
                    }

                    List<RetrievalResult> fused = rrfFusion(retrievalLists, finalTopK);
                    log.debug("Memory retrieval fusion: {} results for userId={}, query='{}'",
                            fused.size(), userId, query.length() > 30 ? query.substring(0, 30) + "..." : query);
                    return fused;
                })
                .defaultIfEmpty(Collections.emptyList())
                .onErrorResume(e -> {
                    log.warn("Memory retrieval failed for userId={}, degrading to empty", userId, e);
                    return Mono.just(Collections.emptyList());
                });
    }

    public List<RetrievalResult> loadProfileMemories(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return memoryVectorStore.loadProfileMemories(userId);
        } catch (Exception e) {
            log.warn("Failed to load profile memories for userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    public String formatProfileText(List<RetrievalResult> memories) {
        if (memories == null || memories.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("[用户画像]\n");
        for (RetrievalResult m : memories) {
            String key = m.metadata() != null ? (String) m.metadata().get("memoryKey") : null;
            String source = m.metadata() != null ? "EXPLICIT".equals(m.metadata().get("source"))
                    ? "，用户明确声明" : "，行为推断" : "";
            String label = formatMemoryLabel(key);
            String value = truncate(m.content(), 30);
            double conf = m.score();

            sb.append("- ").append(label).append(": ").append(value)
                    .append("（置信 ").append(String.format("%.1f", conf)).append(source).append("）\n");

            if (sb.length() > 300) {
                break;
            }
        }
        return sb.toString().trim();
    }

    public Set<Long> getMemoryIds(List<RetrievalResult> memories) {
        if (memories == null || memories.isEmpty()) {
            return Collections.emptySet();
        }
        return memories.stream()
                .map(RetrievalResult::id)
                .filter(Objects::nonNull)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    private List<RetrievalResult> filterByThreshold(List<RetrievalResult> results) {
        return results.stream()
                .filter(r -> r.score() >= similarityThreshold)
                .collect(Collectors.toList());
    }

    private List<RetrievalResult> rrfFusion(List<List<RetrievalResult>> retrievalLists, int topK) {
        if (retrievalLists.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, RetrievalResult> idToResult = new HashMap<>();
        Map<String, Double> idToScore = new HashMap<>();

        for (List<RetrievalResult> list : retrievalLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RetrievalResult result = list.get(rank);
                String key = result.source() + ":" + result.id();
                idToResult.putIfAbsent(key, result);
                double rrfScore = 1.0 / (rrfK + rank + 1);
                idToScore.merge(key, rrfScore, Double::sum);
            }
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(idToScore.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<RetrievalResult> fused = new ArrayList<>(Math.min(topK, sorted.size()));
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            String key = sorted.get(i).getKey();
            double score = sorted.get(i).getValue();
            RetrievalResult original = idToResult.get(key);
            fused.add(new RetrievalResult(
                    original.id(),
                    original.title(),
                    original.content(),
                    score,
                    original.source(),
                    original.metadata()
            ));
        }

        return fused;
    }

    private String formatMemoryLabel(String key) {
        if (key == null) return "未知";
        return switch (key) {
            case "preferred_format" -> "偏好格式";
            case "major" -> "专业";
            case "top_category" -> "主要兴趣分类";
            case "current_task" -> "最近任务";
            case "preferred_language" -> "偏好语言";
            default -> key;
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
