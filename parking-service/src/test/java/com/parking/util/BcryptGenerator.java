package com.parking.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码哈希生成工具
 * 运行此类的 main 方法生成密码哈希
 */
public class BcryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "Admin@123";
        String hash = encoder.encode(password);
        System.out.println("密码: " + password);
        System.out.println("BCrypt 哈希: " + hash);
        System.out.println("验证: " + encoder.matches(password, hash));
    }
}
