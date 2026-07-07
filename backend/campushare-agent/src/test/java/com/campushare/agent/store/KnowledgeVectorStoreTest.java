package com.campushare.agent.store;

import com.campushare.agent.dto.Chunk;
import com.campushare.agent.dto.ChunkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeVectorStore 单元测试。
 *
 * 验证点：
 *  - upsertChunks：先 DELETE 再 batch INSERT，参数顺序正确
 *  - upsertChunks 空 chunks → 仅 DELETE
 *  - upsertChunks chunks.size != embeddings.size → 抛异常
 *  - searchChunks / keywordSearchChunks / findSimilar：mock JdbcTemplate 返回
 *  - incrementRecall / updateQualityScore：UPDATE 调用
 *  - count / countArticles：queryForObject
 */
@DisplayName("KnowledgeVectorStore 单元测试")
class KnowledgeVectorStoreTest {

    private JdbcTemplate jdbcTemplate;
    private KnowledgeVectorStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        store = new KnowledgeVectorStore(jdbcTemplate);
    }

    // ========== upsertChunks ==========

    @Nested
    @DisplayName("upsertChunks：先删后插")
    class UpsertChunks {

        @Test
        @DisplayName("正常分块 → 先 DELETE 再 batch INSERT")
        void upsertChunks_normal_deletesThenBatchInserts() {
            List<Chunk> chunks = List.of(
                    new Chunk("chunk1", 10, "heading1"),
                    new Chunk("chunk2", 20, "heading2")
            );
            List<float[]> embeddings = List.of(
                    new float[]{1.0f, 2.0f},
                    new float[]{3.0f, 4.0f}
            );

            store.upsertChunks(1L, chunks, embeddings, "title", "topic", "md5", "v1.0.0", 0.5);

            verify(jdbcTemplate).update("DELETE FROM knowledge_vectors WHERE article_id = ?", 1L);
            verify(jdbcTemplate).batchUpdate(anyString(), any(List.class));
        }

        @Test
        @DisplayName("空 chunks → 仅 DELETE，不 batch INSERT")
        void upsertChunks_emptyChunks_onlyDeletes() {
            store.upsertChunks(1L, List.of(), List.of(), "title", "topic", "md5", "v1.0.0", 0.5);

            verify(jdbcTemplate).update("DELETE FROM knowledge_vectors WHERE article_id = ?", 1L);
            verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
        }

        @Test
        @DisplayName("chunks.size != embeddings.size → 抛 IllegalArgumentException")
        void upsertChunks_sizeMismatch_throws() {
            List<Chunk> chunks = List.of(new Chunk("c1", 10, "h1"));
            List<float[]> embeddings = List.of(
                    new float[]{1.0f},
                    new float[]{2.0f}
            );

            assertThatThrownBy(() ->
                    store.upsertChunks(1L, chunks, embeddings, "title", "topic", "md5", "v1.0.0", 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("size");
        }

        @Test
        @DisplayName("单 chunk + 单 embedding → 正常 upsert")
        void upsertChunks_singleChunk_succeeds() {
            List<Chunk> chunks = List.of(new Chunk("only", 5, "head"));
            List<float[]> embeddings = List.of(new float[]{1.0f, 2.0f, 3.0f});

            store.upsertChunks(99L, chunks, embeddings, "title", "topic", "md5", "v1.0.0", 0.5);

            verify(jdbcTemplate).update("DELETE FROM knowledge_vectors WHERE article_id = ?", 99L);
            verify(jdbcTemplate).batchUpdate(anyString(), any(List.class));
        }
    }

    // ========== deleteByArticleId ==========

    @Nested
    @DisplayName("deleteByArticleId")
    class DeleteByArticleId {

        @Test
        @DisplayName("正常删除 → 调用 jdbcTemplate.update")
        void deleteByArticleId_normal_callsUpdate() {
            store.deleteByArticleId(42L);
            verify(jdbcTemplate).update("DELETE FROM knowledge_vectors WHERE article_id = ?", 42L);
        }
    }

    // ========== searchChunks ==========

    @Nested
    @DisplayName("searchChunks：向量检索")
    class SearchChunks {

        @Test
        @DisplayName("正常检索 → 返回 ChunkResult 列表")
        void searchChunks_normal_returnsResults() {
            ChunkResult mockResult = new ChunkResult(1L, 0, "title", "content", "heading", "topic", 0.95, 0.5);
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of(mockResult));

            List<ChunkResult> results = store.searchChunks(new float[]{1.0f, 2.0f}, 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).articleId()).isEqualTo(1L);
            assertThat(results.get(0).similarity()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("无结果 → 返回空列表")
        void searchChunks_noMatch_returnsEmptyList() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            List<ChunkResult> results = store.searchChunks(new float[]{1.0f}, 5);

            assertThat(results).isEmpty();
        }
    }

    // ========== keywordSearchChunks ==========

    @Nested
    @DisplayName("keywordSearchChunks：关键词检索")
    class KeywordSearchChunks {

        @Test
        @DisplayName("正常检索 → 返回 ChunkResult 列表")
        void keywordSearchChunks_normal_returnsResults() {
            ChunkResult mockResult = new ChunkResult(1L, 0, "title", "content", "heading", "topic", 0.8, 0.5);
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(mockResult));

            List<ChunkResult> results = store.keywordSearchChunks("keyword", 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).similarity()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("无结果 → 返回空列表")
        void keywordSearchChunks_noMatch_returnsEmptyList() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            List<ChunkResult> results = store.keywordSearchChunks("nonexistent", 5);

            assertThat(results).isEmpty();
        }
    }

    // ========== findSimilar ==========

    @Nested
    @DisplayName("findSimilar：重复检测用")
    class FindSimilar {

        @Test
        @DisplayName("有匹配 → 返回 SimilarMatch")
        void findSimilar_hasMatch_returnsSimilarMatch() {
            KnowledgeVectorStore.SimilarMatch match = new KnowledgeVectorStore.SimilarMatch(100L, 0.95);
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of(match));

            KnowledgeVectorStore.SimilarMatch result = store.findSimilar(new float[]{1.0f}, 1);

            assertThat(result).isNotNull();
            assertThat(result.articleId()).isEqualTo(100L);
            assertThat(result.similarity()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("无匹配 → 返回 null")
        void findSimilar_noMatch_returnsNull() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                    .thenReturn(List.of());

            KnowledgeVectorStore.SimilarMatch result = store.findSimilar(new float[]{1.0f}, 1);

            assertThat(result).isNull();
        }
    }

    // ========== incrementRecall ==========

    @Nested
    @DisplayName("incrementRecall：召回计数")
    class IncrementRecall {

        @Test
        @DisplayName("正常调用 → UPDATE recall_count = recall_count + 1")
        void incrementRecall_normal_callsUpdate() {
            store.incrementRecall(42L);
            verify(jdbcTemplate).update(
                    "UPDATE knowledge_vectors SET recall_count = recall_count + 1, updated_at = CURRENT_TIMESTAMP WHERE article_id = ?",
                    42L
            );
        }
    }

    // ========== updateQualityScore ==========

    @Nested
    @DisplayName("updateQualityScore：质量评分更新")
    class UpdateQualityScore {

        @Test
        @DisplayName("正常调用 → UPDATE quality_score")
        void updateQualityScore_normal_callsUpdate() {
            store.updateQualityScore(42L, 0.85);
            verify(jdbcTemplate).update(
                    "UPDATE knowledge_vectors SET quality_score = ?, updated_at = CURRENT_TIMESTAMP WHERE article_id = ?",
                    0.85, 42L
            );
        }
    }

    // ========== count / countArticles ==========

    @Nested
    @DisplayName("count / countArticles")
    class CountMethods {

        @Test
        @DisplayName("count → 返回分块总数")
        void count_returnsChunkCount() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenReturn(42);

            int count = store.count();

            assertThat(count).isEqualTo(42);
        }

        @Test
        @DisplayName("count 返回 null → 返回 0")
        void count_nullResult_returnsZero() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenReturn(null);

            int count = store.count();

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("countArticles → 返回文章去重数")
        void countArticles_returnsArticleCount() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenReturn(10);

            int count = store.countArticles();

            assertThat(count).isEqualTo(10);
        }

        @Test
        @DisplayName("countArticles 返回 null → 返回 0")
        void countArticles_nullResult_returnsZero() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenReturn(null);

            int count = store.countArticles();

            assertThat(count).isEqualTo(0);
        }
    }
}
