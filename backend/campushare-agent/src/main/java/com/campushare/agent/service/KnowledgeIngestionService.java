package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.entity.KnowledgeArticle;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.mapper.KnowledgeArticleMapper;
import com.campushare.agent.store.KnowledgeVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 知识库文档摄入服务。
 *
 * 流程：
 * 1. 扫描 docs-path 目录下所有 .md 文件
 * 2. 解析 frontmatter（title/topic/tags）+ 正文
 * 3. 计算 MD5，与 MySQL 中已有记录对比，跳过未变更的
 * 4. 新增/更新的文档：存 MySQL knowledge_articles → embedding → 存 knowledge_vectors
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private final KnowledgeArticleMapper articleMapper;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore knowledgeVectorStore;

    @Value("${app.knowledge.docs-path:docs/agent-assistant/knowledge-docs}")
    private String docsPath;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL
    );

    private static final int EXCERPT_LENGTH = 500;

    /**
     * 摄入全部知识库文档。
     * @return 摄入结果统计
     */
    public Map<String, Object> ingestAll() {
        Map<String, Object> result = new HashMap<>();
        int total = 0, inserted = 0, updated = 0, skipped = 0, failed = 0;

        Path rootPath = Paths.get(docsPath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            log.warn("Knowledge docs path not found: {}", docsPath);
            result.put("error", "Docs path not found: " + docsPath);
            return result;
        }

        List<Path> mdFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(rootPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(mdFiles::add);
        } catch (IOException e) {
            log.error("Failed to scan docs directory", e);
            result.put("error", "Failed to scan: " + e.getMessage());
            return result;
        }

        total = mdFiles.size();
        log.info("Found {} knowledge docs to ingest", total);

        for (Path file : mdFiles) {
            try {
                ParsedDoc doc = parseFile(file);
                if (doc == null) {
                    failed++;
                    continue;
                }

                String md5 = md5(doc.content);

                KnowledgeArticle existing = articleMapper.selectOne(
                        new LambdaQueryWrapper<KnowledgeArticle>()
                                .eq(KnowledgeArticle::getTitle, doc.title)
                                .last("LIMIT 1")
                );

                if (existing != null && md5.equals(existing.getContentMd5())) {
                    skipped++;
                    continue;
                }

                String excerpt = doc.content.length() > EXCERPT_LENGTH
                        ? doc.content.substring(0, EXCERPT_LENGTH)
                        : doc.content;

                float[] embedding = embeddingClient.embed(doc.title + "\n" + excerpt).block();
                if (embedding == null || embedding.length == 0) {
                    log.warn("Embedding failed for doc: {}, skipping vector store", doc.title);
                    failed++;
                    continue;
                }

                if (existing != null) {
                    existing.setTopic(doc.topic);
                    existing.setContent(doc.content);
                    existing.setContentMd5(md5);
                    existing.setStatus("PUBLISHED");
                    existing.setTags(doc.tags);
                    existing.setVersion(existing.getVersion() + 1);
                    articleMapper.updateById(existing);
                    knowledgeVectorStore.upsert(existing.getId(), doc.title, doc.topic, excerpt, md5, embedding);
                    updated++;
                    log.info("Updated knowledge article: {}", doc.title);
                } else {
                    KnowledgeArticle article = KnowledgeArticle.builder()
                            .title(doc.title)
                            .topic(doc.topic)
                            .content(doc.content)
                            .contentMd5(md5)
                            .status("PUBLISHED")
                            .version(1)
                            .tags(doc.tags)
                            .build();
                    articleMapper.insert(article);
                    knowledgeVectorStore.upsert(article.getId(), doc.title, doc.topic, excerpt, md5, embedding);
                    inserted++;
                    log.info("Inserted knowledge article: {}", doc.title);
                }
            } catch (Exception e) {
                log.error("Failed to ingest doc: {}", file, e);
                failed++;
            }
        }

        log.info("Knowledge ingestion complete: total={}, inserted={}, updated={}, skipped={}, failed={}",
                total, inserted, updated, skipped, failed);

        result.put("total", total);
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("vectorCount", knowledgeVectorStore.count());
        return result;
    }

    private ParsedDoc parseFile(Path file) throws IOException {
        String raw = Files.readString(file);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            log.warn("No frontmatter found in: {}", file);
            return null;
        }

        String frontmatter = matcher.group(1);
        String content = matcher.group(2).trim();

        String title = extractField(frontmatter, "title");
        String topic = extractField(frontmatter, "topic");
        String tags = extractField(frontmatter, "tags");

        if (title == null || topic == null) {
            log.warn("Missing title or topic in: {}", file);
            return null;
        }

        return new ParsedDoc(title, topic, tags, content);
    }

    private String extractField(String frontmatter, String field) {
        Pattern pattern = Pattern.compile(field + ":\\s*(.+)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(frontmatter);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String md5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 computation failed", e);
        }
    }

    private record ParsedDoc(String title, String topic, String tags, String content) {}
}
