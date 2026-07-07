package com.campushare.agent.service;

import com.campushare.agent.dto.Chunk;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 结构分块器。
 *
 * 分块策略（遵循 project_memory 硬约束）：
 * 1. 按 ^## 二级标题分割为 H2 段
 * 2. 每个 H2 段按 ^### 三级标题分割为 H3 段
 * 3. 每个 H3 段按 \n\n 分割为段落
 * 4. 段落 token 计数：
 *    - ≤ maxTokens(512) → 累积
 *    - > maxTokens → 按句子 [。！？.!?] 拆分
 * 5. 累积达 targetTokens(256) → 输出 chunk
 * 6. 相邻 chunk 保留 overlapTokens(50) token 重叠
 *
 * token 计数：jtokkit cl100k_base（近似 BGE-M3，中文高估 10-15%，作为分块控制可接受）
 */
@Slf4j
@Component
public class MarkdownChunker implements KnowledgeChunker {

    private final int targetTokens;
    private final int maxTokens;
    private final int overlapTokens;
    private final Encoding encoding;

    private static final Pattern H1_PATTERN = Pattern.compile("(?m)^#\\s+(.+)$");
    private static final Pattern H2_BOUNDARY = Pattern.compile("(?m)^(?=##\\s)");
    private static final Pattern H3_BOUNDARY = Pattern.compile("(?m)^(?=###\\s)");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？.!?])");

    public MarkdownChunker(
            @Value("${app.knowledge.chunk.target-tokens:256}") int targetTokens,
            @Value("${app.knowledge.chunk.max-tokens:512}") int maxTokens,
            @Value("${app.knowledge.chunk.overlap-tokens:50}") int overlapTokens
    ) {
        this.targetTokens = targetTokens;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    @Override
    public List<Chunk> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        String trimmed = markdown.trim();
        String docTitle = extractH1(trimmed);
        List<Paragraph> paragraphs = parseParagraphs(trimmed, docTitle);

        if (paragraphs.isEmpty()) {
            int tokens = countTokens(trimmed);
            return List.of(new Chunk(trimmed, tokens, docTitle));
        }

        List<Chunk> chunks = new ArrayList<>();
        Accumulator acc = new Accumulator();

        for (Paragraph p : paragraphs) {
            if (p.tokenCount > maxTokens) {
                flush(acc, chunks);
                splitBySentence(p, chunks);
            } else if (acc.headingPath != null && !acc.headingPath.equals(p.headingPath)) {
                flush(acc, chunks);
                acc.add(p);
            } else if (acc.tokenCount + p.tokenCount > maxTokens) {
                flush(acc, chunks);
                acc.add(p);
            } else {
                acc.add(p);
            }

            if (acc.tokenCount >= targetTokens) {
                flush(acc, chunks);
            }
        }
        flush(acc, chunks);

        return applyOverlap(chunks);
    }

    private String extractH1(String markdown) {
        var matcher = H1_PATTERN.matcher(markdown);
        if (matcher.find()) {
            return "# " + matcher.group(1).trim();
        }
        return "";
    }

    private record Paragraph(String text, int tokenCount, String headingPath) {}

    private List<Paragraph> parseParagraphs(String markdown, String docTitle) {
        List<Paragraph> paragraphs = new ArrayList<>();

        String[] h2Sections = H2_BOUNDARY.split(markdown, -1);

        for (String h2Section : h2Sections) {
            if (h2Section.isBlank()) continue;

            String h2Title = extractFirstHeading(h2Section, "## ");
            String h2Body = removeFirstHeading(h2Section);
            String h2Path = buildHeadingPath(docTitle, h2Title, null);

            if (h2Title.isEmpty()) {
                // 首个 ## 之前的内容（文档标题 + 简介）
                addParagraphs(h2Section, docTitle, paragraphs);
                continue;
            }

            String[] h3Sections = H3_BOUNDARY.split(h2Body, -1);

            for (String h3Section : h3Sections) {
                if (h3Section.isBlank()) continue;

                String h3Title = extractFirstHeading(h3Section, "### ");
                String h3Body = removeFirstHeading(h3Section);
                String h3Path = buildHeadingPath(docTitle, h2Title, h3Title);

                if (h3Title.isEmpty()) {
                    addParagraphs(h3Section, h2Path, paragraphs);
                    continue;
                }

                addParagraphs(h3Body, h3Path, paragraphs);
            }
        }

        return paragraphs;
    }

    private void addParagraphs(String body, String headingPath, List<Paragraph> out) {
        String[] parts = body.split("\\n\\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(new Paragraph(trimmed, countTokens(trimmed), headingPath));
            }
        }
    }

    private String extractFirstHeading(String section, String prefix) {
        String trimmed = section.trim();
        if (trimmed.startsWith(prefix)) {
            int newlineIdx = trimmed.indexOf('\n');
            if (newlineIdx > 0) {
                return trimmed.substring(0, newlineIdx).trim();
            }
            return trimmed;
        }
        return "";
    }

    private String removeFirstHeading(String section) {
        String trimmed = section.trim();
        int newlineIdx = trimmed.indexOf('\n');
        if (newlineIdx > 0) {
            return trimmed.substring(newlineIdx + 1);
        }
        return "";
    }

    private String buildHeadingPath(String docTitle, String h2Title, String h3Title) {
        StringBuilder sb = new StringBuilder();
        if (docTitle != null && !docTitle.isEmpty()) {
            sb.append(docTitle);
        }
        if (h2Title != null && !h2Title.isEmpty()) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(h2Title);
        }
        if (h3Title != null && !h3Title.isEmpty()) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(h3Title);
        }
        return sb.toString();
    }

    private void splitBySentence(Paragraph p, List<Chunk> out) {
        String[] sentences = SENTENCE_BOUNDARY.split(p.text);
        Accumulator acc = new Accumulator();
        acc.headingPath = p.headingPath;

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            int tokens = countTokens(trimmed);
            if (tokens > maxTokens) {
                // 单句超长，强制作为独立 chunk（不再拆分）
                flush(acc, out);
                out.add(new Chunk(trimmed, tokens, p.headingPath));
            } else if (acc.tokenCount + tokens > maxTokens) {
                flush(acc, out);
                acc.add(new Paragraph(trimmed, tokens, p.headingPath));
            } else {
                acc.add(new Paragraph(trimmed, tokens, p.headingPath));
            }

            if (acc.tokenCount >= targetTokens) {
                flush(acc, out);
            }
        }
        flush(acc, out);
    }

    private void flush(Accumulator acc, List<Chunk> out) {
        if (acc.isEmpty()) return;
        String text = String.join("\n\n", acc.parts);
        out.add(new Chunk(text, acc.tokenCount, acc.headingPath));
        acc.reset();
    }

    private List<Chunk> applyOverlap(List<Chunk> chunks) {
        if (overlapTokens <= 0 || chunks.size() < 2) {
            return chunks;
        }

        List<Chunk> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            Chunk prev = chunks.get(i - 1);
            Chunk curr = chunks.get(i);

            String overlapText = takeTailTokens(prev.text(), overlapTokens);
            if (!overlapText.isEmpty()) {
                String mergedText = overlapText + "\n\n" + curr.text();
                int mergedTokens = countTokens(mergedText);
                result.add(new Chunk(mergedText, mergedTokens, curr.headingPath()));
            } else {
                result.add(curr);
            }
        }
        return result;
    }

    private String takeTailTokens(String text, int n) {
        IntArrayList tokens = encoding.encode(text);
        if (tokens.size() <= n) {
            return text;
        }
        IntArrayList tail = new IntArrayList(n);
        for (int i = tokens.size() - n; i < tokens.size(); i++) {
            tail.add(tokens.get(i));
        }
        try {
            return encoding.decode(tail);
        } catch (Exception e) {
            log.warn("Failed to decode tail tokens for overlap, skipping overlap", e);
            return "";
        }
    }

    private int countTokens(String text) {
        return encoding.countTokens(text);
    }

    private static class Accumulator {
        final List<String> parts = new ArrayList<>();
        int tokenCount = 0;
        String headingPath = null;

        void add(Paragraph p) {
            parts.add(p.text);
            tokenCount += p.tokenCount;
            if (headingPath == null) {
                headingPath = p.headingPath;
            }
        }

        boolean isEmpty() {
            return parts.isEmpty();
        }

        void reset() {
            parts.clear();
            tokenCount = 0;
            headingPath = null;
        }
    }
}
