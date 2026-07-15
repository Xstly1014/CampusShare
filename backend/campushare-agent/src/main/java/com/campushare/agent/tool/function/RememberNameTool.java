package com.campushare.agent.tool.function;

import com.campushare.agent.service.LongTermMemoryService;
import com.campushare.agent.tool.Tool;
import com.campushare.agent.tool.ToolDef;
import com.campushare.agent.tool.ToolParam;
import com.campushare.agent.tool.ToolResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@ToolDef(
        name = "remember_name",
        description = "When the user tells you their name or asks you to call them something, save it to long-term memory.",
        readOnly = false,
        timeoutMs = 2000
)
@Component
@RequiredArgsConstructor
public class RememberNameTool implements Tool {

    private final LongTermMemoryService longTermMemoryService;

    @Data
    public static class Params {
        @ToolParam(name = "nickname", description = "The name or nickname the user wants to be called. Max 20 characters.", required = true, type = "string")
        private String nickname;
    }

    @Override
    public Class<?> getParameterClass() {
        return Params.class;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, String userId) {
        String nickname = arguments.get("nickname") != null ? ((String) arguments.get("nickname")).trim() : null;
        if (nickname == null || nickname.isBlank()) {
            return ToolResult.error("TOOL_ARGS_INVALID", "nickname parameter is required");
        }
        if (nickname.length() > 20) {
            nickname = nickname.substring(0, 20);
        }

        longTermMemoryService.saveNickname(userId, nickname);
        return ToolResult.success("Name remembered: " + nickname);
    }
}
