package com.campushare.common.result;

import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
public enum ResultCode {
    
    /* 成功状态码 */
    SUCCESS(200, "操作成功"),
    
    /* 参数错误：1xxx */
    PARAM_NOT_VALID(1001, "参数无效"),
    PARAM_IS_BLANK(1002, "参数不能为空"),
    PARAM_TYPE_ERROR(1003, "参数类型错误"),
    
    /* 用户错误：2xxx */
    USER_NOT_LOGIN(2001, "用户未登录"),
    USER_LOGIN_ERROR(2002, "账号或密码错误"),
    USER_ACCOUNT_FORBIDDEN(2003, "账号已被禁用"),
    USER_ACCOUNT_NOT_EXIST(2004, "账号不存在"),
    USER_ACCOUNT_ALREADY_EXIST(2005, "账号已存在"),
    USER_OLD_PASSWORD_ERROR(2006, "原密码错误"),
    
    /* 验证码错误：3xxx */
    VERIFY_CODE_NOT_FOUND(3001, "验证码不存在"),
    VERIFY_CODE_ERROR(3002, "验证码错误"),
    VERIFY_CODE_EXPIRED(3003, "验证码已过期"),
    VERIFY_CODE_SEND_TOO_FREQUENT(3004, "发送验证码过于频繁"),

    /* 邮件错误：3xxx */
    EMAIL_SEND_FAILED(3005, "邮件发送失败，请稍后重试"),
    EMAIL_NOT_CONFIGURED(3006, "邮件服务未配置"),
    
    /* Token错误：4xxx */
    TOKEN_NOT_FOUND(4001, "Token不存在"),
    TOKEN_INVALID(4002, "Token无效"),
    TOKEN_EXPIRED(4003, "Token已过期"),
    
    /* 资源错误：5xxx */
    RESOURCE_NOT_FOUND(5001, "资源不存在"),
    RESOURCE_UPLOAD_ERROR(5002, "资源上传失败"),
    RESOURCE_DOWNLOAD_ERROR(5003, "资源下载失败"),
    
    /* 服务器错误：5xx */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用");
    
    private final int code;
    private final String message;
    
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}