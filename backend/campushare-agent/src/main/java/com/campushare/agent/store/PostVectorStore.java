package com.campushare.agent.store;

import com.campushare.agent.dto.IntentResult.SlotResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.util.SchoolNameUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
                       String categoryId, String categoryName, String schoolId, String schoolName,
                       String authorId, Boolean authorVerified,
                       int likeCount, int viewCount, java.time.LocalDateTime createdAt,
                       float[] embedding) {
        String vectorStr = toVectorString(embedding);
        String sql = """
                INSERT INTO post_vectors (post_id, post_title, post_content_excerpt, post_type,
                    category, category_name, school, school_name, author_id, author_verified,
                    like_count, view_count, created_at, embedding, embedding_model, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, 'bge-m3', CURRENT_TIMESTAMP)
                ON CONFLICT (post_id) DO UPDATE SET
                    post_title = EXCLUDED.post_title,
                    post_content_excerpt = EXCLUDED.post_content_excerpt,
                    post_type = EXCLUDED.post_type,
                    category = EXCLUDED.category,
                    category_name = EXCLUDED.category_name,
                    school = EXCLUDED.school,
                    school_name = EXCLUDED.school_name,
                    author_id = EXCLUDED.author_id,
                    author_verified = EXCLUDED.author_verified,
                    like_count = EXCLUDED.like_count,
                    view_count = EXCLUDED.view_count,
                    created_at = EXCLUDED.created_at,
                    embedding = EXCLUDED.embedding,
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, postId, title, contentExcerpt, postType,
                categoryId, categoryName, schoolId, schoolName,
                authorId, authorVerified, likeCount, viewCount, createdAt, vectorStr);
    }

    /**
     * 向量相似度检索。
     */
    public List<RetrievalResult> search(float[] queryVec, int topK) {
        String vectorStr = toVectorString(queryVec);
        String sql = """
                SELECT post_id, post_title, post_content_excerpt, post_type,
                       category, category_name, school, school_name,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM post_vectors
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("postType", rs.getString("post_type"));
                    meta.put("category", rs.getString("category_name"));
                    meta.put("categoryId", rs.getString("category"));
                    meta.put("school", rs.getString("school_name"));
                    meta.put("schoolId", rs.getString("school"));
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
     * 当前 category_name/school_name 已存储中文名称，支持 SQL 等值/ILIKE 过滤。
     *
     * @param queryVec 查询向量
     * @param topK 返回数量
     * @param slots 槽位过滤条件（可为 null）
     */
    public List<RetrievalResult> search(float[] queryVec, int topK, SlotResult slots) {
        String normalizedSchool = null;
        if (slots != null && slots.getSchool() != null) {
            normalizedSchool = SchoolNameUtils.normalize(slots.getSchool());
            if (normalizedSchool == null) {
                normalizedSchool = slots.getSchool();
            }
        }
        String category = slots != null ? slots.getCategory() : null;
        String postType = slots != null ? slots.getPostType() : null;

        boolean hasSchool = normalizedSchool != null && !normalizedSchool.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasPostType = postType != null && !postType.isBlank();

        if (!hasSchool && !hasCategory && !hasPostType) {
            return search(queryVec, topK);
        }

        List<RetrievalResult> filtered = doSearchWithFilters(queryVec, topK * 2, normalizedSchool, category, postType);

        if (filtered.isEmpty() && hasSchool) {
            log.warn("Post vector search with school filter returned 0 results, falling back to no-school filter for query");
            filtered = doSearchWithFilters(queryVec, topK, null, category, postType);
        }

        if (filtered.size() > topK) {
            filtered = new ArrayList<>(filtered.subList(0, topK));
        }
        return filtered;
    }

    private List<RetrievalResult> doSearchWithFilters(float[] queryVec, int topK,
                                                      String school, String category, String postType) {
        StringBuilder sql = new StringBuilder("""
                SELECT post_id, post_title, post_content_excerpt, post_type,
                       category, category_name, school, school_name,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM post_vectors
                WHERE 1=1
                """);

        if (school != null && !school.isBlank()) {
            sql.append(" AND school_name ILIKE ?");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category_name ILIKE ?");
        }
        if (postType != null && !postType.isBlank()) {
            sql.append(" AND post_type = ?");
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");

        List<Object> params = new ArrayList<>();
        params.add(toVectorString(queryVec));
        if (school != null && !school.isBlank()) {
            params.add("%" + school + "%");
        }
        if (category != null && !category.isBlank()) {
            params.add("%" + category + "%");
        }
        if (postType != null && !postType.isBlank()) {
            params.add(postType);
        }
        params.add(toVectorString(queryVec));
        params.add(topK);

        log.debug("Post vector search with filters: school={}, category={}, postType={}",
                school, category, postType);

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("postType", rs.getString("post_type"));
                    meta.put("category", rs.getString("category_name"));
                    meta.put("categoryId", rs.getString("category"));
                    meta.put("school", rs.getString("school_name"));
                    meta.put("schoolId", rs.getString("school"));
                    return RetrievalResult.post(
                            rs.getString("post_id"),
                            rs.getString("post_title"),
                            rs.getString("post_content_excerpt"),
                            rs.getDouble("similarity"),
                            meta
                    );
                },
                params.toArray()
        );
    }

    /**
     * 关键词检索。
     */
    public List<RetrievalResult> keywordSearch(String query, int topK) {
        String sql = """
                SELECT post_id, post_title, post_content_excerpt, post_type,
                       category, category_name, school, school_name,
                       GREATEST(similarity(post_title, ?), similarity(post_content_excerpt, ?)) AS sim
                FROM post_vectors
                WHERE post_title % ? OR post_content_excerpt % ?
                ORDER BY sim DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("postType", rs.getString("post_type"));
                    meta.put("category", rs.getString("category_name"));
                    meta.put("categoryId", rs.getString("category"));
                    meta.put("school", rs.getString("school_name"));
                    meta.put("schoolId", rs.getString("school"));
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
     * 关键词检索（带 slots 过滤）。
     */
    public List<RetrievalResult> keywordSearch(String query, int topK, SlotResult slots) {
        String normalizedSchool = null;
        if (slots != null && slots.getSchool() != null) {
            normalizedSchool = SchoolNameUtils.normalize(slots.getSchool());
            if (normalizedSchool == null) {
                normalizedSchool = slots.getSchool();
            }
        }
        String category = slots != null ? slots.getCategory() : null;
        String postType = slots != null ? slots.getPostType() : null;

        boolean hasSchool = normalizedSchool != null && !normalizedSchool.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasPostType = postType != null && !postType.isBlank();

        if (!hasSchool && !hasCategory && !hasPostType) {
            return keywordSearch(query, topK);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT post_id, post_title, post_content_excerpt, post_type,
                       category, category_name, school, school_name,
                       GREATEST(similarity(post_title, ?), similarity(post_content_excerpt, ?)) AS sim
                FROM post_vectors
                WHERE (post_title % ? OR post_content_excerpt % ?)
                """);

        if (hasSchool) {
            sql.append(" AND school_name ILIKE ?");
        }
        if (hasCategory) {
            sql.append(" AND category_name ILIKE ?");
        }
        if (hasPostType) {
            sql.append(" AND post_type = ?");
        }
        sql.append(" ORDER BY sim DESC LIMIT ?");

        List<Object> params = new ArrayList<>();
        params.add(query);
        params.add(query);
        params.add(query);
        params.add(query);
        if (hasSchool) {
            params.add("%" + normalizedSchool + "%");
        }
        if (hasCategory) {
            params.add("%" + category + "%");
        }
        if (hasPostType) {
            params.add(postType);
        }
        params.add(topK);

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("postType", rs.getString("post_type"));
                    meta.put("category", rs.getString("category_name"));
                    meta.put("categoryId", rs.getString("category"));
                    meta.put("school", rs.getString("school_name"));
                    meta.put("schoolId", rs.getString("school"));
                    return RetrievalResult.post(
                            rs.getString("post_id"),
                            rs.getString("post_title"),
                            rs.getString("post_content_excerpt"),
                            rs.getDouble("sim"),
                            meta
                    );
                },
                params.toArray()
        );
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
}
