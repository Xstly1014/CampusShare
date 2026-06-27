package com.campushare.user.dto;

import lombok.Data;

@Data
public class BindAccountRequest {
    private String account;
    private String verifyCode;
}
