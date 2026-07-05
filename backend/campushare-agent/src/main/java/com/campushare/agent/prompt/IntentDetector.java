package com.campushare.agent.prompt;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 规则意图识别器（临时方案）。
 *
 * 设计文档《意图识别模块设计方案》完成后，此实现将被替换为基于 LLM/向量分类的正式版。
 * 当前用关键词匹配兜底，保证 PromptAssembler 能按意图切换 L2 任务级 Prompt。
 *
 * 三大意图：
 *  - HOW_TO：操作指引（怎么发帖/如何注册/怎么改密码）
 *  - SEARCH：内容检索（求 XX 卷子/找 XX 笔记/有没有 XX 资料）
 *  - CHAT：闲聊（你好/你是谁/谢谢）—— 默认兜底
 *
 * 判定优先级：SEARCH > HOW_TO > CHAT
 * 原因：检索类问题含"找/求"等强意图词，操作类问题含"怎么/如何"等弱意图词，
 * 闲聊类无明确意图词。优先匹配强意图，避免"求怎么发帖的教程"被误判为 HOW_TO。
 */
@Component
public class IntentDetector {

    /** 检索类强意图关键词。 */
    private static final Set<String> SEARCH_KEYWORDS = Set.of(
            "求", "找", "有没有", "想要", "需要", "求一份", "找一下",
            "资源", "卷子", "试卷", "笔记", "课件", "教案", "复习资料",
            "期末", "期中", "考试", "作业", "实验报告"
    );

    /** 操作类弱意图关键词。 */
    private static final Set<String> HOW_TO_KEYWORDS = Set.of(
            "怎么", "如何", "怎样", "哪里", "在哪", "如何才能",
            "注册", "登录", "发帖", "发布", "上传", "下载", "改密码",
            "认证", "申请", "删除", "修改", "编辑", "评论", "点赞", "收藏",
            "操作", "步骤", "教程"
    );

    /**
     * 检测用户 query 的意图。
     *
     * @param query 用户查询文本（可为 null/空）
     * @return 意图枚举，null/空文本返回 CHAT
     */
    public Intent detect(String query) {
        if (query == null || query.isBlank()) {
            return Intent.CHAT;
        }

        String trimmed = query.trim();

        for (String kw : SEARCH_KEYWORDS) {
            if (trimmed.contains(kw)) {
                return Intent.SEARCH;
            }
        }

        for (String kw : HOW_TO_KEYWORDS) {
            if (trimmed.contains(kw)) {
                return Intent.HOW_TO;
            }
        }

        return Intent.CHAT;
    }

    /**
     * 意图枚举。
     *
     * 顺序对应 L2 任务级 Prompt 的三个分支。
     */
    public enum Intent {
        HOW_TO,
        SEARCH,
        CHAT
    }
}
