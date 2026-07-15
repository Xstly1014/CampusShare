package com.campushare.agent.service;

import com.campushare.agent.config.KnowledgeMetricsConfig;
import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.IntentResult.SlotResult;
import com.campushare.agent.dto.RetrievalConfig;
import com.campushare.agent.enums.Intent;
import com.campushare.agent.llm.EmbeddingClient;
import com.campushare.agent.store.KnowledgeVectorStore;
import com.campushare.agent.store.PostVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalService.selectConfig 单元测试（ADR-024 意图驱动检索策略）。
 *
 * 验证点：
 *  - null/空意图 → 默认均等配置
 *  - HOW_TO → 偏知识库（knowledgeTopK=8, postTopK=0），不启用帖子关键词
 *  - SEARCH/resource → 偏帖子（postTopK=8），启用帖子关键词
 *  - SEARCH/discussion → 偏帖子，knowledgeKeywordTopK=0
 *  - SEARCH/content_qa → 偏知识库，启用帖子关键词
 *  - SEARCH/未知子意图 → 默认配置
 *  - CLARIFY → 均衡配置
 *  - OUT_OF_SCOPE/NAVIGATE → 默认配置
 *  - 低置信度 → 各路 topK + lowConfidenceBoost
 *  - slots 正确传递
 */
