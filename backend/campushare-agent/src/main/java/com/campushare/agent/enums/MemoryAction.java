package com.campushare.agent.enums;

import lombok.Getter;

@Getter
public enum MemoryAction {
    INSERT("INSERT"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    DECAY("DECAY"),
    CONFLICT_RESOLVED("CONFLICT_RESOLVED"),
    ACCESSED("ACCESSED");

    private final String code;

    MemoryAction(String code) {
        this.code = code;
    }
}
