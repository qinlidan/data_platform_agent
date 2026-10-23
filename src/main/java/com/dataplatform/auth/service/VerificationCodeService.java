package com.dataplatform.auth.service;

import com.dataplatform.auth.enums.CodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final long SEND_INTERVAL_SECONDS = 60;

    /**
     * 生成 6 位随机验证码
     * @return 6 位数字验证码
     */
    public String generateCode() {
        return String.format("%0" + CODE_LENGTH + "d", SECURE_RANDOM.nextInt((int) Math.pow(10, CODE_LENGTH)));
    }

    /**
     * 保存验证码到 Redis
     * @param email 邮箱地址
     * @param code 验证码
     * @param type 验证码类型
     */
    public void saveCode(String email, String code, CodeType type) {
        String key = getCodeKey(email, type);
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.info("验证码已保存: email={}, type={}", email, type);
    }

    /**
     * 验证码验证
     * @param email 邮箱地址
     * @param code 验证码
     * @param type 验证码类型
     * @return 验证是否成功
     */
    public boolean validateCode(String email, String code, CodeType type) {
        String key = getCodeKey(email, type);
        String savedCode = redisTemplate.opsForValue().get(key);

        if (savedCode == null) {
            log.warn("验证码不存在或已过期: email={}, type={}", email, type);
            return false;
        }

        if (!savedCode.equals(code)) {
            log.warn("验证码错误: email={}, type={}", email, type);
            return false;
        }

        // 验证成功后删除验证码
        redisTemplate.delete(key);
        log.info("验证码验证成功: email={}, type={}", email, type);
        return true;
    }

    /**
     * 检查是否可以发送验证码（防重发）
     * @param email 邮箱地址
     * @return true 可以发送，false 不可以发送
     * @deprecated 使用 {@link #tryRecordSendTime(String)} 替代，以避免 race condition
     */
    @Deprecated
    public boolean canSendCode(String email) {
        String key = getSendLimitKey(email);
        return Boolean.FALSE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 尝试记录验证码发送时间（原子操作）
     * 使用 Redis setIfAbsent 确保并发安全性，避免 race condition
     * @param email 邮箱地址
     * @return true 记录成功（可以发送），false 记录失败（不可以发送，已在限制期内）
     */
    public boolean tryRecordSendTime(String email) {
        String key = getSendLimitKey(email);
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 记录验证码发送时间
     * @param email 邮箱地址
     * @deprecated 使用 {@link #tryRecordSendTime(String)} 替代，以避免 race condition
     */
    @Deprecated
    public void recordSendTime(String email) {
        String key = getSendLimitKey(email);
        redisTemplate.opsForValue().set(key, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 获取验证码的 Redis key
     */
    private String getCodeKey(String email, CodeType type) {
        return String.format("email_code:%s:%s", type, email);
    }

    /**
     * 获取发送限制的 Redis key
     */
    private String getSendLimitKey(String email) {
        return String.format("email_code_limit:%s", email);
    }
}
