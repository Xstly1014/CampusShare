package com.campushare.agent.store;

import com.campushare.agent.dto.IntentResult.SlotResult;
import com.campushare.agent.dto.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostVectorStore 带 slots 过滤检索的单元测试（ADR-025）。
 *
 * 验证点：
 *  - search(float[], int, SlotResult) null/空 slots → 走原方法（SQL 不含 WHERE 1=1）
 *  - search 有 school/category/postType → SQL 含对应 AND 条件
 *  - keywordSearch(String, int, SlotResult) null/空 slots → 走原方法
 *  - keywordSearch 有 slots → SQL 含对应 AND 条件
 *  - 返回结果正确映射为 RetrievalResult
 */
@DisplayName("PostVectorStore 带 slots 过滤检索")
class PostVectorStoreTest {

    private JdbcTemplate jdbcTemplate;
    private PostVectorStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        store = new PostVectorStore(jdbcTemplate);
    }

    // ========== search 带过滤 ==========

    @Nested
    @DisplayName("search(float[], int, SlotResult)：向量检索 + slots 过滤")
    class SearchWithSlots {

        @Test
        @DisplayName("null slots → 走原方法（SQL 不含 WHERE 1=1）")
        void search_nullSlots_callsOriginalSearch() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f, 2.0f}, 10, null);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue()).doesNotContain("WHERE 1=1");
        }

        @Test
        @DisplayName("全空 slots → 走原方法")
        void search_emptySlots_callsOriginalSearch() {
            SlotResult slots = SlotResult.builder().build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f}, 5, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue()).doesNotContain("WHERE 1=1");
        }

        @Test
        @DisplayName("有 school → SQL 含 AND school_name ILIKE ?，空结果触发兜底查询")
        void search_withSchoolFilter_buildsWhereClause() {
            SlotResult slots = SlotResult.builder().school("清华").build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f}, 10, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            List<String> sqls = sqlCaptor.getAllValues();
            assertThat(sqls.get(0))
                    .contains("WHERE 1=1")
                    .contains("AND school_name ILIKE ?")
                    .doesNotContain("AND category_name ILIKE ?")
                    .doesNotContain("AND post_type = ?");
            assertThat(sqls.get(1))
                    .contains("WHERE 1=1")
                    .doesNotContain("AND school_name ILIKE ?")
                    .doesNotContain("AND category_name ILIKE ?")
                    .doesNotContain("AND post_type = ?");
        }

        @Test
        @DisplayName("有 category → SQL 含 AND category_name ILIKE ?")
        void search_withCategoryFilter_buildsWhereClause() {
            SlotResult slots = SlotResult.builder().category("计科").build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f}, 10, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue())
                    .contains("WHERE 1=1")
                    .contains("AND category_name ILIKE ?")
                    .doesNotContain("AND school_name ILIKE ?");
        }

        @Test
        @DisplayName("有 postType → SQL 含 AND post_type = ?")
        void search_withPostTypeFilter_buildsWhereClause() {
            SlotResult slots = SlotResult.builder().postType("resource").build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f}, 10, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue())
                    .contains("WHERE 1=1")
                    .contains("AND post_type = ?");
        }

        @Test
        @DisplayName("全字段 slots → SQL 含三个 AND 条件，空结果触发兜底查询")
        void search_withAllFilters_buildsAllWhereClauses() {
            SlotResult slots = SlotResult.builder()
                    .school("清华")
                    .category("计科")
                    .postType("resource")
                    .build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f}, 10, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            List<String> sqls = sqlCaptor.getAllValues();
            assertThat(sqls.get(0))
                    .contains("WHERE 1=1")
                    .contains("AND school_name ILIKE ?")
                    .contains("AND category_name ILIKE ?")
                    .contains("AND post_type = ?");
            assertThat(sqls.get(1))
                    .contains("WHERE 1=1")
                    .doesNotContain("AND school_name ILIKE ?")
                    .contains("AND category_name ILIKE ?")
                    .contains("AND post_type = ?");
        }

        @Test
        @DisplayName("检索结果正确映射为 RetrievalResult.post")
        void search_returnsResults_mappedToRetrievalResult() {
            SlotResult slots = SlotResult.builder().school("清华").build();
            RetrievalResult mockResult = RetrievalResult.post("p1", "测试帖子", "内容摘要", 0.95,
                    Map.of("category", "计科", "school", "清华"));
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(mockResult));

            List<RetrievalResult> results = store.search(new float[]{1.0f}, 10, slots);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("p1");
            assertThat(results.get(0).title()).isEqualTo("测试帖子");
            assertThat(results.get(0).score()).isEqualTo(0.95);
            assertThat(results.get(0).source()).isEqualTo(RetrievalResult.Source.POST);
        }
    }

    // ========== keywordSearch 带过滤 ==========

    @Nested
    @DisplayName("keywordSearch(String, int, SlotResult)：关键词检索 + slots 过滤")
    class KeywordSearchWithSlots {

        @Test
        @DisplayName("null slots → 走原方法（SQL 不含 AND school）")
        void keywordSearch_nullSlots_callsOriginalKeywordSearch() {
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.keywordSearch("操作系统", 10, null);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue()).doesNotContain("AND school = ?");
        }

        @Test
        @DisplayName("有 school → SQL 含 AND school_name ILIKE ?")
        void keywordSearch_withSchoolFilter_buildsWhereClause() {
            SlotResult slots = SlotResult.builder().school("北大").build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.keywordSearch("操作系统", 10, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue())
                    .contains("AND school_name ILIKE ?")
                    .contains("ORDER BY sim DESC");
        }

        @Test
        @DisplayName("有 category → SQL 含 AND category_name ILIKE ?")
        void keywordSearch_withCategoryFilter_buildsWhereClause() {
            SlotResult slots = SlotResult.builder().category("音乐").build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.keywordSearch("钢琴", 5, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue()).contains("AND category_name ILIKE ?");
        }

        @Test
        @DisplayName("全字段 slots → SQL 含三个 AND 条件")
        void keywordSearch_withAllFilters_buildsAllWhereClauses() {
            SlotResult slots = SlotResult.builder()
                    .school("北大")
                    .category("音乐")
                    .postType("resource")
                    .build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.keywordSearch("钢琴谱", 5, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue())
                    .contains("AND school_name ILIKE ?")
                    .contains("AND category_name ILIKE ?")
                    .contains("AND post_type = ?");
        }

        @Test
        @DisplayName("检索结果正确映射为 RetrievalResult.post")
        void keywordSearch_returnsResults_mappedToRetrievalResult() {
            SlotResult slots = SlotResult.builder().school("北大").build();
            RetrievalResult mockResult = RetrievalResult.post("p2", "北大钢琴谱", "钢琴谱资源", 0.85,
                    Map.of("category", "音乐", "school", "北大"));
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of(mockResult));

            List<RetrievalResult> results = store.keywordSearch("钢琴谱", 5, slots);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("p2");
            assertThat(results.get(0).title()).isEqualTo("北大钢琴谱");
            assertThat(results.get(0).source()).isEqualTo(RetrievalResult.Source.POST);
        }
    }

    // ========== 空白字符串 slots ==========

    @Nested
    @DisplayName("空白字符串 slots 视为无过滤")
    class BlankSlots {

        @Test
        @DisplayName("school 为空字符串 → 走原方法")
        void search_blankSchool_callsOriginalSearch() {
            SlotResult slots = SlotResult.builder().school("").category("").postType("").build();
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            store.search(new float[]{1.0f}, 10, slots);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
            assertThat(sqlCaptor.getValue()).doesNotContain("WHERE 1=1");
        }
    }
}
