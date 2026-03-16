package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 * 使用 Redis 存储验证码和失败次数，支持：
 * - 6位随机数字验证码，5分钟有效
 * - 验证失败3次后锁定10分钟
 * - 验证成功后删除验证码和失败计数
 * Validates: Requirements 1.2, 1.3
 */
@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    /** 验证码 Redis 键前缀 */
    private static final String CODE_KEY_PREFIX = "verification_code:";

    /** 验证失败次数 Redis 键前缀 */
    private static final String FAIL_COUNT_KEY_PREFIX = "verification_fail_count:";

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    /** 验证码有效期（分钟） */
    private static final long CODE_EXPIRE_MINUTES = 5;

    /** 最大失败次数 */
    private static final int MAX_FAIL_COUNT = 3;

    /** 锁定时间（分钟） */
    private static final long LOCK_MINUTES = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom secureRandom;

    @org.springframework.beans.factory.annotation.Autowired
    public VerificationCodeServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 包级别构造函数，用于测试注入自定义 SecureRandom
     */
    VerificationCodeServiceImpl(RedisTemplate<String, Object> redisTemplate, SecureRandom secureRandom) {
        this.redisTemplate = redisTemplate;
        this.secureRandom = secureRandom;
    }

    @Override
    public void send(String phone) {
        // 生成6位随机数字验证码
        String code = generateCode();

        // 存入 Redis，5分钟过期
        String codeKey = CODE_KEY_PREFIX + phone;
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 预留短信发送接口，当前仅 log 记录
        log.info("验证码已发送: phone={}, code={}", phone, code);
    }

    @Override
    public boolean verify(String phone, String code) {
        String failCountKey = FAIL_COUNT_KEY_PREFIX + phone;
        String codeKey = CODE_KEY_PREFIX + phone;

        // 检查是否处于锁定状态（失败次数 >= 3 且锁定键未过期）
        Object failCountObj = redisTemplate.opsForValue().get(failCountKey);
        int failCount = parseFailCount(failCountObj);
        if (failCount >= MAX_FAIL_COUNT) {
            log.warn("验证码验证被锁定: phone={}, failCount={}", phone, failCount);
            throw new BusinessException(ErrorCode.PARKING_1001);
        }

        // 从 Redis 获取验证码
        Object storedCodeObj = redisTemplate.opsForValue().get(codeKey);
        if (storedCodeObj == null) {
            // 验证码不存在或已过期
            log.warn("验证码已过期或不存在: phone={}", phone);
            throw new BusinessException(ErrorCode.PARKING_1002);
        }

        String storedCode = storedCodeObj.toString();

        // 比对验证码
        if (storedCode.equals(code)) {
            // 验证成功，删除验证码和失败计数
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failCountKey);
            log.info("验证码验证成功: phone={}", phone);
            return true;
        }

        // 验证失败，增加失败次数
        Long newFailCount = redisTemplate.opsForValue().increment(failCountKey);
        // 设置锁定过期时间为10分钟
        redisTemplate.expire(failCountKey, LOCK_MINUTES, TimeUnit.MINUTES);
        log.warn("验证码验证失败: phone={}, failCount={}", phone, newFailCount);

        // 如果达到最大失败次数，抛出锁定异常
        if (newFailCount != null && newFailCount >= MAX_FAIL_COUNT) {
            throw new BusinessException(ErrorCode.PARKING_1001);
        }

        return false;
    }

    /**
     * 生成6位随机数字验证码
     *
     * @return 6位数字字符串
     */
    private String generateCode() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    /**
     * 解析失败次数
     *
     * @param failCountObj Redis 中存储的失败次数对象
     * @return 失败次数，如果为 null 则返回 0
     */
    private int parseFailCount(Object failCountObj) {
        if (failCountObj == null) {
            return 0;
        }
        if (failCountObj instanceof Number) {
            return ((Number) failCountObj).intValue();
        }
        try {
            return Integer.parseInt(failCountObj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
