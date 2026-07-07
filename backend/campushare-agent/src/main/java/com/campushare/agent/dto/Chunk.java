package com.campushare.agent.dto;

/**
 * Markdown 分块结果。
 *
 * @param text        分块文本（已拼接 headingPath + 正文）
 * @param tokenCount  分块 token 数（jtokkit cl100k_base 计数）
 * @param headingPath 标题路径（如 "# 如何发帖 > ## 填写标题"），用于检索时展示上下文
 */
public record Chunk(
        String text,
        int tokenCount,
        String headingPath
) {
    /**
     * 用于 embedding 的文本：headingPath + 换行 + 正文。
     */
    public String embeddingText() {
        if (headingPath == null || headingPath.isBlank()) {
            return text;
        }
        return headingPath + "\n" + text;
    }
}
