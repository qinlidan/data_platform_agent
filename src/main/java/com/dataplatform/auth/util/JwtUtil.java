package com.dataplatform.auth.util;

import com.dataplatform.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email, LocalDateTime lastVerifiedAt) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .claim("lastVerifiedAt", lastVerifiedAt != null ?
                    Date.from(lastVerifiedAt.atZone(ZoneId.systemDefault()).toInstant()).getTime() : null)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSignKey())
                .compact();
    }

    // 为游客登录提供的重载方法（userId 为字符串）
    public String generateToken(String userId, String email, LocalDateTime lastVerifiedAt) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .claim("lastVerifiedAt", lastVerifiedAt != null ?
                    Date.from(lastVerifiedAt.atZone(ZoneId.systemDefault()).toInstant()).getTime() : null)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSignKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.warn("提取 userId 失败: {}", e.getMessage());
            return null;
        }
    }

    public String getEmailFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("email", String.class);
        } catch (Exception e) {
            log.warn("提取 email 失败: {}", e.getMessage());
            return null;
        }
    }

    public LocalDateTime getLastVerifiedAtFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long timestamp = claims.get("lastVerifiedAt", Long.class);
            return timestamp != null ?
                LocalDateTime.ofInstant(new Date(timestamp).toInstant(), ZoneId.systemDefault()) : null;
        } catch (Exception e) {
            log.warn("提取 lastVerifiedAt 失败: {}", e.getMessage());
            return null;
        }
    }
}
