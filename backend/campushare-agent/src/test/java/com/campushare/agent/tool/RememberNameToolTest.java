package com.campushare.agent.tool;

import com.campushare.agent.service.LongTermMemoryService;
import com.campushare.agent.tool.function.RememberNameTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RememberNameTool 昵称记忆工具测试")
class RememberNameToolTest {

    @Mock
    private LongTermMemoryService longTermMemoryService;

    private RememberNameTool tool;

    @BeforeEach
    void setUp() {
        tool = new RememberNameTool(longTermMemoryService);
    }

    @Test
    @DisplayName("保存有效昵称并返回成功")
    void execute_validNickname_savesAndReturnsSuccess() {
        String userId = "user-123";
        String nickname = "yuuki";

        ToolResult result = tool.execute(Map.of("nickname", nickname), userId);

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.SUCCESS);
        assertThat(result.getData()).isEqualTo("Name remembered: " + nickname);
        verify(longTermMemoryService).saveNickname(userId, nickname);
    }

    @Test
    @DisplayName("超长昵称自动截断到 20 字符")
    void execute_longNickname_truncatesTo20Chars() {
        String userId = "user-456";
        String longNickname = "这是一个超过二十个字符的很长很长很长的昵称";
        String expected = longNickname.substring(0, 20);

        ToolResult result = tool.execute(Map.of("nickname", longNickname), userId);

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.SUCCESS);
        assertThat(result.getData()).isEqualTo("Name remembered: " + expected);
        verify(longTermMemoryService).saveNickname(userId, expected);
    }

    @Test
    @DisplayName("昵称为空时返回错误且不调用服务")
    void execute_blankNickname_returnsError() {
        String userId = "user-789";

        ToolResult result = tool.execute(Map.of("nickname", "   "), userId);

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_ARGS_INVALID");
        verify(longTermMemoryService, never()).saveNickname(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("缺少 nickname 参数时返回错误")
    void execute_missingNickname_returnsError() {
        ToolResult result = tool.execute(Map.of(), "user-000");

        assertThat(result.getStatus()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(result.getErrorCode()).isEqualTo("TOOL_ARGS_INVALID");
    }
}
