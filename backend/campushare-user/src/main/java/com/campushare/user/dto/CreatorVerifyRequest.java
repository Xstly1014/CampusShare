package com.campushare.user.dto;

import lombok.Data;

@Data
public class CreatorVerifyRequest {
    private boolean approved;
    private String rejectReason;
    private String reviewNote;
}
