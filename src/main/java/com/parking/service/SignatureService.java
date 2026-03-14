package com.parking.service;

/**
 * 签名验证服务接口
 * 负责请求签名的生成与验证，防止请求被篡改
 * Validates: Requirements 19.9, 19.10
 */
public interface SignatureService {

    /**
     * 生成签名
     * 算法: SHA256(timestamp + nonce + requestBody + secretKey)
     *
     * @param timestamp   请求时间戳
     * @param nonce       防重放随机数
     * @param requestBody 请求体内容
     * @return 签名字符串（十六进制小写）
     */
    String generateSignature(String timestamp, String nonce, String requestBody);

    /**
     * 验证签名
     * 重新计算签名并与请求中的 signature 比对
     * 验证失败时抛出 BusinessException（PARKING_19003）
     *
     * @param timestamp   请求时间戳
     * @param nonce       防重放随机数
     * @param requestBody 请求体内容
     * @param signature   请求中携带的签名
     */
    void verifySignature(String timestamp, String nonce, String requestBody, String signature);
}
