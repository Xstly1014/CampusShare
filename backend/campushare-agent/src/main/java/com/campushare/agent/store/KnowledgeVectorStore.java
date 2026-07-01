package com.campushare.agent.store;

import com.campushare.agent.dto.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库向量存储，操作 PostgreSQL knowledge_vectors 表。
 *
 * 两类检索：
 * - search(float[], int): 向量相似度检索（HNSW + 余弦距离 <=>）
 * - keywordSearch(String, int): 关键词检索（pg_trgm + GIN 索引）
 */
@Slf4j
@Component
public class KnowledgeVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入或更新知识库向量。
     */
    public void upsert(Long articleId, String title, String topic, String excerpt,
                       String md5, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        String sql = """
                INSERT INTO knowledge_vectors (article_id, title, topic, content_excerpt, content_md5, status, version, embedding, embedding_model, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 1, ?::vector, 'bge-m3', CURRENT_TIMESTAMP)
                ON CONFLICT (article_id) DO UPDATE SET
                    title = EXCLUDED.title,
                    topic = EXCLUDED.topic,
                    content_excerpt = EXCLUDED.content_excerpt,
                    content_md5 = EXCLUDED.content_md5,
                    embedding = EXCLUDED.embedding,
                    embedding_model = EXCLUDED.embedding_model,
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, articleId, title, topic, excerpt, md5, vectorStr);
    }

    /**
     * 向量相似度检索（余弦距离）。
     * 返回余弦相似度（1 - distance），越大越相关。
     */
    public List<RetrievalResult> search(float[] queryVec, int topK) {
        String vectorStr = toVectorString(queryVec);
        String sql = """
                SELECT article_id, title, content_excerpt, topic,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_vectors
                WHERE status = 'PUBLISHED'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("topic", rs.getString("topic"));
                    return RetrievalResult.knowledge(
                            String.valueOf(rs.getLong("article_id")),
                            rs.getString("title"),
                            rs.getString("content_excerpt"),
                            rs.getDouble("similarity"),
                            meta
                    );
                },
                vectorStr, vectorStr, topK
        );
    }

    /**
     * 关键词检索（pg_trgm 三元组模糊匹配）。
     * 在 title 和 content_excerpt 上使用 trigram 相似度。
     */
    public List<RetrievalResult> keywordSearch(String query, int topK) {
        String sql = """
                SELECT article_id, title, content_excerpt, topic,
                       GREATEST(similarity(title, ?), similarity(content_excerpt, ?)) AS sim
                FROM knowledge_vectors
                WHERE status = 'PUBLISHED'
                  AND (title % ? OR content_excerpt % ?)
                ORDER BY sim DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("topic", rs.getString("topic"));
                    return RetrievalResult.knowledge(
                            String.valueOf(rs.getLong("article_id")),
                            rs.getString("title"),
                            rs.getString("content_excerpt"),
                            rs.getDouble("sim"),
                            meta
                    );
                },
                query, query, query, query, topK
        );
    }

    /**
     * 查询知识库向量总数。
     */
    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM knowledge_vectors WHERE status = 'PUBLISHED'",
                Integer.class
        );
        return count != null ? count : 0;
    }

    /**
     * 将 float[] 转为 pgvector 字符串格式：[0.1,0.2,...]
     */
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
