package com.campushare.user.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送验证码邮件
     *
     * @param toEmail      收件人邮箱
     * @param verifyCode   验证码
     */
    void sendVerifyCodeEmail(String toEmail, String verifyCode);
}
