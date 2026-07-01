package com.campushare.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SessionCreateRequest {

    @Size(max = 200, message = "标题不能超过200字")
    private String title;
}
