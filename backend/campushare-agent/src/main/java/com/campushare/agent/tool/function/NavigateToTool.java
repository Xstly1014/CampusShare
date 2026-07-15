package com.campushare.agent.tool.function;

import com.campushare.agent.tool.Tool;
import com.campushare.agent.tool.ToolDef;
import com.campushare.agent.tool.ToolParam;
import com.campushare.agent.tool.ToolResult;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ToolDef(
        name = "navigate_to",
        description = "When the user asks for a page or feature location, return the route to navigate.",
        intent = {"NAVIGATE", "HOW_TO"},
        readOnly = true,
        timeoutMs = 1000
)
@Component
public class NavigateToTool implements Tool {

    @Data
    public static class Params {
        @ToolParam(name = "route", description = "The route path to navigate to, e.g. '/profile' or '/help'", required = true, type = "string")
        private String route;

        @ToolParam(name = "label", description = "Human-readable label for the navigation target. Optional.", required = false, type = "string")
        private String label;
    }

    @Override
    public Class<?> getParameterClass() {
        return Params.class;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, String userId) {
        String route = (String) arguments.get("route");
        if (route == null || route.isBlank()) {
            return ToolResult.error("TOOL_ARGS_INVALID", "route parameter is required");
        }

        String label = (String) arguments.get("label");
        if (label == null || label.isBlank()) {
            label = "Click to navigate";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("route", route);
        data.put("label", label);

        return ToolResult.success(data);
    }
}
