package com.campushare.agent.llm.gateway;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmStreamChunk {

    private String content;

    private LlmResponse.Usage usage;

    private boolean isDone;

    private String errorMessage;

    public static LlmStreamChunk content(String content) {
        return new LlmStreamChunk(content, null, false, null);
    }

    public static LlmStreamChunk usage(LlmResponse.Usage usage) {
        return new LlmStreamChunk(null, usage, false, null);
    }

    public static LlmStreamChunk done() {
        return new LlmStreamChunk(null, null, true, null);
    }

    public static LlmStreamChunk error(String errorMessage) {
        return new LlmStreamChunk(null, null, false, errorMessage);
    }
}
