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
                Object paramsObj = claims.get("params");
                if (paramsObj instanceof Map) {
                    usedToken.setToolParams((Map<String, Object>) paramsObj);
                } else if (paramsObj != null) {
                    usedToken.setToolParams(Map.of("raw", paramsObj.toString()));
                } else {
                    usedToken.setToolParams(Map.of());
                }
                usedToken.setUsedAt(LocalDateTime.now());
                usedToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
                usedTokenMapper.insert(usedToken);
            }

            return valid;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 中提取原始工具调用参数（不验证、不标记已使用）
     *
     * @param token 确认令牌
     * @return 原始参数 Map，解析失败返回 null
     */
    public Map<String, Object> extractParams(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object paramsObj = claims.get("params");
            if (paramsObj instanceof Map) {
                return (Map<String, Object>) paramsObj;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 token 并返回原始参数（验证通过后标记已使用）
     *
     * @param token 确认令牌
     * @param toolName 工具名
     * @param methodName 方法名
     * @return 验证通过返回原始参数，否则返回 null
     */
    public Map<String, Object> validateAndGetParams(String token, String toolName, String methodName) {
        if (!validateToken(token, toolName, methodName)) {
            return null;
        }
        return extractParams(token);
    }
}
