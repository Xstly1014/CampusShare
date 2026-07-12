package com.campushare.agent.tool;

import com.campushare.agent.enums.Intent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();
    private final Map<String, ToolDefinition> definitionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Map<String, Tool> tools = applicationContext.getBeansOfType(Tool.class);
        for (Tool tool : tools.values()) {
            ToolDef def = tool.getClass().getAnnotation(ToolDef.class);
            if (def == null) {
                log.warn("Tool {} has no @ToolDef annotation, skipping", tool.getClass().getName());
                continue;
            }
            if (!def.readOnly()) {
                throw new IllegalStateException(
                        "Write operation tools are not allowed: " + def.name() +
                        " (ADR-TOOL-02: Agent cannot perform write operations)");
            }
            if (toolMap.containsKey(def.name())) {
                throw new IllegalStateException("Duplicate tool name: " + def.name());
            }
            toolMap.put(def.name(), tool);
            definitionMap.put(def.name(), buildDefinition(def, tool));
            log.info("Registered tool: {}", def.name());
        }
        log.info("ToolRegistry initialized: {} tools registered", toolMap.size());
    }

    private ToolDefinition buildDefinition(ToolDef def, Tool tool) {
        return new ToolDefinition(
                def.name(),
                def.description(),
                Arrays.asList(def.intent()),
                def.readOnly(),
                def.timeoutMs(),
                buildParametersSchema(tool)
        );
    }

    private Map<String, Object> buildParametersSchema(Tool tool) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        schema.put("properties", properties);
        schema.put("required", required);

        Class<?> paramClass = tool.getParameterClass();
        if (paramClass == null) {
            return schema;
        }

        for (java.lang.reflect.Field field : paramClass.getDeclaredFields()) {
            ToolParam param = field.getAnnotation(ToolParam.class);
            if (param == null) continue;

            Map<String, Object> prop = new HashMap<>();
            prop.put("type", param.type());
            prop.put("description", param.description());

            if (param.enumValues().length > 0) {
                prop.put("enum", java.util.Arrays.asList(param.enumValues()));
            }

            properties.put(param.name(), prop);

            if (param.required()) {
                required.add(param.name());
            }
        }

        return schema;
    }

    public List<Map<String, Object>> getToolSchemas(Intent intent) {
        return definitionMap.values().stream()
                .filter(d -> d.intents().isEmpty() || d.intents().contains(intent.name()))
                .map(this::toOpenAIFunction)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAllToolSchemas() {
        return definitionMap.values().stream()
                .map(this::toOpenAIFunction)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toOpenAIFunction(ToolDefinition def) {
        Map<String, Object> function = new HashMap<>();
        function.put("type", "function");
        Map<String, Object> func = new HashMap<>();
        func.put("name", def.name());
        func.put("description", def.description());
        func.put("parameters", def.parametersSchema());
        function.put("function", func);
        return function;
    }

    public Tool getTool(String name) {
        return toolMap.get(name);
    }

    public boolean hasTool(String name) {
        return toolMap.containsKey(name);
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(toolMap.keySet());
    }

    public ToolDefinition getDefinition(String name) {
        return definitionMap.get(name);
    }

    public record ToolDefinition(
            String name,
            String description,
            List<String> intents,
            boolean readOnly,
            int timeoutMs,
            Map<String, Object> parametersSchema
    ) {}
}
