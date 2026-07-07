package com.campushare.agent.store;

import com.campushare.agent.dto.Chunk;
import com.campushare.agent.dto.ChunkResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库向量存储，操作 PostgreSQL knowledge_vectors 表（分块版 v2）。
 *
 * 核心方法：
 * - upsertChunks：先删后插，支持一文档多分块
 * - searchChunks：向量相似度检索（HNSW + 余弦距离），返回分块级结果
 * - keywordSearchChunks：关键词检索（pg_trgm + GIN）
 * - findSimilar：重复检测用，仅查 chunk_index=0 的代表性分块
 * - incrementRecall / updateQualityScore：召回统计与质量评分更新
 */
@Slf4j
@Component
public class KnowledgeVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeVectorStore(@Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入或更新知识库分块向量（先删后插）。
     * 文档更新后分块数可能变化，ON CONFLICT 无法清理多余旧分块，故用先删后插。
     */
    public void upsertChunks(Long articleId, List<Chunk> chunks, List<float[]> embeddings,
                             String title, String topic, String md5, String version,
                             double qualityScore) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks size " + chunks.size() + " != embeddings size " + embeddings.size());
        }

        jdbcTemplate.update("DELETE FROM knowledge_vectors WHERE article_id = ?", articleId);

        if (chunks.isEmpty()) {
            log.warn("No chunks to upsert for articleId={}", articleId);
            return;
        }

        String sql = """
                INSERT INTO knowledge_vectors (
                    article_id, chunk_index, title, topic, chunk_content, heading_path,
                    content_md5, status, version, quality_score, token_count,
                    embedding, embedding_model, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?::vector, 'bge-m3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
        List<Object[]> batchArgs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            float[] emb = embeddings.get(i);
            batchArgs.add(new Object[]{
                    articleId, i, title, topic, chunk.text(), chunk.headingPath(),
                    md5, version, qualityScore, chunk.tokenCount(),
                    toVectorString(emb)
            });
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    /**
     * 删除指定文章的所有分块向量。
     */
    public void deleteByArticleId(Long articleId) {
        jdbcTemplate.update("DELETE FROM knowledge_vectors WHERE article_id = ?", articleId);
    }

    /**
     * 向量相似度检索（分块级，余弦距离）。
     */
    public List<ChunkResult> searchChunks(float[] queryVec, int topK) {
        String vectorStr = toVectorString(queryVec);
        String sql = """
                SELECT article_id, chunk_index, title, chunk_content, heading_path, topic,
                       1 - (embedding <=> ?::vector) AS similarity, quality_score
                FROM knowledge_vectors
                WHERE status = 'PUBLISHED'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ChunkResult(
                        rs.getLong("article_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("title"),
                        rs.getString("chunk_content"),
                        rs.getString("heading_path"),
                        rs.getString("topic"),
                        rs.getDouble("similarity"),
                        rs.getDouble("quality_score")
                ),
                vectorStr, vectorStr, topK
        );
    }

    /**
     * 关键词检索（pg_trgm 三元组模糊匹配，分块级）。
     */
    public List<ChunkResult> keywordSearchChunks(String query, int topK) {
        String sql = """
                SELECT article_id, chunk_index, title, chunk_content, heading_path, topic,
                       GREATEST(similarity(title, ?), similarity(chunk_content, ?)) AS sim,
                       quality_score
                FROM knowledge_vectors
                WHERE status = 'PUBLISHED'
                  AND (title % ? OR chunk_content % ?)
                ORDER BY sim DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ChunkResult(
                        rs.getLong("article_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("title"),
                        rs.getString("chunk_content"),
                        rs.getString("heading_path"),
                        rs.getString("topic"),
                        rs.getDouble("sim"),
                        rs.getDouble("quality_score")
                ),
                query, query, query, query, topK
        );
    }

    /**
     * 查找与给定 embedding 最相似的文章（仅查 chunk_index=0 的代表性分块）。
     * 用于重复检测，避免 N 次查询。
     */
    public SimilarMatch findSimilar(float[] embedding, int topK) {
        String vectorStr = toVectorString(embedding);
        String sql = """
                SELECT article_id, 1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_vectors
                WHERE chunk_index = 0
                  AND status = 'PUBLISHED'
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        List<SimilarMatch> matches = jdbcTemplate.query(sql,
                (rs, rowNum) -> new SimilarMatch(
                        rs.getLong("article_id"),
                        rs.getDouble("similarity")
                ),
                vectorStr, vectorStr, topK
        );
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * 累加文章的召回次数（检索命中时异步调用）。
     */
    public void incrementRecall(Long articleId) {
        jdbcTemplate.update(
                "UPDATE knowledge_vectors SET recall_count = recall_count + 1, updated_at = CURRENT_TIMESTAMP WHERE article_id = ?",
                articleId
        );
    }

    /**
     * 更新文章所有分块的质量评分。
     */
    public void updateQualityScore(Long articleId, double score) {
        jdbcTemplate.update(
                "UPDATE knowledge_vectors SET quality_score = ?, updated_at = CURRENT_TIMESTAMP WHERE article_id = ?",
                score, articleId
        );
    }

    /**
     * 查询知识库分块向量总数（按 chunk 计）。
     */
    public int count() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM knowledge_vectors WHERE status = 'PUBLISHED'",
                Integer.class
        );
        return count != null ? count : 0;
    }

    /**
     * 查询知识库文章总数（按 article_id 去重）。
     */
    public int countArticles() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT article_id) FROM knowledge_vectors WHERE status = 'PUBLISHED'",
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

    /**
     * 相似度匹配结果（用于重复检测）。
     */
    public record SimilarMatch(Long articleId, double similarity) {}
}
