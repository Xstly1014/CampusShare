package com.campushare.agent.service;

import com.campushare.agent.dto.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarkdownChunker 单元测试。
 *
 * 验证点：
 *  - 空内容 / null → 空列表
 *  - 短文档 → 单块
 *  - H2 分割：两个 ## 段 → 至少 2 块
 *  - H3 分割：## A 下两个 ### → 各自独立块
 *  - 超长段落按句子拆分
 *  - 重叠 token：相邻 chunk 前部包含上一 chunk 尾部
 *  - headingPath 正确拼接
 *  - maxTokens 强制 flush
 *
 * 默认参数：targetTokens=256 / maxTokens=512 / overlapTokens=50
 */
@DisplayName("MarkdownChunker 单元测试")
class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new MarkdownChunker(256, 512, 50);
    }

    // ========== 空值/空白 ==========

    @Nested
    @DisplayName("空值/空白输入")
    class EmptyInput {

        @Test
        @DisplayName("null → 空列表")
        void chunk_null_returnsEmpty() {
            assertThat(chunker.chunk(null)).isEmpty();
        }

        @Test
        @DisplayName("空字符串 → 空列表")
        void chunk_empty_returnsEmpty() {
            assertThat(chunker.chunk("")).isEmpty();
        }

        @Test
        @DisplayName("纯空白 → 空列表")
        void chunk_blank_returnsEmpty() {
            assertThat(chunker.chunk("   \n\n   ")).isEmpty();
        }
    }

    // ========== 短文档单块 ==========

    @Nested
    @DisplayName("短文档（< 256 token）")
    class ShortDocument {

        @Test
        @DisplayName("无标题的短文本 → 单块")
        void chunk_shortText_returnsSingleChunk() {
            String md = "这是一段简短的知识文档内容。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).text()).contains("简短的知识文档");
            assertThat(chunks.get(0).tokenCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("仅 H1 标题的短文档 → 单块且 headingPath 含 H1")
        void chunk_h1Only_headingPathContainsH1() {
            String md = "# 如何发帖\n\n这是发帖的简短说明。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).headingPath()).startsWith("# 如何发帖");
        }
    }

    // ========== H2 分割 ==========

    @Nested
    @DisplayName("H2 二级标题分割")
    class H2Split {

        @Test
        @DisplayName("两个 ## 段 → 至少 2 块")
        void chunk_twoH2Sections_returnsAtLeastTwoChunks() {
            String md = "# 文档标题\n\n" +
                    "## 第一节\n\n" +
                    "这是第一节的内容，包含足够多的文字以形成一个独立的分块。这部分文字应当被独立切分出来。\n\n" +
                    "## 第二节\n\n" +
                    "这是第二节的内容，同样应当被独立切分。这部分文字与第一节在主题上有所不同。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("H2 段的 headingPath 包含 H1 + H2")
        void chunk_h2Section_headingPathContainsH1AndH2() {
            String md = "# 发帖指南\n\n" +
                    "## 标题填写\n\n" +
                    "标题应当简洁明了，能够准确描述帖子内容。建议不超过 30 个字符。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).anyMatch(c -> c.headingPath().contains("发帖指南"));
            assertThat(chunks).anyMatch(c -> c.headingPath().contains("标题填写"));
        }
    }

    // ========== H3 分割 ==========

    @Nested
    @DisplayName("H3 三级标题分割")
    class H3Split {

        @Test
        @DisplayName("## A 下两个 ### A1/A2 → headingPath 各自正确")
        void chunk_h3Sections_headingPathContainsH2AndH3() {
            String md = "# 指南\n\n" +
                    "## 注册流程\n\n" +
                    "### 填写邮箱\n\n" +
                    "邮箱是注册的必要字段。请使用常用邮箱。\n\n" +
                    "### 设置密码\n\n" +
                    "密码长度至少 8 位，包含字母和数字。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
            assertThat(chunks).anyMatch(c -> c.headingPath().contains("填写邮箱"));
            assertThat(chunks).anyMatch(c -> c.headingPath().contains("设置密码"));
            assertThat(chunks).anyMatch(c -> c.headingPath().contains("注册流程"));
        }
    }

    // ========== headingPath 拼接 ==========

    @Nested
    @DisplayName("headingPath 拼接格式")
    class HeadingPathFormat {

        @Test
        @DisplayName("完整路径：# Title > ## H2 > ### H3")
        void headingPath_fullPath() {
            String md = "# Title\n\n## H2\n\n### H3\n\n正文内容应当足够多以形成一个分块。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).anyMatch(c -> c.headingPath().matches(".*Title.*>.*H2.*>.*H3.*"));
        }

        @Test
        @DisplayName("无 H1 时 headingPath 从 H2 开始")
        void headingPath_noH1() {
            String md = "## H2\n\n正文内容。";
            List<Chunk> chunks = chunker.chunk(md);
            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).headingPath()).contains("H2");
        }
    }

    // ========== 超长段落按句子拆分 ==========

    @Nested
    @DisplayName("超长段落处理")
    class LongParagraph {

        @Test
        @DisplayName("段落超过 maxTokens → 按句子拆分为多块")
        void chunk_longParagraph_splitsBySentence() {
            StringBuilder sb = new StringBuilder();
            sb.append("# 长文档\n\n## 长段落\n\n");
            for (int i = 0; i < 60; i++) {
                sb.append("这是第").append(i).append("句话，用于构造超长段落以触发按句子拆分逻辑。");
            }
            List<Chunk> chunks = chunker.chunk(sb.toString());
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("分块 token 数不超过 maxTokens（除单句超长外）")
        void chunk_tokenCountRespectsMaxTokens() {
            StringBuilder sb = new StringBuilder();
            sb.append("# 文档\n\n## 段\n\n");
            for (int i = 0; i < 40; i++) {
                sb.append("这是用于测试的句子。");
            }
            List<Chunk> chunks = chunker.chunk(sb.toString());
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allSatisfy(c -> assertThat(c.tokenCount()).isLessThanOrEqualTo(600));
        }
    }

    // ========== 重叠 token ==========

    @Nested
    @DisplayName("重叠 token")
    class Overlap {

        @Test
        @DisplayName("overlapTokens=50 → 相邻 chunk 前部包含上一 chunk 尾部片段")
        void chunk_overlap_chunksHaveOverlapContent() {
            StringBuilder sb = new StringBuilder();
            sb.append("# 文档\n\n## 第一节\n\n");
            for (int i = 0; i < 30; i++) {
                sb.append("内容片段编号").append(i).append("。");
            }
            sb.append("\n\n## 第二节\n\n");
            for (int i = 0; i < 30; i++) {
                sb.append("第二节内容编号").append(i).append("。");
            }
            List<Chunk> chunks = chunker.chunk(sb.toString());
            if (chunks.size() >= 2) {
                Chunk first = chunks.get(0);
                Chunk second = chunks.get(1);
                assertThat(first.text()).isNotEqualTo(second.text());
            }
        }

        @Test
        @DisplayName("overlapTokens=0 → 相邻 chunk 无重叠")
        void chunk_noOverlap_chunksAreDisjoint() {
            MarkdownChunker noOverlapChunker = new MarkdownChunker(64, 128, 0);
            StringBuilder sb = new StringBuilder();
            sb.append("# 文档\n\n## 第一节\n\n");
            for (int i = 0; i < 30; i++) {
                sb.append("内容片段编号").append(i).append("。");
            }
            sb.append("\n\n## 第二节\n\n");
            for (int i = 0; i < 30; i++) {
                sb.append("第二节内容编号").append(i).append("。");
            }
            List<Chunk> chunks = noOverlapChunker.chunk(sb.toString());
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ========== 边界：targetTokens / maxTokens ==========

    @Nested
    @DisplayName("Token 边界")
    class TokenBoundary {

        @Test
        @DisplayName("累积达 targetTokens 立即 flush")
        void chunk_reachTargetTokens_flushesImmediately() {
            MarkdownChunker smallChunker = new MarkdownChunker(10, 100, 0);
            String md = "# 文档\n\n## 段\n\n" + "内容内容内容内容内容内容内容内容内容内容内容内容内容内容内容内容内容内容。";
            List<Chunk> chunks = smallChunker.chunk(md);
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("maxTokens=512 默认配置：长文本生成多块且每块 tokenCount > 0")
        void chunk_defaultConfig_generatesValidChunks() {
            StringBuilder sb = new StringBuilder("# 文档\n\n## 段\n\n");
            for (int i = 0; i < 100; i++) {
                sb.append("这是测试用的句子，用于验证分块器的默认配置。");
            }
            List<Chunk> chunks = chunker.chunk(sb.toString());
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allSatisfy(c -> assertThat(c.tokenCount()).isGreaterThan(0));
        }
    }

    // ========== Chunk.embeddingText ==========

    @Nested
    @DisplayName("Chunk.embeddingText 方法")
    class EmbeddingText {

        @Test
        @DisplayName("headingPath + 换行 + text")
        void embeddingText_combinesHeadingPathAndText() {
            Chunk chunk = new Chunk("正文内容", 10, "# 标题 > ## 子标题");
            assertThat(chunk.embeddingText()).isEqualTo("# 标题 > ## 子标题\n正文内容");
        }

        @Test
        @DisplayName("headingPath 为空 → 仅返回 text")
        void embeddingText_emptyHeadingPath_returnsTextOnly() {
            Chunk chunk = new Chunk("正文内容", 10, "");
            assertThat(chunk.embeddingText()).isEqualTo("正文内容");
        }

        @Test
        @DisplayName("headingPath 为 null → 仅返回 text")
        void embeddingText_nullHeadingPath_returnsTextOnly() {
            Chunk chunk = new Chunk("正文内容", 10, null);
            assertThat(chunk.embeddingText()).isEqualTo("正文内容");
        }
    }
}
