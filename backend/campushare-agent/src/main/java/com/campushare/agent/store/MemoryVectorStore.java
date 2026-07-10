package com.campushare.agent.store;

import com.campushare.agent.dto.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
                       String memoryValue, BigDecimal confidence, String source, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        String sql = """
                INSERT INTO memory_vectors (id, user_id, memory_type, memory_key, memory_value,
                    confidence, source, embedding, access_count, is_active, decay_score,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, 0, TRUE, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    memory_type = EXCLUDED.memory_type,
                    memory_key = EXCLUDED.memory_key,
                    memory_value = EXCLUDED.memory_value,
                    confidence = EXCLUDED.confidence,
                    source = EXCLUDED.source,
                    embedding = EXCLUDED.embedding,
                    decay_score = EXCLUDED.decay_score,
                    updated_at = CURRENT_TIMESTAMP
                """;
        double decayScore = confidence != null ? confidence.doubleValue() : 1.0;
        jdbcTemplate.update(sql, String.valueOf(memoryId), userId, memoryType, memoryKey,
                memoryValue, confidence, source, vectorStr, decayScore);
    }

    public List<RetrievalResult> search(String userId, float[] queryVec, int topK) {
        String vectorStr = toVectorString(queryVec);
        String sql = """
                SELECT id, memory_type, memory_key, memory_value, confidence, source,
                       access_count, decay_score,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM memory_vectors
                WHERE user_id = ? AND is_active = TRUE
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
                    meta.put("accessCount", rs.getInt("access_count"));
                    String title = formatMemoryTitle(rs.getString("memory_type"), rs.getString("memory_key"));
                    double decayScore = rs.getDouble("decay_score");
                    double rawSim = rs.getDouble("similarity");
                    return RetrievalResult.memory(
                            rs.getString("id"),
                            title,
                            rs.getString("memory_value"),
                            rawSim * decayScore,
                            meta
                    );
                },
                vectorStr, userId, vectorStr, topK
        );
    }

    public List<RetrievalResult> keywordSearch(String userId, String query, int topK) {
        String sql = """
                SELECT id, memory_type, memory_key, memory_value, confidence, source,
                       access_count, decay_score,
                       GREATEST(similarity(memory_key, ?), similarity(memory_value, ?)) AS sim
                FROM memory_vectors
                WHERE user_id = ? AND is_active = TRUE
                  AND (memory_key % ? OR memory_value % ?)
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
                    meta.put("accessCount", rs.getInt("access_count"));
                    String title = formatMemoryTitle(rs.getString("memory_type"), rs.getString("memory_key"));
                    double decayScore = rs.getDouble("decay_score");
                    double rawSim = rs.getDouble("sim");
                    return RetrievalResult.memory(
                            rs.getString("id"),
                            title,
                            rs.getString("memory_value"),
                            rawSim * decayScore,
                            meta
                    );
                },
                query, query, userId, query, query, topK
        );
    }

    public void delete(Long memoryId) {
        String sql = "UPDATE memory_vectors SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        jdbcTemplate.update(sql, String.valueOf(memoryId));
    }

    public void hardDelete(Long memoryId) {
        jdbcTemplate.update("DELETE FROM memory_vectors WHERE id = ?", String.valueOf(memoryId));
    }

    public void deleteByUserId(String userId) {
        jdbcTemplate.update("UPDATE memory_vectors SET is_active = FALSE WHERE user_id = ?", userId);
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM memory_vectors WHERE is_active = TRUE", Integer.class
        );
        return count != null ? count : 0;
    }

    public int countByUser(String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM memory_vectors WHERE user_id = ? AND is_active = TRUE",
                Integer.class, userId
        );
        return count != null ? count : 0;
    }

    public void updateDecayScore(Long memoryId, double decayScore) {
        String sql = "UPDATE memory_vectors SET decay_score = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        jdbcTemplate.update(sql, decayScore, String.valueOf(memoryId));
    }

    public void recordAccess(Long memoryId) {
        String sql = """
                UPDATE memory_vectors
                SET access_count = COALESCE(access_count, 0) + 1,
                    last_accessed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, String.valueOf(memoryId));
    }

    public List<RetrievalResult> loadProfileMemories(String userId) {
        String sql = """
                SELECT id, memory_type, memory_key, memory_value, confidence, source, decay_score
                FROM memory_vectors
                WHERE user_id = ? AND is_active = TRUE
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
                    double decayScore = rs.getDouble("decay_score");
                    BigDecimal conf = rs.getBigDecimal("confidence");
                    return RetrievalResult.memory(
                            rs.getString("id"),
                            title,
                            rs.getString("memory_value"),
                            conf != null ? conf.doubleValue() * decayScore : decayScore,
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
