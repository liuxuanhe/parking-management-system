package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 签名验证服务实现类
 * 使用 SHA256 算法对请求进行签名和验证，防止请求被篡改
 * 签名算法: SHA256(timestamp + nonce + requestBody + secretKey)
 * Validates: Requirements 19.9, 19.10
 */
@Slf4j
@Service
public class SignatureServiceImpl implements SignatureService {

    @Value("${signature.secret-key}")
    private String secretKey;

    /**
     * 允许通过构造函数注入 secretKey，便于测试
     */
    public SignatureServiceImpl() {
    }

    @Override
    public String generateSignature(String timestamp, String nonce, String requestBody) {
        // 拼接待签名字符串: timestamp + nonce + requestBody + secretKey
        String data = (timestamp == null ? "" : timestamp)
                + (nonce == null ? "" : nonce)
                + (requestBody == null ? "" : requestBody)
                + secretKey;
        return sha256Hex(data);
    }

    @Override
    public void verifySignature(String timestamp, String nonce, String requestBody, String signature) {
        // 重新计算签名
        String expectedSignature = generateSignature(timestamp, nonce, requestBody);

        // 使用常量时间比较防止时序攻击
        if (!constantTimeEquals(expectedSignature, signature)) {
            log.warn("签名验证失败, timestamp: {}, nonce: {}", timestamp, nonce);
            throw new BusinessException(ErrorCode.PARKING_19003);
        }

        log.debug("签名验证通过, timestamp: {}, nonce: {}", timestamp, nonce);
    }

    /**
     * 计算 SHA256 摘要并返回十六进制小写字符串
     */
    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 内置算法，理论上不会抛出此异常
            log.error("SHA-256 算法不可用", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 字节数组转十六进制小写字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 常量时间字符串比较，防止时序攻击
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
