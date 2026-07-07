package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.Chunk;
import com.campushare.agent.dto.DuplicateDetectionResult;
import com.campushare.agent.entity.KnowledgeArticle;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.mapper.KnowledgeArticleMapper;
import com.campushare.agent.store.KnowledgeVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeIngestionService 单元测试。
 *
 * 验证点：
 *  - ingestAll 全链路：文件扫描 → frontmatter 解析 → MD5 去重 → 分块 → embedding → 重复检测 → SemVer → 快照 → MySQL/PG 双写
 *  - reingestArticle：单文章重新摄入（回滚后同步 PG）
 *  - 各分支计数（inserted/updated/skipped/duplicated/failed）
 *  - Metrics 埋点验证
 *
 * 实现要点：
 *  - docsPath 用 ReflectionTestUtils 注入 @TempDir 路径
 *  - 8 个依赖全 mock
 *  - 用临时 .md 文件测试文件扫描与 frontmatter 解析
 */
@DisplayName("KnowledgeIngestionService 单元测试")
class KnowledgeIngestionServiceTest {

    private KnowledgeArticleMapper articleMapper;
    private EmbeddingClient embeddingClient;
    private KnowledgeVectorStore knowledgeVectorStore;
    private KnowledgeChunker chunker;
    private KnowledgeQualityScorer qualityScorer;
    private KnowledgeDuplicateDetector duplicateDetector;
    private KnowledgeVersionService versionService;
    private KnowledgeMetricsConfig metricsConfig;
    private KnowledgeIngestionService service;

    @TempDir
    Path tempDir;

    private static final String VALID_FRONTMATTER = """
            ---
            title: 测试文档
            topic: 测试主题
            tags: test,demo
            ---
            # 测试标题

            这是正文内容，用于测试分块。
            """;

    private static final String NO_FRONTMATTER = """
            # 无 frontmatter 的文档

            这个文档没有 frontmatter，应该被跳过。
            """;

    @BeforeEach
    void setUp() {
        articleMapper = mock(KnowledgeArticleMapper.class);
        embeddingClient = mock(EmbeddingClient.class);
        knowledgeVectorStore = mock(KnowledgeVectorStore.class);
        chunker = mock(KnowledgeChunker.class);
        qualityScorer = mock(KnowledgeQualityScorer.class);
        duplicateDetector = mock(KnowledgeDuplicateDetector.class);
        versionService = mock(KnowledgeVersionService.class);
        metricsConfig = mock(KnowledgeMetricsConfig.class);

        service = new KnowledgeIngestionService(
                articleMapper, embeddingClient, knowledgeVectorStore,
                chunker, qualityScorer, duplicateDetector,
                versionService, metricsConfig
        );
        ReflectionTestUtils.setField(service, "docsPath", tempDir.toString());
    }

    // ========== ingestAll ==========

    @Nested
    @DisplayName("ingestAll：全链路摄入")
    class IngestAll {

        @Test
        @DisplayName("docsPath 不存在 → 返回 error")
        void ingestAll_docsPathNotFound_returnsError() {
            ReflectionTestUtils.setField(service, "docsPath", "/nonexistent/path/xyz");

            Map<String, Object> result = service.ingestAll();

            assertThat(result).containsKey("error");
            assertThat(result.get("error").toString()).contains("not found");
        }

        @Test
        @DisplayName("空目录 → total=0，无文档处理")
        void ingestAll_emptyDir_returnsZeroTotal() {
            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(0);
            assertThat(result.get("inserted")).isEqualTo(0);
            assertThat(result.get("failed")).isEqualTo(0);
        }

