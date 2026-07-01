package com.campushare.agent.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 硅基流动 / OpenAI 兼容的 Embedding 请求 DTO。
 *
 * 请求格式：
 * POST /v1/embeddings
 * {
 *   "model": "BAAI/bge-m3",
 *   "input": "文本内容",          // 单条 String 或批量 List<String>
 *   "encoding_format": "float"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    private String model;

    private Object input;

    @JsonProperty("encoding_format")
    @Builder.Default
    private String encodingFormat = "float";
}
