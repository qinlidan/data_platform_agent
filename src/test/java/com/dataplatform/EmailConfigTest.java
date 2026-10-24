package com.dataplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 邮件配置测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class EmailConfigTest {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Test
    public void testMailSenderBean() {
        assertNotNull(mailSender, "JavaMailSender bean should be configured");
    }
}
