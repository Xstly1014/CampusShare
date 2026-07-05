package com.campushare.agent.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntentDetector 单元测试。
 *
 * 验证点：
 *  - 三大意图关键词识别：SEARCH（强意图）/ HOW_TO（弱意图）/ CHAT（默认兜底）
 *  - 优先级：SEARCH > HOW_TO > CHAT（强意图优先，避免"求怎么发帖的教程"被误判为 HOW_TO）
 *  - 空值/空白兜底：返回 CHAT
 *
 * 注意：本测试基于 IntentDetector 的关键词集（SEARCH_KEYWORDS / HOW_TO_KEYWORDS），
 *      若关键词集变更，需同步更新用例。
 */
@DisplayName("IntentDetector 单元测试")
class IntentDetectorTest {

    private IntentDetector detector;

    @BeforeEach
    void setUp() {
        detector = new IntentDetector();
    }

    // ========== 空值/空白兜底 ==========

    @Test
    @DisplayName("null 输入：返回 CHAT")
    void detect_null_returnsChat() {
        assertThat(detector.detect(null)).isEqualTo(IntentDetector.Intent.CHAT);
    }

    @Test
    @DisplayName("空白输入：返回 CHAT")
    void detect_blank_returnsChat() {
        assertThat(detector.detect("   ")).isEqualTo(IntentDetector.Intent.CHAT);
    }

    @Test
    @DisplayName("空字符串输入：返回 CHAT")
    void detect_empty_returnsChat() {
        assertThat(detector.detect("")).isEqualTo(IntentDetector.Intent.CHAT);
    }

    // ========== SEARCH 关键词 ==========

    @Test
    @DisplayName("SEARCH 关键词「求」：返回 SEARCH")
    void detect_searchKeyword_求() {
        assertThat(detector.detect("求一份操作系统卷子")).isEqualTo(IntentDetector.Intent.SEARCH);
    }

    @Test
    @DisplayName("SEARCH 关键词「找」：返回 SEARCH")
    void detect_searchKeyword_找() {
        assertThat(detector.detect("找一下高数笔记")).isEqualTo(IntentDetector.Intent.SEARCH);
    }

    @Test
    @DisplayName("SEARCH 长关键词「有没有」：返回 SEARCH")
    void detect_searchKeyword_有没有() {
        assertThat(detector.detect("有没有数据结构课件")).isEqualTo(IntentDetector.Intent.SEARCH);
    }

    @Test
    @DisplayName("SEARCH 关键词「资源」：返回 SEARCH")
    void detect_searchKeyword_资源() {
        assertThat(detector.detect("求资源")).isEqualTo(IntentDetector.Intent.SEARCH);
    }

    // ========== HOW_TO 关键词 ==========

    @Test
    @DisplayName("HOW_TO 关键词「怎么」：返回 HOW_TO")
    void detect_howToKeyword_怎么() {
        assertThat(detector.detect("怎么发帖")).isEqualTo(IntentDetector.Intent.HOW_TO);
    }

    @Test
    @DisplayName("HOW_TO 关键词「如何」：返回 HOW_TO")
    void detect_howToKeyword_如何() {
        assertThat(detector.detect("如何修改密码")).isEqualTo(IntentDetector.Intent.HOW_TO);
    }

    @Test
    @DisplayName("HOW_TO 关键词「在哪」：返回 HOW_TO")
    void detect_howToKeyword_在哪() {
        assertThat(detector.detect("在哪里认证")).isEqualTo(IntentDetector.Intent.HOW_TO);
    }

    // ========== CHAT 兜底 ==========

    @Test
    @DisplayName("无意图词「你好」：返回 CHAT")
    void detect_chatFallback_hello() {
        assertThat(detector.detect("你好")).isEqualTo(IntentDetector.Intent.CHAT);
    }

    @Test
    @DisplayName("无意图词「你是谁」：返回 CHAT")
    void detect_chatFallback_你是谁() {
        assertThat(detector.detect("你是谁")).isEqualTo(IntentDetector.Intent.CHAT);
    }

    @Test
    @DisplayName("无意图词「谢谢」：返回 CHAT")
    void detect_chatFallback_thanks() {
        assertThat(detector.detect("谢谢")).isEqualTo(IntentDetector.Intent.CHAT);
    }

    // ========== 优先级 SEARCH > HOW_TO > CHAT ==========

    @Test
    @DisplayName("优先级：SEARCH 关键词 + HOW_TO 关键词 → SEARCH 优先（求怎么发帖的教程）")
    void detect_priority_searchOverHowTo() {
        // "求"（SEARCH）+ "怎么"（HOW_TO）+ "教程"（HOW_TO）→ 应判 SEARCH
        assertThat(detector.detect("求怎么发帖的教程")).isEqualTo(IntentDetector.Intent.SEARCH);
    }

    @Test
    @DisplayName("优先级：SEARCH 关键词 + CHAT 兜底 → SEARCH 优先（找资源）")
    void detect_priority_searchOverChat() {
        assertThat(detector.detect("找资源")).isEqualTo(IntentDetector.Intent.SEARCH);
    }

    @Test
    @DisplayName("优先级：HOW_TO 关键词 + CHAT 兜底 → HOW_TO 优先（怎么发帖呀）")
    void detect_priority_howToOverChat() {
        // "怎么"（HOW_TO）+ "呀"（无意图）→ 应判 HOW_TO
        assertThat(detector.detect("怎么发帖呀")).isEqualTo(IntentDetector.Intent.HOW_TO);
    }
}
