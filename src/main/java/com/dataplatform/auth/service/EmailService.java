package com.dataplatform.auth.service;

import com.dataplatform.auth.enums.CodeType;
import com.dataplatform.auth.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailUsername;

    public void sendVerificationCode(String email, String code, CodeType type) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername);
            helper.setTo(email);
            helper.setSubject(getSubject(type));
            helper.setText(getContent(code, type), true);  // true: HTML 格式

            mailSender.send(message);
            log.info("验证码邮件发送成功: email={}, type={}", email, type);
        } catch (MessagingException e) {
            log.error("验证码邮件发送失败: email={}, type={}", email, type, e);
            throw new BusinessException("验证码发送失败，请稍后重试", e);
        } catch (Exception e) {
            log.error("验证码邮件发送异常: email={}, type={}", email, type, e);
            throw new BusinessException("验证码发送失败，请稍后重试", e);
        }
    }

    private String getSubject(CodeType type) {
        return switch (type) {
            case REGISTER -> "【数据平台】注册验证码";
            case LOGIN -> "【数据平台】登录验证码";
        };
    }

    private String getContent(String code, CodeType type) {
        String action = switch (type) {
            case REGISTER -> "注册数据平台账号";
            case LOGIN -> "登录数据平台";
        };

        return String.format("""
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: "Microsoft YaHei", Arial, sans-serif; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f5f5; }
                        .email-box { background: white; padding: 30px; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
                        .code-box { background: #f0f0f0; padding: 15px; text-align: center; border-radius: 5px; margin: 20px 0; }
                        .code { font-size: 32px; font-weight: bold; color: #0066cc; letter-spacing: 5px; }
                        .footer { margin-top: 20px; font-size: 12px; color: #999; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="email-box">
                            <p>您好！</p>
                            <p>您正在%s，请使用以下验证码继续操作：</p>
                            <div class="code-box">
                                <div class="code">%s</div>
                            </div>
                            <p style="color: #666; font-size: 14px;">验证码10分钟内有效，请勿泄露给他人。</p>
                            <p style="color: #666; font-size: 14px;">如非本人操作，请忽略此邮件。</p>
                            <div class="footer">
                                <p>数据开发平台 | 自动发送邮件，请勿直接回复</p>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """, action, code);
    }
}

