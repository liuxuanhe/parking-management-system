package com.parking.common;

import java.util.regex.Pattern;

/**
 * 密码强度验证工具类
 * 验证规则：至少8位，包含大小写字母、数字、特殊字符
 * Validates: Requirements 13.2, 13.4
 */
public final class PasswordValidator {

    /** 最小密码长度 */
    private static final int MIN_LENGTH = 8;

    /** 大写字母正则 */
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");

    /** 小写字母正则 */
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");

    /** 数字正则 */
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");

    /** 特殊字符正则 */
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]");

    private PasswordValidator() {
        // 工具类禁止实例化
    }

    /**
     * 验证密码强度是否满足要求
     *
     * @param password 待验证的密码
     * @return true-满足强度要求，false-不满足
     */
    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        boolean hasUppercase = UPPERCASE_PATTERN.matcher(password).find();
        boolean hasLowercase = LOWERCASE_PATTERN.matcher(password).find();
        boolean hasDigit = DIGIT_PATTERN.matcher(password).find();
        boolean hasSpecialChar = SPECIAL_CHAR_PATTERN.matcher(password).find();

        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar;
    }
}
