package com.campushare.agent.enums;

import lombok.Getter;

@Getter
public enum MemoryType {
    PREFERENCE("PREFERENCE", "用户偏好", 1),
    FACT("FACT", "用户事实", 2),
    BEHAVIOR("BEHAVIOR", "行为模式", 3),
    TASK("TASK", "当前任务", 4),
    SKILL("SKILL", "用户技能", 5),
    EVENT("EVENT", "相关事件", 6);

    private final String code;
    private final String label;
    private final int priority;

    MemoryType(String code, String label, int priority) {
        this.code = code;
        this.label = label;
        this.priority = priority;
    }

    public static MemoryType fromCode(String code) {
        if (code == null) return null;
        for (MemoryType type : values()) {
            if (type.code.equals(code)) return type;
        }
        return null;
    }
}
