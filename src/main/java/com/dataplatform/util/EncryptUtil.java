package com.dataplatform.util;

import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 加密工具类
 */
@Slf4j
@Component
public class EncryptUtil {

    @Value("${jwt.secret:dataplatform-secret-key}")
    private String aesKey;

    /**
     * AES加密
     */
    public String encrypt(String data) {
        try {
            if (data == null || data.isEmpty()) {
                return data;
            }
            byte[] key = aesKey.substring(0, 16).getBytes(StandardCharsets.UTF_8);
            SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, key);
            return aes.encryptHex(data);
        } catch (Exception e) {
            log.error("加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * AES解密
     */
    public String decrypt(String encryptedData) {
        try {
            if (encryptedData == null || encryptedData.isEmpty()) {
                return encryptedData;
            }
            byte[] key = aesKey.substring(0, 16).getBytes(StandardCharsets.UTF_8);
            SymmetricCrypto aes = new SymmetricCrypto(SymmetricAlgorithm.AES, key);
            return aes.decryptStr(encryptedData);
        } catch (Exception e) {
            log.error("解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据解密失败", e);
        }
    }
}
