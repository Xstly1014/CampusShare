package com.campushare.agent.service;

import com.campushare.agent.dto.Chunk;

import java.util.List;

/**
 * 知识库分块器接口。
 *
 * 实现类：
 * - MarkdownChunker：按 Markdown 结构（H2→H3→段落→句子）分块
 *
 * 未来扩展：
 * - LatexChunker / HtmlChunker 等
 */
public interface KnowledgeChunker {

    /**
     * 将文档正文分块。
     *
     * @param markdown 文档正文（Markdown，已去除 frontmatter）
     * @return 分块列表（按文档顺序）
     */
    List<Chunk> chunk(String markdown);
}
