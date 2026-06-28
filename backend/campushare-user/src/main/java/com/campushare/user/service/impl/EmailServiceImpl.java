package com.campushare.user.service.impl;

import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.ResultCode;
import com.campushare.user.service.EmailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:campusshare@qq.com}")
    private String fromEmail;

    @Value("${mail.from-name:CampusShare}")
    private String fromName;

    @Override
    public void sendVerifyCodeEmail(String toEmail, String verifyCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromName + " <" + fromEmail + ">");
            message.setTo(toEmail);
            message.setSubject("【CampusShare】您的验证码");
            message.setText(buildEmailContent(verifyCode));

            mailSender.send(message);
            log.info("验证码邮件发送成功: to={}", toEmail);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: to={}, error={}", toEmail, e.getMessage(), e);
            throw new BusinessException(ResultCode.EMAIL_SEND_FAILED);
        }
    }

    private String buildEmailContent(String verifyCode) {
        return "您好！\n\n"
                + "您正在注册/验证 CampusShare 校园共享平台账号，验证码为：\n\n"
                + "    " + verifyCode + "\n\n"
                + "验证码有效期为 5 分钟，请尽快使用。\n\n"
                + "如果这不是您本人的操作，请忽略此邮件。\n\n"
                + "———— CampusShare 校园共享团队";
    }
}
