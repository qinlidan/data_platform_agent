package com.dataplatform.ai.confirmation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.entity.UsedConfirmationToken;
import com.dataplatform.mapper.UsedConfirmationTokenMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfirmationService {

    private final UsedConfirmationTokenMapper usedTokenMapper;
    private final SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_MS = 5 * 60 * 1000; // 5分钟

    public String generateToken(String toolName, String methodName, Map<String, Object> parameters) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tool", toolName);
        claims.put("method", methodName);
        claims.put("params", parameters);

        // 生成唯一 JTI（用于防重放）
        String jti = java.util.UUID.randomUUID().toString();
        claims.put("jti", jti);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token, String toolName, String methodName) {
        try {
            // 1. 验证 Token 签名并解析
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String jti = claims.get("jti", String.class);
            if (jti == null) {
                return false; // Token 无 JTI
            }

            // 2. 检查 Token 是否已被使用（防重放）
            LambdaQueryWrapper<UsedConfirmationToken> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UsedConfirmationToken::getJti, jti);
            if (usedTokenMapper.selectCount(wrapper) > 0) {
                return false; // Token 已使用
            }

            // 3. 验证工具名和方法名匹配
            boolean valid = toolName.equals(claims.get("tool")) && methodName.equals(claims.get("method"));

            // 4. 验证通过后，标记 Token 为已使用
            if (valid) {
                UsedConfirmationToken usedToken = new UsedConfirmationToken();
                usedToken.setJti(jti);
                usedToken.setToolName(toolName);
                usedToken.setToolParams(claims.get("params") != null ? claims.get("params").toString() : "{}");
                usedToken.setUsedAt(LocalDateTime.now());
                usedToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
                usedTokenMapper.insert(usedToken);
            }

            return valid;
        } catch (Exception e) {
            return false;
        }
    }
}
