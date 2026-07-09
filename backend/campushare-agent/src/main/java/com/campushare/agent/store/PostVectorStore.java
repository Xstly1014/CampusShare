package com.campushare.agent.store;

import com.campushare.agent.dto.IntentResult.SlotResult;
import com.campushare.agent.dto.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子向量存储，操作 PostgreSQL post_vectors 表。
 *
 * 帖子数据由 post-service 通过内部 API 通知后同步入库。
 * 检索方式与 KnowledgeVectorStore 一致：向量相似度 + pg_trgm 关键词。
 */
@Slf4j
@Component
public class PostVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public PostVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入或更新帖子向量。
     */
    public void upsert(String postId, String title, String contentExcerpt, String postType,
                       String category, String school, String authorId, Boolean authorVerified,
                       int likeCount, int viewCount, java.time.LocalDateTime createdAt,
                       float[] embedding) {
        String vectorStr = toVectorString(embedding);
        String sql = """
                INSERT INTO post_vectors (post_id, post_title, post_content_excerpt, post_type,
                    category, school, author_id, author_verified, like_count, view_count,
                    created_at, embedding, embedding_model, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, 'bge-m3', CURRENT_TIMESTAMP)
                ON CONFLICT (post_id) DO UPDATE SET
                    post_title = EXCLUDED.post_title,
                    post_content_excerpt = EXCLUDED.post_content_excerpt,
                    post_type = EXCLUDED.post_type,
                    category = EXCLUDED.category,
                    school = EXCLUDED.school,
                    author_id = EXCLUDED.author_id,
                    author_verified = EXCLUDED.author_verified,
                    like_count = EXCLUDED.like_count,
                    view_count = EXCLUDED.view_count,
                    created_at = EXCLUDED.created_at,
                    embedding = EXCLUDED.embedding,
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, postId, title, contentExcerpt, postType, category, school,
                authorId, authorVerified, likeCount, viewCount, createdAt, vectorStr);
    }

    /**
     * 向量相似度检索。
     */
    public List<RetrievalResult> search(float[] queryVec, int topK) {
        String vectorStr = toVectorString(queryVec);
        String sql = """
                SELECT post_id, post_title, post_content_excerpt, category, school,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM post_vectors
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("category", rs.getString("category"));
                    meta.put("school", rs.getString("school"));
                    return RetrievalResult.post(
                            rs.getString("post_id"),
                            rs.getString("post_title"),
                            rs.getString("post_content_excerpt"),
                            rs.getDouble("similarity"),
                            meta
                    );
                },
                vectorStr, vectorStr, topK
        );
    }

    /**
     * 向量相似度检索（带 slots 过滤，ADR-025）。
     *
     * 注意：当前 post_vectors 表中 category/school 字段存储的是 UUID，
     * 而 LLM 解析出的 slots 中 school/category 是中文名称字符串，
     * SQL 等值过滤会导致查不到结果。暂时禁用 SQL 层面的等值过滤，
     * 仅扩大 topK 取更多结果，由向量相似度+关键词排序保证相关性。
     * TODO: 待 post-service 返回 schoolName/categoryName 后恢复过滤。
     *
     * @param queryVec 查询向量
     * @param topK 返回数量
     * @param slots 槽位过滤条件（可为 null）
     */
    public List<RetrievalResult> search(float[] queryVec, int topK, SlotResult slots) {
        if (slots == null || (isBlank(slots.getSchool())
                && isBlank(slots.getCategory())
                && isBlank(slots.getPostType()))) {
            return search(queryVec, topK);
        }
        int expandedTopK = Math.min(topK * 3, 50);
        log.debug("Post vector search with slots: school={}, category={}, postType={}, expandedTopK={}",
                slots.getSchool(), slots.getCategory(), slots.getPostType(), expandedTopK);
        return search(queryVec, expandedTopK);
    }

    /**
     * 关键词检索。
     */
    public List<RetrievalResult> keywordSearch(String query, int topK) {
        String sql = """
                SELECT post_id, post_title, post_content_excerpt, category, school,
                       GREATEST(similarity(post_title, ?), similarity(post_content_excerpt, ?)) AS sim
                FROM post_vectors
                WHERE post_title % ? OR post_content_excerpt % ?
                ORDER BY sim DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("category", rs.getString("category"));
                    meta.put("school", rs.getString("school"));
                    return RetrievalResult.post(
                            rs.getString("post_id"),
                            rs.getString("post_title"),
                            rs.getString("post_content_excerpt"),
                            rs.getDouble("sim"),
                            meta
                    );
                },
                query, query, query, query, topK
        );
    }

    /**
     * 关键词检索（带 slots 过滤，ADR-025）。
     * 暂时禁用 SQL 等值过滤（原因同向量检索），仅扩大 topK。
     *
     * @param query 查询文本
     * @param topK 返回数量
     * @param slots 槽位过滤条件（可为 null）
     */
    public List<RetrievalResult> keywordSearch(String query, int topK, SlotResult slots) {
        if (slots == null || (isBlank(slots.getSchool())
                && isBlank(slots.getCategory())
                && isBlank(slots.getPostType()))) {
            return keywordSearch(query, topK);
        }
        int expandedTopK = Math.min(topK * 3, 50);
        return keywordSearch(query, expandedTopK);
    }

    /**
     * 删除帖子向量。
     */
    public void delete(String postId) {
        jdbcTemplate.update("DELETE FROM post_vectors WHERE post_id = ?", postId);
    }

    /**
     * 查询帖子向量总数。
     */
    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM post_vectors", Integer.class
        );
        return count != null ? count : 0;
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
