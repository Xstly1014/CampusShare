package com.campushare.agent.enums;

import lombok.Getter;

@Getter
public enum MemorySource {
    EXPLICIT("EXPLICIT", "用户明确声明"),
    IMPLICIT("IMPLICIT", "隐式记忆"),
    INFERRED("INFERRED", "行为推断");

    private final String code;
    private final String label;

    MemorySource(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static MemorySource fromCode(String code) {
        if (code == null) return null;
        for (MemorySource source : values()) {
            if (source.code.equals(code)) return source;
        }
        return null;
    }
}
