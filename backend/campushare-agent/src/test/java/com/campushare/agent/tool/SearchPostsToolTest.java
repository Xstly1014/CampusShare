package com.campushare.agent.tool;

import com.campushare.agent.dto.IntentResult;
import com.campushare.agent.dto.RetrievalResult;
import com.campushare.agent.service.RetrievalService;
import com.campushare.agent.tool.function.SearchPostsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchPostsTool 帖子搜索工具测试")
class SearchPostsToolTest {

    @Mock
    private RetrievalService retrievalService;

    private SearchPostsTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchPostsTool(retrievalService);
    }

    @Test
    @DisplayName("带可选参数搜索并返回帖子结果")
    void execute_withOptionalParams_returnsPosts() {
        RetrievalResult result = RetrievalResult.post("post-1", "操作系统期末卷子",
                "这里有一份操作系统期末复习资料", 0.92, Map.of("school", "清华"));
        when(retrievalService.retrieve(eq("操作系统 期末"), any(IntentResult.class), eq(null)))
                .thenReturn(Mono.just(List.of(result)));

        Map<String, Object> args = Map.of(
                "query", "操作系统 期末",
                "school", "清华",
                "category", "资料",
                "post_type", "resource",
                "sort", "hottest"
        );

        ToolResult toolResult = tool.execute(args, "user-123");

        assertThat(toolResult.getStatus()).isEqualTo(ToolResult.Status.SUCCESS);
        assertThat(toolResult.getSummary()).isEqualTo("Found 1 relevant posts");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) toolResult.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) data.get("results");
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).get("post_id")).isEqualTo("post-1");
        assertThat(posts.get(0).get("title")).isEqualTo("操作系统期末卷子");

        ArgumentCaptor<IntentResult> captor = ArgumentCaptor.forClass(IntentResult.class);
        verifyRetrieve(captor);
        IntentResult intent = captor.getValue();
        assertThat(intent.getIntent()).isEqualTo(com.campushare.agent.enums.Intent.SEARCH);
        assertThat(intent.getSubIntent()).isEqualTo(com.campushare.agent.enums.Intent.SubIntent.RESOURCE);
        assertThat(intent.getSlots().getSchool()).isEqualTo("清华");
        assertThat(intent.getSlots().getCategory()).isEqualTo("资料");
        assertThat(intent.getSlots().getPostType()).isEqualTo("resource");
        assertThat(intent.getSlots().getSort()).isEqualTo("hottest");
    }

    @Test
    @DisplayName("无结果时返回 EMPTY")
    void execute_noResults_returnsEmpty() {
        when(retrievalService.retrieve(eq("不存在的资料"), any(IntentResult.class), eq(null)))
                .thenReturn(Mono.just(List.of()));

        ToolResult result = tool.execute(Map.of("query", "不存在的资料"), "user-123");

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.EMPTY);
    }

    @Test
    @DisplayName("缺少 query 时返回参数错误")
    void execute_missingQuery_returnsError() {
        ToolResult result = tool.execute(Map.of("school", "北大"), "user-123");

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_ARGS_INVALID");
    }

    private void verifyRetrieve(ArgumentCaptor<IntentResult> captor) {
        org.mockito.Mockito.verify(retrievalService).retrieve(eq("操作系统 期末"), captor.capture(), eq(null));
    }
}
