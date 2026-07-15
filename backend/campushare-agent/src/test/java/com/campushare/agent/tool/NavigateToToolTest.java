package com.campushare.agent.tool;

import com.campushare.agent.tool.function.NavigateToTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NavigateToTool 页面导航工具测试")
class NavigateToToolTest {

    private NavigateToTool tool;

    @BeforeEach
    void setUp() {
        tool = new NavigateToTool();
    }

    @Test
    @DisplayName("返回指定路由和标签")
    void execute_withRouteAndLabel_returnsNavigation() {
        ToolResult result = tool.execute(Map.of("route", "/profile", "label", "个人主页"), "user-123");

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.SUCCESS);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data.get("route")).isEqualTo("/profile");
        assertThat(data.get("label")).isEqualTo("个人主页");
    }

    @Test
    @DisplayName("未提供标签时使用默认标签")
    void execute_withoutLabel_usesDefaultLabel() {
        ToolResult result = tool.execute(Map.of("route", "/help"), "user-123");

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.SUCCESS);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data.get("route")).isEqualTo("/help");
        assertThat(data.get("label")).isEqualTo("Click to navigate");
    }

    @Test
    @DisplayName("缺少 route 时返回参数错误")
    void execute_missingRoute_returnsError() {
        ToolResult result = tool.execute(Map.of("label", "首页"), "user-123");

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_ARGS_INVALID");
    }
}
