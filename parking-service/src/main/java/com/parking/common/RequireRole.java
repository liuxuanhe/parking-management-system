package com.parking.common;

import java.lang.annotation.*;

/**
 * 角色校验注解
 * 标注在 Controller 方法上，指定允许访问的角色列表
 * 校验失败抛出 BusinessException(ErrorCode.PARKING_12001)
 * Validates: Requirements 12.3, 14.1, 20.4
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许的角色列表，如 {"super_admin"} 或 {"super_admin", "property_admin"} */
    String[] value();
}