@DisplayName("RetrievalService.selectConfig 意图驱动检索配置")
@ExtendWith(MockitoExtension.class)
class RetrievalServiceConfigTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private KnowledgeVectorStore knowledgeVectorStore;

    @Mock
    private PostVectorStore postVectorStore;

    @Mock
    private KnowledgeMetricsConfig metricsConfig;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(retrievalService, "rerankTopK", 5);
        ReflectionTestUtils.setField(retrievalService, "lowConfidenceBoost", 3);
        ReflectionTestUtils.setField(retrievalService, "defaultTokenBudget", 2500);
        ReflectionTestUtils.setField(retrievalService, "cacheEnabled", false);
    }

    // ========== null / 默认 ==========

    @Nested
    @DisplayName("null 与默认配置")
    class NullAndDefault {

        @Test
        @DisplayName("null intent → 默认均等配置")
        void selectConfig_nullIntent_returnsDefault() {
            RetrievalConfig config = retrievalService.selectConfig(null);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
            assertThat(config.postKeywordTopK()).isEqualTo(0);
            assertThat(config.usePostKeyword()).isFalse();
            assertThat(config.slots()).isNull();
        }

        @Test
        @DisplayName("intent 字段为 null → 默认配置")
        void selectConfig_intentFieldNull_returnsDefault() {
            IntentResult intent = IntentResult.builder().build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
        }
    }

    // ========== HOW_TO ==========

    @Nested
    @DisplayName("HOW_TO：偏知识库")
    class HowTo {

        @Test
        @DisplayName("高置信度 → knowledgeTopK=8, postTopK=0")
        void howTo_highConfidence_knowledgeBiased() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .subIntent(Intent.SubIntent.FEATURE_HELP)
                    .confidence(0.95)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(8);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(0);
            assertThat(config.usePostKeyword()).isFalse();
        }

        @Test
        @DisplayName("低置信度 → knowledgeTopK=11（8+3 boost），postTopK=0")
        void howTo_lowConfidence_expandedTopK() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .subIntent(Intent.SubIntent.FEATURE_HELP)
                    .confidence(0.3)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(11);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(8);
            assertThat(config.postTopK()).isEqualTo(0);
        }

        @Test
        @DisplayName("rule_explain 子意图 → 同 HOW_TO 默认配置")
        void howTo_ruleExplain_sameAsFeatureHelp() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .subIntent(Intent.SubIntent.RULE_EXPLAIN)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(8);
            assertThat(config.postTopK()).isEqualTo(0);
        }
    }

    // ========== SEARCH ==========

    @Nested
    @DisplayName("SEARCH：按子意图差异化")
    class Search {

        @Test
        @DisplayName("resource + 高置信度 → postTopK=8, usePostKeyword=true")
        void searchResource_highConfidence_postBiased() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.RESOURCE)
                    .confidence(0.92)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(2);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(2);
            assertThat(config.postTopK()).isEqualTo(8);
            assertThat(config.postKeywordTopK()).isEqualTo(5);
            assertThat(config.usePostKeyword()).isTrue();
        }

        @Test
        @DisplayName("resource + 低置信度 → postTopK=11（8+3 boost）")
        void searchResource_lowConfidence_expandedPostTopK() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.RESOURCE)
                    .confidence(0.4)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.postTopK()).isEqualTo(11);
            assertThat(config.postKeywordTopK()).isEqualTo(8);
        }

        @Test
        @DisplayName("discussion → knowledgeKeywordTopK=0, postTopK=8")
        void searchDiscussion_noKnowledgeKeyword() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.DISCUSSION)
                    .confidence(0.85)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(2);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(0);
            assertThat(config.postTopK()).isEqualTo(8);
            assertThat(config.usePostKeyword()).isTrue();
        }

        @Test
        @DisplayName("content_qa → 偏知识库 knowledgeTopK=8, usePostKeyword=true")
        void searchContentQa_knowledgeBiasedWithPostKeyword() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.CONTENT_QA)
                    .confidence(0.88)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(8);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(3);
            assertThat(config.postKeywordTopK()).isEqualTo(2);
            assertThat(config.usePostKeyword()).isTrue();
        }

        @Test
        @DisplayName("未知子意图 → 默认配置")
        void searchUnknownSubIntent_returnsDefault() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent("unknown_sub")
                    .confidence(0.7)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
        }

        @Test
        @DisplayName("子意图为 null → 默认配置")
        void searchNullSubIntent_returnsDefault() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .confidence(0.7)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
        }
    }

    // ========== CLARIFY ==========

    @Nested
    @DisplayName("CLARIFY：均衡配置")
    class Clarify {

        @Test
        @DisplayName("CLARIFY → knowledgeTopK=5, postTopK=5, usePostKeyword=true")
        void clarify_balancedConfig() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.CLARIFY)
                    .subIntent(Intent.SubIntent.COREFERENCE)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.knowledgeKeywordTopK()).isEqualTo(3);
            assertThat(config.postTopK()).isEqualTo(5);
            assertThat(config.postKeywordTopK()).isEqualTo(3);
            assertThat(config.usePostKeyword()).isTrue();
        }

        @Test
        @DisplayName("CLARIFY 低置信度不加 boost（CLARIFY 分支不使用 confidenceBoost）")
        void clarify_lowConfidence_noBoost() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.CLARIFY)
                    .subIntent(Intent.SubIntent.FOLLOWUP)
                    .confidence(0.3)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
        }
    }

    // ========== OUT_OF_SCOPE / NAVIGATE ==========

    @Nested
    @DisplayName("OUT_OF_SCOPE / NAVIGATE：默认配置")
    class OutOfScopeAndNavigate {

        @Test
        @DisplayName("OUT_OF_SCOPE → 默认配置")
        void outOfScope_returnsDefault() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.OUT_OF_SCOPE)
                    .subIntent(Intent.SubIntent.CHITCHAT)
                    .confidence(0.97)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
            assertThat(config.usePostKeyword()).isFalse();
        }

        @Test
        @DisplayName("NAVIGATE → 默认配置")
        void navigate_returnsDefault() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.NAVIGATE)
                    .subIntent(Intent.SubIntent.FEATURE_LOC)
                    .confidence(0.94)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.knowledgeTopK()).isEqualTo(5);
            assertThat(config.postTopK()).isEqualTo(5);
        }
    }

    // ========== slots 传递 ==========

    @Nested
    @DisplayName("slots 槽位传递")
    class SlotsPassThrough {

        @Test
        @DisplayName("HOW_TO + slots → slots 传入 config")
        void howTo_slotsPassedThrough() {
            SlotResult slots = SlotResult.builder()
                    .school("清华")
                    .category("计科")
                    .postType("resource")
                    .build();
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .subIntent(Intent.SubIntent.FEATURE_HELP)
                    .confidence(0.9)
                    .slots(slots)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.slots()).isNotNull();
            assertThat(config.slots().getSchool()).isEqualTo("清华");
            assertThat(config.slots().getCategory()).isEqualTo("计科");
            assertThat(config.slots().getPostType()).isEqualTo("resource");
        }

        @Test
        @DisplayName("SEARCH/resource + slots → slots 传入 config")
        void searchResource_slotsPassedThrough() {
            SlotResult slots = SlotResult.builder()
                    .school("北大")
                    .category("音乐")
                    .build();
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.RESOURCE)
                    .confidence(0.9)
                    .slots(slots)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.slots()).isNotNull();
            assertThat(config.slots().getSchool()).isEqualTo("北大");
            assertThat(config.slots().getCategory()).isEqualTo("音乐");
            assertThat(config.slots().getPostType()).isNull();
        }

        @Test
        @DisplayName("null intent → slots 为 null")
        void nullIntent_slotsNull() {
            RetrievalConfig config = retrievalService.selectConfig(null);

            assertThat(config.slots()).isNull();
        }

        @Test
        @DisplayName("intent 无 slots → config.slots 为 null")
        void intentNoSlots_configSlotsNull() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.slots()).isNull();
        }
    }

    // ========== 通用字段 ==========

    @Nested
    @DisplayName("通用字段验证")
    class CommonFields {

        @Test
        @DisplayName("所有配置的 rerankTopK 来自 application.yml")
        void rerankTopK_fromConfig() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.rerankTopK()).isEqualTo(5);
        }

        @Test
        @DisplayName("所有配置的 tokenBudget 来自 defaultTokenBudget")
        void tokenBudget_fromConfig() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.RESOURCE)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.tokenBudget()).isEqualTo(2500);
        }

        @Test
        @DisplayName("HOW_TO similarityThreshold=0.5")
        void howTo_similarityThreshold() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.HOW_TO)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.similarityThreshold()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("SEARCH/resource similarityThreshold=0.4")
        void searchResource_similarityThreshold() {
            IntentResult intent = IntentResult.builder()
                    .intent(Intent.SEARCH)
                    .subIntent(Intent.SubIntent.RESOURCE)
                    .confidence(0.9)
                    .build();

            RetrievalConfig config = retrievalService.selectConfig(intent);

            assertThat(config.similarityThreshold()).isEqualTo(0.4);
        }
    }
}
