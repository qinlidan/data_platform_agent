package com.dataplatform.auth.service;

import com.dataplatform.auth.enums.CodeType;
import com.dataplatform.auth.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationCode(String email, String code, CodeType type) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("${MAIL_USERNAME:17766238993@163.com}");
            message.setTo(email);
            message.setSubject(getSubject(type));
            message.setText(getContent(code, type));

            mailSender.send(message);
            log.info("验证码邮件发送成功: email={}, type={}", email, type);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: email={}, type={}", email, type, e);
            throw new BusinessException("验证码发送失败，请稍后重试", e);
        }
    }

    private String getSubject(CodeType type) {
        return switch (type) {
            case REGISTER -> "【数据平台】注册验证码";
            case LOGIN -> "【数据平台】登录验证码";
            case RESET_PASSWORD -> "【数据平台】重置密码验证码";
        };
    }

    private String getContent(String code, CodeType type) {
        String action = switch (type) {
            case REGISTER -> "注册数据平台账号";
            case LOGIN -> "登录数据平台";
            case RESET_PASSWORD -> "重置数据平台密码";
        };

        return String.format("""
                您好！

                您正在%s，验证码为：

                    %s

                验证码10分钟内有效，请勿泄露给他人。

                如非本人操作，请忽略此邮件。
                """, action, code);
    }
}
