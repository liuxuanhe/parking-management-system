package com.parking.service.impl;

import com.parking.common.PasswordValidator;
import com.parking.mapper.AdminMapper;
import com.parking.model.Admin;
import com.parking.service.AdminInitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 管理员账号初始化服务实现
 * 系统启动时自动检查并创建超级管理员账号
 * Validates: Requirements 13.1, 13.2, 13.3
 */
@Slf4j
@Service
public class AdminInitServiceImpl implements AdminInitService, ApplicationRunner {

    private final AdminMapper adminMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 随机密码字符集：大小写字母、数字、特殊字符 */
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL_CHARS;

    /** 随机密码长度 */
    private static final int PASSWORD_LENGTH = 16;

    public AdminInitServiceImpl(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(ApplicationArguments args) {
        initSuperAdmin();
    }

    @Override
    public void initSuperAdmin() {
        // 检查是否已存在超级管理员
        int count = adminMapper.countByRole("super_admin");
        if (count > 0) {
            log.info("超级管理员账号已存在，跳过初始化");
            return;
        }

        // 生成随机强密码
        String rawPassword = generateRandomPassword();

        // BCrypt 加密
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 创建超级管理员账号
        Admin admin = new Admin();
        admin.setCommunityId(0L); // 0 表示超级管理员，跨小区
        admin.setUsername("admin");
        admin.setPassword(encodedPassword);
        admin.setRealName("超级管理员");
        admin.setPhoneNumber("00000000000");
        admin.setRole("super_admin");
        admin.setStatus("active");
        admin.setLoginFailCount(0);
        admin.setMustChangePassword(1); // 首次登录强制修改密码
        admin.setPasswordExpireTime(LocalDateTime.now().plusDays(90));

        adminMapper.insert(admin);

        log.info("========================================");
        log.info("超级管理员账号初始化成功");
        log.info("用户名: admin");
        log.info("初始密码: {}", rawPassword);
        log.info("请在首次登录后立即修改密码！");
        log.info("========================================");
    }

    /**
     * 生成随机强密码（16位，包含大小写字母、数字、特殊字符）
     * 确保生成的密码一定满足 PasswordValidator 的强度要求
     *
     * @return 随机密码明文
     */
    public String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        // 确保至少包含每种字符各一个
        password.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(SPECIAL_CHARS.charAt(random.nextInt(SPECIAL_CHARS.length())));

        // 填充剩余字符
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password.append(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }

        // 打乱顺序，避免前4位固定为各类型字符
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        String result = new String(chars);

        // 二次校验密码强度（防御性编程）
        if (!PasswordValidator.isValid(result)) {
            // 理论上不会走到这里，但作为安全兜底递归重新生成
            return generateRandomPassword();
        }

        return result;
    }
}
