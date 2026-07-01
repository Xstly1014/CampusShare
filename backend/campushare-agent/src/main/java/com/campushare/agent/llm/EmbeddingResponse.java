package com.campushare.agent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 硅基流动 / OpenAI 兼容的 Embedding 响应 DTO。
 *
 * 响应格式：
 * {
 *   "data": [
 *     {"embedding": [0.1, 0.2, ...], "index": 0}
 *   ],
 *   "model": "BAAI/bge-m3",
 *   "usage": {"prompt_tokens": 10, "total_tokens": 10}
 * }
 *
 * BGE-M3 输出 1024 维稠密向量。
 */
@Data
public class EmbeddingResponse {

    private List<EmbeddingItem> data;

    private String model;

    private Usage usage;

    @Data
    public static class EmbeddingItem {
        private float[] embedding;
        private int index;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}
