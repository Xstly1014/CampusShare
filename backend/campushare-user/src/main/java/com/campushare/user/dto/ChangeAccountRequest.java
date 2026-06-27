package com.campushare.user.dto;

import lombok.Data;

/**
 * 换绑邮箱/手机号请求
 * - 初次绑定：只需 newAccount + newVerifyCode
 * - 换绑：需先验证 originalAccount + originalVerifyCode，再绑定 newAccount + newVerifyCode
 * - 实名认证换绑：提供 realNameVerify=true 跳过原绑定验证（需配合实名认证流程）
 */
@Data
public class ChangeAccountRequest {
    /** 原绑定账号（换绑时必填） */
    private String originalAccount;
    /** 原账号验证码（换绑时必填） */
    private String originalVerifyCode;
    /** 新账号 */
    private String newAccount;
    /** 新账号验证码 */
    private String newVerifyCode;
    /** 是否通过实名认证换绑（预留） */
    private Boolean realNameVerify;
}
