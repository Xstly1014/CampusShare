package com.campushare.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryCreateRequest {

    @Size(max = 64, message = "分类名不能超过64字")
    private String name;
}
