package com.parking.common;

import lombok.Getter;

/**
 * 错误码枚举，格式为 PARKING_XXXX
 * Validates: Requirements 26.3, 26.5
 */
@Getter
public enum ErrorCode {

    // 1xxx: 认证与注册相关
    PARKING_1001(1001, "验证码错误次数过多，请10分钟后重试"),
    PARKING_1002(1002, "验证码已过期，请重新获取"),

    // 2xxx: 审核相关
    PARKING_2001(2001, "该申请已被审核，无法重复操作"),

    // 3xxx: 车牌管理相关
    PARKING_3001(3001, "车牌数量已达上限（5个），无法继续添加"),
    PARKING_3002(3002, "该车辆当前在场，无法删除"),

    // 4xxx: Primary 车辆相关
    PARKING_4001(4001, "房屋号下有车辆在场，无法切换 Primary 车辆"),
    PARKING_4002(4002, "原 Primary 车辆有未完成入场申请，无法切换"),

    // 5xxx: 入场出场相关
    PARKING_5001(5001, "车位已满，无法入场"),

    // 7xxx: Visitor 权限相关
    PARKING_7001(7001, "本月 Visitor 时长配额已用完（72小时），无法申请"),

    // 9xxx: 车位配置相关
    PARKING_9001(9001, "Visitor 可开放车位不足，无法申请"),
    PARKING_9002(9002, "新车位数小于当前在场车辆数，无法修改"),

    // 12xxx: 权限与数据隔离相关
    PARKING_12001(12001, "无权访问该小区数据"),

    // 14xxx: 账号管理相关
    PARKING_14001(14001, "业主有车辆在场，无法注销账号"),

    // 17xxx: 数据脱敏与导出相关
    PARKING_17001(17001, "IP 不在白名单内，无法导出原始数据"),

    // 19xxx: 安全防护相关
    PARKING_19001(19001, "请求时间戳超出有效窗口"),
    PARKING_19002(19002, "Nonce 已被使用"),
    PARKING_19003(19003, "签名验证失败"),

    // 20xxx: 高危操作相关
    PARKING_20001(20001, "IP 不在白名单内，无法执行高危操作"),

    // 13xxx: 认证相关
    PARKING_13001(13001, "Token 已过期"),
    PARKING_13002(13002, "Token 无效"),
    PARKING_13003(13003, "Token 已被撤销"),

    // 锁相关
    LOCK_ACQUIRE_FAILED(10010, "获取分布式锁失败，请稍后重试"),

    // 通用错误
    SYSTEM_ERROR(99999, "系统内部错误"),
    PARAM_ERROR(10000, "参数校验失败"),
    UNAUTHORIZED(10001, "未授权访问"),
    FORBIDDEN(10003, "权限不足");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
