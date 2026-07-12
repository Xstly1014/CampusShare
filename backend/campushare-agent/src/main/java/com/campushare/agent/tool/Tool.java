package com.campushare.agent.tool;

import com.campushare.agent.enums.Intent;

import java.util.List;
import java.util.Map;

public interface Tool {
    ToolResult execute(Map<String, Object> arguments, String userId);

    default String getName() {
        ToolDef def = this.getClass().getAnnotation(ToolDef.class);
        return def != null ? def.name() : this.getClass().getSimpleName();
    }

    default boolean isReadOnly() {
        ToolDef def = this.getClass().getAnnotation(ToolDef.class);
        return def == null || def.readOnly();
    }

    default List<Intent> getApplicableIntents() {
        ToolDef def = this.getClass().getAnnotation(ToolDef.class);
        if (def == null) return List.of();
        return java.util.Arrays.stream(def.intent())
                .map(Intent::valueOf)
                .toList();
    }

    default Class<?> getParameterClass() {
        return null;
    }
}
