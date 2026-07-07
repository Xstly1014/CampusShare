package com.campushare.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.dto.QualityInput;
import com.campushare.agent.entity.KnowledgeArticle;
import com.campushare.agent.entity.KnowledgeArticleVersion;
import com.campushare.agent.mapper.KnowledgeArticleMapper;
import com.campushare.agent.mapper.KnowledgeArticleVersionMapper;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.util.SemVer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库版本管理服务。
 *
 * 职责：
 * - snapshot：文档更新前写入完整快照到 knowledge_article_versions 表
 * - listVersions：查询文章的版本历史（按时间倒序）
 * - rollback：回滚到指定历史版本（恢复内容，version 递增 patch）
 *
 * 设计决策（遵循 project_memory 硬约束）：
 * - 版本号用 SemVer（v1.0.0），更新时 nextPatch 递增
 * - 历史表存完整快照（而非 diff），回滚简单直接
 * - 回滚后主表 version 递增（不回退），表示产生了新版本
 * - 回滚后 PG 向量由 KnowledgeScheduler 下一周期或调用方触发重新摄入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVersionService {

    private final KnowledgeArticleMapper articleMapper;
    private final KnowledgeArticleVersionMapper versionMapper;
    private final KnowledgeQualityScorer qualityScorer;
    private final KnowledgeVectorStore knowledgeVectorStore;

    /**
     * 在更新前写入当前文章的完整快照。
     *
     * @param article 即将被更新的文章（更新前的状态）
     * @param reason  快照原因（UPDATE/ROLLBACK/DEPRECATED）
     */
    public void snapshot(KnowledgeArticle article, String reason) {
        if (article == null || article.getId() == null) {
            log.warn("Cannot snapshot null or unsaved article");
            return;
        }

        KnowledgeArticleVersion version = KnowledgeArticleVersion.builder()
                .articleId(article.getId())
                .version(article.getVersion())
                .title(article.getTitle())
                .topic(article.getTopic())
                .content(article.getContent())
                .contentMd5(article.getContentMd5())
                .chunkCount(article.getChunkCount())
                .tags(article.getTags())
                .snapshotReason(reason != null ? reason : "UPDATE")
                .build();
        versionMapper.insert(version);
        log.info("Snapshot created: articleId={}, version={}, reason={}",
                article.getId(), article.getVersion(), reason);
    }

    /**
     * 查询文章的版本历史（按时间倒序）。
     */
    public List<KnowledgeArticleVersion> listVersions(Long articleId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<KnowledgeArticleVersion>()
                        .eq(KnowledgeArticleVersion::getArticleId, articleId)
                        .orderByDesc(KnowledgeArticleVersion::getCreatedAt)
        );
    }

    /**
     * 回滚到指定历史版本。
     *
     * 流程：
     * 1. 查询目标版本快照
     * 2. 对当前版本创建快照（保存当前状态）
     * 3. 用快照内容更新主表，version = SemVer.nextPatch(current)
     * 4. 返回更新后的文章（调用方负责触发 PG 重新摄入）
     *
     * @param articleId 文章 ID
     * @param targetVersion 目标版本号（如 v1.0.2）
     * @return 更新后的文章
     * @throws IllegalArgumentException 版本不存在时抛出
     */
    public KnowledgeArticle rollback(Long articleId, String targetVersion) {
        KnowledgeArticleVersion target = versionMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeArticleVersion>()
                        .eq(KnowledgeArticleVersion::getArticleId, articleId)
                        .eq(KnowledgeArticleVersion::getVersion, targetVersion)
                        .last("LIMIT 1")
        );
        if (target == null) {
            throw new IllegalArgumentException(
                    "Version " + targetVersion + " not found for article " + articleId);
        }

        KnowledgeArticle current = articleMapper.selectById(articleId);
        if (current == null) {
            throw new IllegalArgumentException("Article " + articleId + " not found");
        }

        // 先对当前版本创建快照（标记为 ROLLBACK）
        snapshot(current, "ROLLBACK");

        // 用快照内容更新主表，version 递增 patch
        SemVer nextVer = SemVer.parseOrInitial(current.getVersion()).nextPatch();

        current.setTitle(target.getTitle());
        current.setTopic(target.getTopic());
        current.setContent(target.getContent());
        current.setContentMd5(target.getContentMd5());
        current.setChunkCount(target.getChunkCount());
        current.setTags(target.getTags());
        current.setVersion(nextVer.toString());
        articleMapper.updateById(current);

        log.info("Rollback completed: articleId={}, from={}, to={}, newVersion={}",
                articleId, current.getVersion(), targetVersion, nextVer);

        return current;
    }

    /**
     * 标记文章为 DEPRECATED（软下线）。
     * 先创建快照，再将 status 改为 DEPRECATED。
     */
    public void deprecate(Long articleId) {
        KnowledgeArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new IllegalArgumentException("Article " + articleId + " not found");
        }
        snapshot(article, "DEPRECATED");
        article.setStatus("DEPRECATED");
        articleMapper.updateById(article);
        log.info("Article deprecated: articleId={}", articleId);
    }

    /**
     * 用户反馈（点赞/点踩），调整 feedbackScore 并重新计算质量评分。
     *
     * @param articleId 文章 ID
     * @param positive  true=点赞（+0.05），false=点踩（-0.05）
     */
    public void updateFeedback(Long articleId, boolean positive) {
        KnowledgeArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new IllegalArgumentException("Article " + articleId + " not found");
        }

        double currentFeedback = article.getFeedbackScore() != null ? article.getFeedbackScore() : 0.5;
        double newFeedback = positive
                ? Math.min(1.0, currentFeedback + 0.05)
                : Math.max(0.0, currentFeedback - 0.05);
        article.setFeedbackScore(newFeedback);

        double qualityScore = qualityScorer.score(new QualityInput(
                article.getRecallCount() != null ? article.getRecallCount() : 0,
                newFeedback,
                article.getUpdatedAt(),
                article.getChunkCount() != null ? article.getChunkCount() : 0
        ));
        article.setQualityScore(qualityScore);
        articleMapper.updateById(article);

        knowledgeVectorStore.updateQualityScore(articleId, qualityScore);
        log.info("Feedback updated: articleId={}, positive={}, feedbackScore={}, qualityScore={}",
                articleId, positive, newFeedback, qualityScore);
    }
}
