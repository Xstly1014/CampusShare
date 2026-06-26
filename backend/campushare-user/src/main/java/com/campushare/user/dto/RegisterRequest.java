package com.campushare.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    /**
     * 注册类型：phone-手机号，email-邮箱
     */
    @NotBlank(message = "注册类型不能为空")
    private String registerType;
    
    /**
     * 账号（手机号或邮箱）
     */
    @NotBlank(message = "账号不能为空")
    private String account;
    
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
    
    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
}