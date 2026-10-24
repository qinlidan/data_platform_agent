package com.dataplatform.ai.confirmation;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ConfirmationService {

    private final SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_MS = 5 * 60 * 1000; // 5分钟

    public String generateToken(String toolName, String methodName, Map<String, Object> parameters) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tool", toolName);
        claims.put("method", methodName);
        claims.put("params", parameters);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token, String toolName, String methodName) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return toolName.equals(claims.get("tool")) && methodName.equals(claims.get("method"));
        } catch (Exception e) {
            return false;
        }
    }
}
