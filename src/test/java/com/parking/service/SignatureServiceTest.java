package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.service.impl.SignatureServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SignatureService 单元测试
 * Validates: Requirements 19.9, 19.10
 */
class SignatureServiceTest {

    private static final String SECRET_KEY = "c2lnbmF0dXJlLXNlY3JldC1rZXktZm9yLXBhcmtpbmctc3lzdGVtLTIwMjQ=";

    private SignatureServiceImpl signatureService;

    @BeforeEach
    void setUp() {
        signatureService = new SignatureServiceImpl();
        ReflectionTestUtils.setField(signatureService, "secretKey", SECRET_KEY);
    }

    @Test
    @DisplayName("生成签名应返回非空的十六进制字符串")
    void generateSignature_shouldReturnNonEmptyHexString() {
        String signature = signatureService.generateSignature("1705305600000", "abc123", "{\"key\":\"value\"}");

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // SHA256 输出为64个十六进制字符
        assertEquals(64, signature.length());
        // 验证是合法的十六进制字符串
        assertTrue(signature.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("相同输入应生成相同签名（确定性）")
    void generateSignature_sameInput_shouldReturnSameResult() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String body = "{\"communityId\":1001}";

        String sig1 = signatureService.generateSignature(timestamp, nonce, body);
        String sig2 = signatureService.generateSignature(timestamp, nonce, body);

        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("不同输入应生成不同签名")
    void generateSignature_differentInput_shouldReturnDifferentResult() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";

        String sig1 = signatureService.generateSignature(timestamp, nonce, "{\"key\":\"value1\"}");
        String sig2 = signatureService.generateSignature(timestamp, nonce, "{\"key\":\"value2\"}");

        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("签名算法应为 SHA256(timestamp + nonce + requestBody + secretKey)")
    void generateSignature_shouldFollowDesignedAlgorithm() throws Exception {
        String timestamp = "1705305600000";
        String nonce = "abc123";
        String body = "{\"test\":true}";

        // 手动计算期望的签名
        String data = timestamp + nonce + body + SECRET_KEY;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder expected = new StringBuilder();
        for (byte b : hash) {
            expected.append(String.format("%02x", b));
        }

        String actual = signatureService.generateSignature(timestamp, nonce, body);
        assertEquals(expected.toString(), actual);
    }

    @Test
    @DisplayName("验证正确签名应通过")
    void verifySignature_validSignature_shouldPass() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String body = "{\"communityId\":1001}";

        String signature = signatureService.generateSignature(timestamp, nonce, body);

        assertDoesNotThrow(() -> signatureService.verifySignature(timestamp, nonce, body, signature));
    }

    @Test
    @DisplayName("验证错误签名应抛出 BusinessException（PARKING_19003）")
    void verifySignature_invalidSignature_shouldThrowException() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String body = "{\"communityId\":1001}";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> signatureService.verifySignature(timestamp, nonce, body, "invalid_signature"));
        assertEquals(19003, ex.getCode());
    }

    @Test
    @DisplayName("请求体被篡改后签名验证应失败")
    void verifySignature_tamperedBody_shouldFail() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String originalBody = "{\"communityId\":1001}";
        String tamperedBody = "{\"communityId\":9999}";

        String signature = signatureService.generateSignature(timestamp, nonce, originalBody);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> signatureService.verifySignature(timestamp, nonce, tamperedBody, signature));
        assertEquals(19003, ex.getCode());
    }

    @Test
    @DisplayName("timestamp 被篡改后签名验证应失败")
    void verifySignature_tamperedTimestamp_shouldFail() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String body = "{\"communityId\":1001}";

        String signature = signatureService.generateSignature(timestamp, nonce, body);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> signatureService.verifySignature("1705305699999", nonce, body, signature));
        assertEquals(19003, ex.getCode());
    }

    @Test
    @DisplayName("nonce 被篡改后签名验证应失败")
    void verifySignature_tamperedNonce_shouldFail() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String body = "{\"communityId\":1001}";

        String signature = signatureService.generateSignature(timestamp, nonce, body);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> signatureService.verifySignature(timestamp, "tampered_nonce", body, signature));
        assertEquals(19003, ex.getCode());
    }

    @Test
    @DisplayName("签名为 null 时验证应失败")
    void verifySignature_nullSignature_shouldFail() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> signatureService.verifySignature("1705305600000", "abc123", "{}", null));
        assertEquals(19003, ex.getCode());
    }

    @Test
    @DisplayName("请求体为空字符串时应正常生成和验证签名")
    void verifySignature_emptyBody_shouldWork() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";
        String body = "";

        String signature = signatureService.generateSignature(timestamp, nonce, body);
        assertDoesNotThrow(() -> signatureService.verifySignature(timestamp, nonce, body, signature));
    }

    @Test
    @DisplayName("请求体为 null 时应正常生成和验证签名")
    void verifySignature_nullBody_shouldWork() {
        String timestamp = "1705305600000";
        String nonce = "abc123def456";

        String signature = signatureService.generateSignature(timestamp, nonce, null);
        assertDoesNotThrow(() -> signatureService.verifySignature(timestamp, nonce, null, signature));
    }

    @Test
    @DisplayName("不同 secretKey 生成的签名应不同")
    void generateSignature_differentSecretKey_shouldDiffer() {
        String timestamp = "1705305600000";
        String nonce = "abc123";
        String body = "{}";

        String sig1 = signatureService.generateSignature(timestamp, nonce, body);

        // 使用不同的 secretKey
        SignatureServiceImpl anotherService = new SignatureServiceImpl();
        ReflectionTestUtils.setField(anotherService, "secretKey", "another-secret-key-for-testing");
        String sig2 = anotherService.generateSignature(timestamp, nonce, body);

        assertNotEquals(sig1, sig2);
    }
}
