package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campushare.agent.dto.QualityInput;
import com.campushare.agent.entity.KnowledgeArticle;
import com.campushare.agent.entity.KnowledgeArticleVersion;
import com.campushare.agent.mapper.KnowledgeArticleMapper;
import com.campushare.agent.mapper.KnowledgeArticleVersionMapper;
import com.campushare.agent.store.KnowledgeVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeVersionService 单元测试。
 *
 * 验证点：
 *  - snapshot：写入字段正确（articleId/version/reason）
 *  - snapshot(null) → 警告不写
 *  - listVersions：返回列表
 *  - rollback：目标版本存在 → snapshot 当前 → 更新主表 → version = nextPatch(current)
 *  - rollback 目标版本不存在 → 抛 IllegalArgumentException
 *  - rollback 文章不存在 → 抛 IllegalArgumentException
 *  - deprecate：snapshot + status=DEPRECATED
 *  - updateFeedback(positive=true) → feedbackScore +0.05（封顶 1.0）
 *  - updateFeedback(positive=false) → feedbackScore -0.05（封底 0.0）
 *  - updateFeedback 触发 qualityScore 重算 + knowledgeVectorStore.updateQualityScore
 */
@DisplayName("KnowledgeVersionService 单元测试")
class KnowledgeVersionServiceTest {

    private KnowledgeArticleMapper articleMapper;
    private KnowledgeArticleVersionMapper versionMapper;
    private KnowledgeQualityScorer qualityScorer;
    private KnowledgeVectorStore vectorStore;
    private KnowledgeVersionService versionService;

    @BeforeEach
    void setUp() {
        articleMapper = mock(KnowledgeArticleMapper.class);
        versionMapper = mock(KnowledgeArticleVersionMapper.class);
        qualityScorer = mock(KnowledgeQualityScorer.class);
        vectorStore = mock(KnowledgeVectorStore.class);
        versionService = new KnowledgeVersionService(articleMapper, versionMapper, qualityScorer, vectorStore);
    }

    // ========== snapshot ==========

    @Nested
    @DisplayName("snapshot：版本快照写入")
    class Snapshot {

        @Test
        @DisplayName("正常文章 → 写入版本历史表，字段正确")
        void snapshot_normal_writesVersionRecord() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .version("v1.0.0")
                    .title("测试文章")
                    .topic("TEST")
                    .content("正文内容")
                    .contentMd5("abc123")
                    .chunkCount(3)
                    .tags("tag1,tag2")
                    .build();

            versionService.snapshot(article, "UPDATE");

            ArgumentCaptor<KnowledgeArticleVersion> captor = ArgumentCaptor.forClass(KnowledgeArticleVersion.class);
            verify(versionMapper).insert(captor.capture());
            KnowledgeArticleVersion saved = captor.getValue();
            assertThat(saved.getArticleId()).isEqualTo(1L);
            assertThat(saved.getVersion()).isEqualTo("v1.0.0");
            assertThat(saved.getTitle()).isEqualTo("测试文章");
            assertThat(saved.getTopic()).isEqualTo("TEST");
            assertThat(saved.getContent()).isEqualTo("正文内容");
            assertThat(saved.getContentMd5()).isEqualTo("abc123");
            assertThat(saved.getChunkCount()).isEqualTo(3);
            assertThat(saved.getTags()).isEqualTo("tag1,tag2");
            assertThat(saved.getSnapshotReason()).isEqualTo("UPDATE");
        }

        @Test
        @DisplayName("reason=null → snapshotReason 默认为 UPDATE")
        void snapshot_nullReason_defaultsToUpdate() {
            KnowledgeArticle article = KnowledgeArticle.builder().id(1L).version("v1.0.0").build();

            versionService.snapshot(article, null);

            ArgumentCaptor<KnowledgeArticleVersion> captor = ArgumentCaptor.forClass(KnowledgeArticleVersion.class);
            verify(versionMapper).insert(captor.capture());
            assertThat(captor.getValue().getSnapshotReason()).isEqualTo("UPDATE");
        }

