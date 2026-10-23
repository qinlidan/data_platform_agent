package com.dataplatform.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private Long expiration;  // 毫秒

    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret 必须至少 32 字符（256 位）");
        }
    }
}
