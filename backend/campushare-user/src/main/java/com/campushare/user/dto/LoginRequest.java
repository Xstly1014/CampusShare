package com.campushare.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    /**
     * 账号（手机号/邮箱）
     */
    @NotBlank(message = "账号不能为空")
    private String account;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
    
    /**
     * 记住我
     */
    private Boolean rememberMe;
}