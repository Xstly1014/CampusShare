package com.campushare.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorApplyRequest {
    private String realName;
    private String idCard;
    private String idCardFront;
    private String idCardBack;
}
