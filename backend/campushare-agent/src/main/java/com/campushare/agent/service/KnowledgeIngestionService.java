package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.Chunk;
import com.campushare.agent.dto.DuplicateDetectionResult;
import com.campushare.agent.dto.QualityInput;
import com.campushare.agent.entity.KnowledgeArticle;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.mapper.KnowledgeArticleMapper;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.util.SemVer;
import io.micrometer.core.instrument.Timer;
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
 * 知识库文档摄入服务（v2 分块版）。
 *
 * 流程：
 * 1. 扫描 docs-path 目录下所有 .md 文件
 * 2. 解析 frontmatter（title/topic/tags）+ 正文
 * 3. 计算 MD5，与 MySQL 中已有记录对比，跳过未变更的
 * 4. MarkdownChunker 分块
 * 5. embedBatch 批量 embedding
 * 6. 重复检测（chunk_index=0 的 embedding）
 * 7. [更新时] KnowledgeVersionService.snapshot
 * 8. SemVer.nextPatch 递增版本
 * 9. QualityScorer 计算初始质量评分
 * 10. 写 MySQL knowledge_articles → 写 PG knowledge_vectors（分块）
 *
 * 降级策略：
 * - Embedding 失败：跳过该文档，记录 failed
 * - 重复检测异常：默认按 UNIQUE 处理
 * - PG 写入失败：MySQL 已写入，下一调度周期通过 MD5 diff 跳过（需手动触发 reindex）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private final KnowledgeArticleMapper articleMapper;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final KnowledgeChunker chunker;
    private final KnowledgeQualityScorer qualityScorer;
    private final KnowledgeDuplicateDetector duplicateDetector;
    private final KnowledgeVersionService versionService;
    private final KnowledgeMetricsConfig metricsConfig;

    @Value("${app.knowledge.docs-path:docs/agent-assistant/knowledge-docs}")
    private String docsPath;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL
    );

    /**
     * 摄入全部知识库文档。
     * @return 摄入结果统计
     */
    public Map<String, Object> ingestAll() {
        Map<String, Object> result = new HashMap<>();
        int total = 0, inserted = 0, updated = 0, skipped = 0, failed = 0, duplicated = 0;
        Timer.Sample ingestTimer = metricsConfig.startIngestTimer();

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
                    metricsConfig.recordIngest("FAIL");
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
                    metricsConfig.recordIngest("SKIPPED");
                    continue;
                }

                List<Chunk> chunks = chunker.chunk(doc.content);
                if (chunks.isEmpty()) {
                    log.warn("No chunks generated for doc: {}, skipping", doc.title);
                    failed++;
                    metricsConfig.recordIngest("FAIL");
                    continue;
                }
                metricsConfig.recordChunksPerDoc(chunks.size());

                List<String> chunkTexts = chunks.stream().map(Chunk::embeddingText).toList();
                List<float[]> embeddings = embeddingClient.embedBatch(chunkTexts).block();
                if (embeddings == null || embeddings.isEmpty() || embeddings.size() != chunks.size()) {
                    log.warn("Embedding batch failed for doc: {} (got {} embeddings for {} chunks)",
                            doc.title, embeddings == null ? 0 : embeddings.size(), chunks.size());
                    failed++;
                    metricsConfig.recordIngest("FAIL");
                    continue;
                }
                metricsConfig.recordEmbeddingBatchSize(embeddings.size());

                // 重复检测（用第一个分块的 embedding）
                DuplicateDetectionResult duplicateResult = duplicateDetector.detect(doc.content, embeddings.get(0));
                if (duplicateResult.isDuplicate()) {
                    log.info("Skip duplicated doc: {} (matched articleId={}, similarity={})",
                            doc.title, duplicateResult.matchedArticleId(), duplicateResult.similarity());
                    duplicated++;
                    metricsConfig.recordIngest("DUPLICATED");
                    continue;
                }
                if (duplicateResult.isSimilar()) {
                    log.warn("Similar content detected for doc: {} (matched articleId={}, similarity={}), ingesting anyway",
                            doc.title, duplicateResult.matchedArticleId(), duplicateResult.similarity());
                }

                if (existing != null) {
                    versionService.snapshot(existing, "UPDATE");
                    SemVer nextVer = SemVer.parseOrInitial(existing.getVersion()).nextPatch();
                    double qualityScore = qualityScorer.score(new QualityInput(
                            existing.getRecallCount() != null ? existing.getRecallCount() : 0,
                            existing.getFeedbackScore() != null ? existing.getFeedbackScore() : 0.5,
                            existing.getUpdatedAt(),
                            chunks.size()
                    ));

                    existing.setTopic(doc.topic);
                    existing.setContent(doc.content);
                    existing.setContentMd5(md5);
                    existing.setStatus("PUBLISHED");
                    existing.setTags(doc.tags);
                    existing.setVersion(nextVer.toString());
                    existing.setChunkCount(chunks.size());
                    existing.setQualityScore(qualityScore);
                    articleMapper.updateById(existing);

                    knowledgeVectorStore.upsertChunks(
                            existing.getId(), chunks, embeddings,
                            doc.title, doc.topic, md5, nextVer.toString(), qualityScore
                    );
                    updated++;
                    metricsConfig.recordIngest("SUCCESS");
                    log.info("Updated knowledge article: {} (version={}, chunks={})",
                            doc.title, nextVer, chunks.size());
                } else {
                    double qualityScore = qualityScorer.score(new QualityInput(
                            0, 0.5, null, chunks.size()
                    ));

                    KnowledgeArticle article = KnowledgeArticle.builder()
                            .title(doc.title)
                            .topic(doc.topic)
                            .content(doc.content)
                            .contentMd5(md5)
                            .status("PUBLISHED")
                            .version(SemVer.initial().toString())
                            .chunkCount(chunks.size())
                            .qualityScore(qualityScore)
                            .recallCount(0)
                            .feedbackScore(0.5)
                            .tags(doc.tags)
                            .build();
                    articleMapper.insert(article);

                    knowledgeVectorStore.upsertChunks(
                            article.getId(), chunks, embeddings,
                            doc.title, doc.topic, md5, SemVer.initial().toString(), qualityScore
                    );
                    inserted++;
                    metricsConfig.recordIngest("SUCCESS");
                    log.info("Inserted knowledge article: {} (version={}, chunks={})",
                            doc.title, SemVer.initial(), chunks.size());
                }
            } catch (Exception e) {
                log.error("Failed to ingest doc: {}", file, e);
                failed++;
                metricsConfig.recordIngest("FAIL");
            }
        }

        metricsConfig.recordIngestDuration(ingestTimer, "TOTAL");
        log.info("Knowledge ingestion complete: total={}, inserted={}, updated={}, skipped={}, duplicated={}, failed={}",
                total, inserted, updated, skipped, duplicated, failed);

        result.put("total", total);
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("duplicated", duplicated);
        result.put("failed", failed);
        result.put("chunkCount", knowledgeVectorStore.count());
        result.put("articleCount", knowledgeVectorStore.countArticles());
        return result;
    }

    /**
     * 重新摄入单个文章（用于回滚后同步 PG 向量）。
     * 从 MySQL 读取文章内容，重新分块 + embedding + 写入 PG。
     */
    public void reingestArticle(Long articleId) {
        KnowledgeArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new IllegalArgumentException("Article not found: " + articleId);
        }

        List<Chunk> chunks = chunker.chunk(article.getContent());
        if (chunks.isEmpty()) {
            log.warn("No chunks generated for article: {}, skipping reingest", articleId);
            return;
        }

        List<String> chunkTexts = chunks.stream().map(Chunk::embeddingText).toList();
        List<float[]> embeddings = embeddingClient.embedBatch(chunkTexts).block();
        if (embeddings == null || embeddings.size() != chunks.size()) {
            log.warn("Embedding batch failed for article: {}", articleId);
            return;
        }

        double qualityScore = qualityScorer.score(new QualityInput(
                article.getRecallCount() != null ? article.getRecallCount() : 0,
                article.getFeedbackScore() != null ? article.getFeedbackScore() : 0.5,
                article.getUpdatedAt(),
                chunks.size()
        ));

        article.setChunkCount(chunks.size());
        article.setQualityScore(qualityScore);
        articleMapper.updateById(article);

        knowledgeVectorStore.upsertChunks(
                articleId, chunks, embeddings,
                article.getTitle(), article.getTopic(), article.getContentMd5(),
                article.getVersion(), qualityScore
        );
        log.info("Reingested article: id={}, version={}, chunks={}",
                articleId, article.getVersion(), chunks.size());
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
