package com.campushare.agent.store;

import com.campushare.agent.dto.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MemoryVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public MemoryVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(Long memoryId, String userId, String memoryType, String memoryKey,
                       String content, BigDecimal confidence, String source, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        String sql = """
                INSERT INTO memory_vectors (memory_id, user_id, memory_type, memory_key, content,
                    confidence, source, embedding, embedding_model, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, 'bge-m3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (memory_id) DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    memory_type = EXCLUDED.memory_type,
                    memory_key = EXCLUDED.memory_key,
                    content = EXCLUDED.content,
                    confidence = EXCLUDED.confidence,
                    source = EXCLUDED.source,
                    embedding = EXCLUDED.embedding,
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, memoryId, userId, memoryType, memoryKey, content,
                confidence, source, vectorStr);
    }

    public List<RetrievalResult> search(String userId, float[] queryVec, int topK) {
        String vectorStr = toVectorString(queryVec);
        String sql = """
                SELECT memory_id, memory_type, memory_key, content, confidence, source,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM memory_vectors
                WHERE user_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("memoryType", rs.getString("memory_type"));
                    meta.put("memoryKey", rs.getString("memory_key"));
                    meta.put("confidence", rs.getBigDecimal("confidence"));
                    meta.put("source", rs.getString("source"));
                    String title = formatMemoryTitle(rs.getString("memory_type"), rs.getString("memory_key"));
                    return RetrievalResult.memory(
                            String.valueOf(rs.getLong("memory_id")),
                            title,
                            rs.getString("content"),
                            rs.getDouble("similarity") * rs.getBigDecimal("confidence").doubleValue(),
                            meta
                    );
                },
                vectorStr, userId, vectorStr, topK
        );
    }

    public List<RetrievalResult> keywordSearch(String userId, String query, int topK) {
        String sql = """
                SELECT memory_id, memory_type, memory_key, content, confidence, source,
                       GREATEST(similarity(memory_key, ?), similarity(content, ?)) AS sim
                FROM memory_vectors
                WHERE user_id = ?
                  AND (memory_key % ? OR content % ?)
                ORDER BY sim DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("memoryType", rs.getString("memory_type"));
                    meta.put("memoryKey", rs.getString("memory_key"));
                    meta.put("confidence", rs.getBigDecimal("confidence"));
                    meta.put("source", rs.getString("source"));
                    String title = formatMemoryTitle(rs.getString("memory_type"), rs.getString("memory_key"));
                    return RetrievalResult.memory(
                            String.valueOf(rs.getLong("memory_id")),
                            title,
                            rs.getString("content"),
                            rs.getDouble("sim") * rs.getBigDecimal("confidence").doubleValue(),
                            meta
                    );
                },
                query, query, userId, query, query, topK
        );
    }

    public void delete(Long memoryId) {
        jdbcTemplate.update("DELETE FROM memory_vectors WHERE memory_id = ?", memoryId);
    }

    public void deleteByUserId(String userId) {
        jdbcTemplate.update("DELETE FROM memory_vectors WHERE user_id = ?", userId);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM memory_vectors", Integer.class
        );
        return count != null ? count : 0;
    }

    public int countByUser(String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM memory_vectors WHERE user_id = ?", Integer.class, userId
        );
        return count != null ? count : 0;
    }

    public List<RetrievalResult> loadProfileMemories(String userId) {
        String sql = """
                SELECT memory_id, memory_type, memory_key, content, confidence, source
                FROM memory_vectors
                WHERE user_id = ?
                  AND memory_type IN ('PREFERENCE', 'FACT')
                ORDER BY confidence DESC, updated_at DESC
                LIMIT 10
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("memoryType", rs.getString("memory_type"));
                    meta.put("memoryKey", rs.getString("memory_key"));
                    meta.put("confidence", rs.getBigDecimal("confidence"));
                    meta.put("source", rs.getString("source"));
                    String title = formatMemoryTitle(rs.getString("memory_type"), rs.getString("memory_key"));
                    return RetrievalResult.memory(
                            String.valueOf(rs.getLong("memory_id")),
                            title,
                            rs.getString("content"),
                            rs.getBigDecimal("confidence").doubleValue(),
                            meta
                    );
                },
                userId
        );
    }

    private String formatMemoryTitle(String type, String key) {
        String typeLabel = switch (type) {
            case "PREFERENCE" -> "偏好";
            case "FACT" -> "事实";
            case "BEHAVIOR" -> "行为模式";
            case "TASK" -> "任务";
            case "SKILL" -> "技能";
            case "EVENT" -> "事件";
            default -> type;
        };
        String keyLabel = switch (key) {
            case "preferred_format" -> "偏好格式";
            case "major" -> "专业";
            case "top_category" -> "主要兴趣分类";
            case "current_task" -> "最近任务";
            case "preferred_language" -> "偏好语言";
            default -> key;
        };
        return typeLabel + ": " + keyLabel;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
