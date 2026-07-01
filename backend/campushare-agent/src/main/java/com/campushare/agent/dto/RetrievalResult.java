package com.campushare.agent.dto;

import java.util.Map;

/**
 * 检索结果统一结构。
 *
 * source 标识来源：
 * - KNOWLEDGE: 知识库文档（knowledge_vectors 表）
 * - POST: 帖子内容（post_vectors 表）
 *
 * score 含义取决于检索方式：
 * - 向量检索：余弦相似度（1 - cosine_distance），越大越相关
 * - 关键词检索：pg_trgm 相似度，越大越相关
 * - RRF 融合后：RRF 分数，越大越相关
 */
public record RetrievalResult(
        String id,
        String title,
        String content,
        double score,
        Source source,
        Map<String, Object> metadata
) {
    public enum Source {
        KNOWLEDGE,
        POST
    }

    public static RetrievalResult knowledge(String id, String title, String content, double score, Map<String, Object> metadata) {
        return new RetrievalResult(id, title, content, score, Source.KNOWLEDGE, metadata);
    }

    public static RetrievalResult post(String id, String title, String content, double score, Map<String, Object> metadata) {
        return new RetrievalResult(id, title, content, score, Source.POST, metadata);
    }
}
