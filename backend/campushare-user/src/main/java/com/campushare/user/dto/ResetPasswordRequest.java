package com.campushare.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    
    @NotBlank(message = "账号不能为空")
    private String account;
    
    @NotBlank(message = "验证码不能为空")
    private String verifyCode;
    
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