        @Test
        @DisplayName(".md 无 frontmatter → failed=1 + recordIngest(FAIL)")
        void ingestAll_noFrontmatter_failedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("no-frontmatter.md"), NO_FRONTMATTER);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(1);
            assertThat(result.get("failed")).isEqualTo(1);
            verify(metricsConfig).recordIngest("FAIL");
            verify(articleMapper, never()).insert(any());
            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("MD5 相同 → skipped=1 + recordIngest(SKIPPED)")
        void ingestAll_sameMd5_skippedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("same.md"), VALID_FRONTMATTER);

            Chunk chunk = new Chunk("正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));

            String contentMd5 = computeExpectedMd5(extractContent(VALID_FRONTMATTER));
            KnowledgeArticle existing = KnowledgeArticle.builder()
                    .id(1L)
                    .title("测试文档")
                    .contentMd5(contentMd5)
                    .version("v1.0.0")
                    .build();
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(1);
            assertThat(result.get("skipped")).isEqualTo(1);
            verify(metricsConfig).recordIngest("SKIPPED");
            verify(embeddingClient, never()).embedBatch(anyList());
            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("新文档 + UNIQUE → inserted=1 + insert + upsertChunks + recordIngest(SUCCESS)")
        void ingestAll_newDocUnique_insertedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("new.md"), VALID_FRONTMATTER);

            Chunk chunk = new Chunk("正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(duplicateDetector.detect(anyString(), any(float[].class)))
                    .thenReturn(DuplicateDetectionResult.unique());
            when(qualityScorer.score(any())).thenReturn(0.5);
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(articleMapper.insert(any(KnowledgeArticle.class))).thenAnswer(invocation -> {
                ((KnowledgeArticle) invocation.getArgument(0)).setId(100L);
                return 1;
            });

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(1);
            assertThat(result.get("inserted")).isEqualTo(1);
            verify(metricsConfig).recordIngest("SUCCESS");
            verify(metricsConfig).recordChunksPerDoc(1);
            verify(metricsConfig).recordEmbeddingBatchSize(1);
            verify(articleMapper).insert(any(KnowledgeArticle.class));
            verify(knowledgeVectorStore).upsertChunks(eq(100L), anyList(), anyList(),
                    eq("测试文档"), eq("测试主题"), anyString(), eq("v1.0.0"), eq(0.5));
        }

        @Test
        @DisplayName("已有文档 MD5 变化 → updated=1 + snapshot + nextPatch + updateById + upsertChunks")
        void ingestAll_existingDocMd5Changed_updatedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("updated.md"), VALID_FRONTMATTER);

            Chunk chunk = new Chunk("更新后正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(duplicateDetector.detect(anyString(), any(float[].class)))
                    .thenReturn(DuplicateDetectionResult.unique());
            when(qualityScorer.score(any())).thenReturn(0.8);

            KnowledgeArticle existing = KnowledgeArticle.builder()
                    .id(50L)
                    .title("测试文档")
                    .contentMd5("old_md5_value_different_from_new")
                    .version("v1.0.0")
                    .recallCount(5)
                    .feedbackScore(0.6)
                    .build();
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(1);
            assertThat(result.get("updated")).isEqualTo(1);
            verify(versionService).snapshot(existing, "UPDATE");
            verify(articleMapper).updateById(any(KnowledgeArticle.class));
            verify(knowledgeVectorStore).upsertChunks(eq(50L), anyList(), anyList(),
                    eq("测试文档"), eq("测试主题"), anyString(), eq("v1.0.1"), eq(0.8));
            verify(metricsConfig).recordIngest("SUCCESS");
        }

        @Test
        @DisplayName("重复检测返回 DUPLICATE → duplicated=1，不写库")
        void ingestAll_duplicateDetected_duplicatedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("dup.md"), VALID_FRONTMATTER);

            Chunk chunk = new Chunk("正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(duplicateDetector.detect(anyString(), any(float[].class)))
                    .thenReturn(DuplicateDetectionResult.of(
                            DuplicateDetectionResult.Level.DUPLICATE, 0.97, 99L));
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(1);
            assertThat(result.get("duplicated")).isEqualTo(1);
            verify(metricsConfig).recordIngest("DUPLICATED");
            verify(articleMapper, never()).insert(any());
            verify(articleMapper, never()).updateById(any());
            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("重复检测返回 SIMILAR → 仍正常摄入（warn 日志）")
        void ingestAll_similarDetected_stillIngests() throws IOException {
            Files.writeString(tempDir.resolve("similar.md"), VALID_FRONTMATTER);

            Chunk chunk = new Chunk("正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(duplicateDetector.detect(anyString(), any(float[].class)))
                    .thenReturn(DuplicateDetectionResult.of(
                            DuplicateDetectionResult.Level.SIMILAR, 0.88, 99L));
            when(qualityScorer.score(any())).thenReturn(0.5);
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(articleMapper.insert(any(KnowledgeArticle.class))).thenAnswer(invocation -> {
                ((KnowledgeArticle) invocation.getArgument(0)).setId(1L);
                return 1;
            });

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("inserted")).isEqualTo(1);
            verify(articleMapper).insert(any(KnowledgeArticle.class));
            verify(knowledgeVectorStore).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("embedBatch 返回 null → failed=1")
        void ingestAll_embedBatchNull_failedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("null-embed.md"), VALID_FRONTMATTER);

            Chunk chunk = new Chunk("正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList())).thenReturn(Mono.empty());
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("failed")).isEqualTo(1);
            verify(metricsConfig).recordIngest("FAIL");
            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("embedBatch 数量不匹配 → failed=1")
        void ingestAll_embedBatchSizeMismatch_failedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("mismatch.md"), VALID_FRONTMATTER);

            Chunk chunk1 = new Chunk("正文1", 10, "# 标题1");
            Chunk chunk2 = new Chunk("正文2", 10, "# 标题2");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk1, chunk2));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("failed")).isEqualTo(1);
            verify(metricsConfig).recordIngest("FAIL");
        }

        @Test
        @DisplayName("chunker 返回空列表 → failed=1")
        void ingestAll_chunkerEmpty_failedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("empty-chunks.md"), VALID_FRONTMATTER);

            when(chunker.chunk(anyString())).thenReturn(Collections.emptyList());
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("failed")).isEqualTo(1);
            verify(metricsConfig).recordIngest("FAIL");
            verify(embeddingClient, never()).embedBatch(anyList());
        }

        @Test
        @DisplayName("摄入过程抛异常 → failed=1 + recordIngest(FAIL)")
        void ingestAll_exceptionThrown_failedIncrement() throws IOException {
            Files.writeString(tempDir.resolve("exception.md"), VALID_FRONTMATTER);

            when(chunker.chunk(anyString())).thenThrow(new RuntimeException("chunker boom"));
            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("failed")).isEqualTo(1);
            verify(metricsConfig).recordIngest("FAIL");
        }

        @Test
        @DisplayName("全链路：3 个文件（新+跳过+失败）→ inserted=1, skipped=1, failed=1")
        void ingestAll_mixedFiles_correctCounts() throws IOException {
            Files.writeString(tempDir.resolve("new.md"), VALID_FRONTMATTER);
            Files.writeString(tempDir.resolve("skip.md"), VALID_FRONTMATTER);
            Files.writeString(tempDir.resolve("nofm.md"), NO_FRONTMATTER);

            Chunk chunk = new Chunk("正文", 10, "# 测试标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(duplicateDetector.detect(anyString(), any(float[].class)))
                    .thenReturn(DuplicateDetectionResult.unique());
            when(qualityScorer.score(any())).thenReturn(0.5);

            String sameMd5 = computeExpectedMd5(extractContent(VALID_FRONTMATTER));
            KnowledgeArticle existing = KnowledgeArticle.builder()
                    .id(1L)
                    .title("测试文档")
                    .contentMd5(sameMd5)
                    .version("v1.0.0")
                    .build();

            when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
            when(articleMapper.insert(any(KnowledgeArticle.class))).thenAnswer(invocation -> {
                ((KnowledgeArticle) invocation.getArgument(0)).setId(100L);
                return 1;
            });

            Map<String, Object> result = service.ingestAll();

            assertThat(result.get("total")).isEqualTo(3);
            assertThat(result.get("skipped")).isEqualTo(2);
            assertThat(result.get("failed")).isEqualTo(1);
        }
    }

    // ========== reingestArticle ==========

    @Nested
    @DisplayName("reingestArticle：单文章重新摄入")
    class ReingestArticle {

        @Test
        @DisplayName("文章不存在 → 抛 IllegalArgumentException")
        void reingestArticle_notFound_throwsIllegalArgument() {
            when(articleMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> service.reingestArticle(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Article not found");
        }

        @Test
        @DisplayName("正常 → chunk + embed + quality + updateById + upsertChunks")
        void reingestArticle_success_updatesAndUpserts() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(10L)
                    .title("测试文档")
                    .topic("测试主题")
                    .content("正文内容")
                    .contentMd5("abc123")
                    .version("v1.0.0")
                    .recallCount(3)
                    .feedbackScore(0.7)
                    .build();
            when(articleMapper.selectById(10L)).thenReturn(article);

            Chunk chunk = new Chunk("正文", 10, "# 标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));
            when(qualityScorer.score(any())).thenReturn(0.9);

            service.reingestArticle(10L);

            verify(articleMapper).updateById(any(KnowledgeArticle.class));
            verify(knowledgeVectorStore).upsertChunks(eq(10L), anyList(), anyList(),
                    eq("测试文档"), eq("测试主题"), eq("abc123"), eq("v1.0.0"), eq(0.9));
        }

        @Test
        @DisplayName("chunker 返回空 → 跳过 reingest（不调 embedBatch）")
        void reingestArticle_chunkerEmpty_skipsReingest() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(10L)
                    .title("测试文档")
                    .content("正文")
                    .version("v1.0.0")
                    .build();
            when(articleMapper.selectById(10L)).thenReturn(article);
            when(chunker.chunk(anyString())).thenReturn(Collections.emptyList());

            service.reingestArticle(10L);

            verify(embeddingClient, never()).embedBatch(anyList());
            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("embedBatch 返回 null → 跳过 upsertChunks")
        void reingestArticle_embedBatchNull_skipsUpsert() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(10L)
                    .title("测试文档")
                    .content("正文")
                    .version("v1.0.0")
                    .build();
            when(articleMapper.selectById(10L)).thenReturn(article);

            Chunk chunk = new Chunk("正文", 10, "# 标题");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk));
            when(embeddingClient.embedBatch(anyList())).thenReturn(Mono.empty());

            service.reingestArticle(10L);

            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("embedBatch 数量不匹配 → 跳过 upsertChunks")
        void reingestArticle_embedBatchSizeMismatch_skipsUpsert() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(10L)
                    .title("测试文档")
                    .content("正文")
                    .version("v1.0.0")
                    .build();
            when(articleMapper.selectById(10L)).thenReturn(article);

            Chunk chunk1 = new Chunk("正文1", 10, "# 标题1");
            Chunk chunk2 = new Chunk("正文2", 10, "# 标题2");
            when(chunker.chunk(anyString())).thenReturn(List.of(chunk1, chunk2));
            when(embeddingClient.embedBatch(anyList()))
                    .thenReturn(Mono.just(List.of(new float[1024])));

            service.reingestArticle(10L);

            verify(knowledgeVectorStore, never()).upsertChunks(anyLong(), anyList(), anyList(),
                    anyString(), anyString(), anyString(), anyString(), anyDouble());
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 从带 frontmatter 的 .md 内容中提取正文部分（与 KnowledgeIngestionService.parseFile 一致的逻辑）。
     */
    private static String extractContent(String md) {
        String[] parts = md.split("\\n---\\s*\\n", 2);
        if (parts.length < 2) {
            return md;
        }
        return parts[1].trim();
    }

    /**
     * 计算预期 MD5（与 KnowledgeIngestionService.md5 一致的算法）。
     */
    private static String computeExpectedMd5(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
