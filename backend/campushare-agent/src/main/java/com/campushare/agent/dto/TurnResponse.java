package com.campushare.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnResponse {

    private String id;
    private String sessionId;
    private Integer turnNumber;
    private String userMessage;
    private String assistantMessage;
    private Integer tokensUsed;
    private String status;
    private LocalDateTime createdAt;
    private List<Map<String, Object>> refs;
    private Map<String, String> navigate;
}