        @Test
        @DisplayName("article=null → 警告不写")
        void snapshot_nullArticle_doesNotWrite() {
            versionService.snapshot(null, "UPDATE");
            verify(versionMapper, never()).insert(any());
        }

        @Test
        @DisplayName("article.id=null → 警告不写（未保存的文章）")
        void snapshot_nullArticleId_doesNotWrite() {
            KnowledgeArticle article = KnowledgeArticle.builder().version("v1.0.0").build();
            versionService.snapshot(article, "UPDATE");
            verify(versionMapper, never()).insert(any());
        }
    }

    // ========== listVersions ==========

    @Nested
    @DisplayName("listVersions：版本历史查询")
    class ListVersions {

        @Test
        @DisplayName("正常查询 → 返回版本列表")
        void listVersions_normal_returnsList() {
            when(versionMapper.selectList(any(Wrapper.class)))
                    .thenReturn(java.util.List.of(
                            new KnowledgeArticleVersion(),
                            new KnowledgeArticleVersion()
                    ));

            var result = versionService.listVersions(1L);

            assertThat(result).hasSize(2);
            verify(versionMapper).selectList(any(Wrapper.class));
        }

        @Test
        @DisplayName("无历史版本 → 返回空列表")
        void listVersions_noHistory_returnsEmptyList() {
            when(versionMapper.selectList(any(Wrapper.class)))
                    .thenReturn(java.util.List.of());

            var result = versionService.listVersions(999L);

            assertThat(result).isEmpty();
        }
    }

    // ========== rollback ==========

    @Nested
    @DisplayName("rollback：版本回滚")
    class Rollback {

        @Test
        @DisplayName("目标版本存在 → snapshot 当前 → 更新主表 → version = nextPatch(current)")
        void rollback_targetExists_snapshotsAndUpdates() {
            KnowledgeArticleVersion target = KnowledgeArticleVersion.builder()
                    .articleId(1L)
                    .version("v1.0.0")
                    .title("旧标题")
                    .topic("OLD_TOPIC")
                    .content("旧内容")
                    .contentMd5("old_md5")
                    .chunkCount(2)
                    .tags("old_tags")
                    .build();
            when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(target);

            KnowledgeArticle current = KnowledgeArticle.builder()
                    .id(1L)
                    .version("v1.0.2")
                    .title("新标题")
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(current);

            KnowledgeArticle result = versionService.rollback(1L, "v1.0.0");

            verify(versionMapper).insert(any(KnowledgeArticleVersion.class));
            verify(articleMapper).updateById(any(KnowledgeArticle.class));
            assertThat(result.getTitle()).isEqualTo("旧标题");
            assertThat(result.getTopic()).isEqualTo("OLD_TOPIC");
            assertThat(result.getContent()).isEqualTo("旧内容");
            assertThat(result.getContentMd5()).isEqualTo("old_md5");
            assertThat(result.getChunkCount()).isEqualTo(2);
            assertThat(result.getTags()).isEqualTo("old_tags");
            assertThat(result.getVersion()).isEqualTo("v1.0.3");
        }

        @Test
        @DisplayName("目标版本不存在 → 抛 IllegalArgumentException")
        void rollback_targetNotExists_throwsException() {
            when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> versionService.rollback(1L, "v9.9.9"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("文章不存在 → 抛 IllegalArgumentException")
        void rollback_articleNotExists_throwsException() {
            KnowledgeArticleVersion target = KnowledgeArticleVersion.builder()
                    .articleId(1L)
                    .version("v1.0.0")
                    .build();
            when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(target);
            when(articleMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> versionService.rollback(1L, "v1.0.0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("current.version 非法 → 降级为 v1.0.0 后 nextPatch = v1.0.1")
        void rollback_invalidCurrentVersion_degradesToInitial() {
            KnowledgeArticleVersion target = KnowledgeArticleVersion.builder()
                    .articleId(1L)
                    .version("v1.0.0")
                    .build();
            when(versionMapper.selectOne(any(Wrapper.class))).thenReturn(target);

            KnowledgeArticle current = KnowledgeArticle.builder()
                    .id(1L)
                    .version("invalid")
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(current);

            KnowledgeArticle result = versionService.rollback(1L, "v1.0.0");

            assertThat(result.getVersion()).isEqualTo("v1.0.1");
        }
    }

    // ========== deprecate ==========

    @Nested
    @DisplayName("deprecate：标记为 DEPRECATED")
    class Deprecate {

        @Test
        @DisplayName("正常文章 → snapshot + status=DEPRECATED")
        void deprecate_normal_snapshotsAndDeprecates() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .version("v1.0.0")
                    .status("PUBLISHED")
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(article);

            versionService.deprecate(1L);

            verify(versionMapper).insert(any(KnowledgeArticleVersion.class));
            verify(articleMapper).updateById(any(KnowledgeArticle.class));
            assertThat(article.getStatus()).isEqualTo("DEPRECATED");
        }

        @Test
        @DisplayName("文章不存在 → 抛 IllegalArgumentException")
        void deprecate_articleNotExists_throwsException() {
            when(articleMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> versionService.deprecate(1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========== updateFeedback ==========

    @Nested
    @DisplayName("updateFeedback：用户反馈调整")
    class UpdateFeedback {

        @Test
        @DisplayName("positive=true → feedbackScore +0.05")
        void updateFeedback_positive_incrementsFeedbackScore() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .feedbackScore(0.5)
                    .recallCount(10)
                    .chunkCount(3)
                    .version("v1.0.0")
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(article);
            when(qualityScorer.score(any(QualityInput.class))).thenReturn(0.7);

            versionService.updateFeedback(1L, true);

            assertThat(article.getFeedbackScore()).isCloseTo(0.55, within(0.001));
            assertThat(article.getQualityScore()).isEqualTo(0.7);
            verify(articleMapper).updateById(article);
            verify(vectorStore).updateQualityScore(1L, 0.7);
        }

        @Test
        @DisplayName("positive=false → feedbackScore -0.05")
        void updateFeedback_negative_decrementsFeedbackScore() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .feedbackScore(0.5)
                    .recallCount(10)
                    .chunkCount(3)
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(article);
            when(qualityScorer.score(any(QualityInput.class))).thenReturn(0.4);

            versionService.updateFeedback(1L, false);

            assertThat(article.getFeedbackScore()).isCloseTo(0.45, within(0.001));
            verify(vectorStore).updateQualityScore(1L, 0.4);
        }

        @Test
        @DisplayName("positive=true 且 feedbackScore 已=1.0 → 封顶 1.0")
        void updateFeedback_positiveAtMax_cappedAtOne() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .feedbackScore(1.0)
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(article);
            when(qualityScorer.score(any(QualityInput.class))).thenReturn(0.9);

            versionService.updateFeedback(1L, true);

            assertThat(article.getFeedbackScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("positive=false 且 feedbackScore 已=0.0 → 封底 0.0")
        void updateFeedback_negativeAtMin_cappedAtZero() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .feedbackScore(0.0)
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(article);
            when(qualityScorer.score(any(QualityInput.class))).thenReturn(0.1);

            versionService.updateFeedback(1L, false);

            assertThat(article.getFeedbackScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("feedbackScore=null → 默认从 0.5 开始")
        void updateFeedback_nullFeedbackScore_startsFromDefault() {
            KnowledgeArticle article = KnowledgeArticle.builder()
                    .id(1L)
                    .feedbackScore(null)
                    .build();
            when(articleMapper.selectById(1L)).thenReturn(article);
            when(qualityScorer.score(any(QualityInput.class))).thenReturn(0.5);

            versionService.updateFeedback(1L, true);

            assertThat(article.getFeedbackScore()).isCloseTo(0.55, within(0.001));
        }

        @Test
        @DisplayName("文章不存在 → 抛 IllegalArgumentException")
        void updateFeedback_articleNotExists_throwsException() {
            when(articleMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> versionService.updateFeedback(1L, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
