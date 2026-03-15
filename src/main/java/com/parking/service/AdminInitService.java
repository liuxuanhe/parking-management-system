package com.parking.service;

/**
 * 管理员账号初始化服务接口
 * Validates: Requirements 13.1, 13.2, 13.3
 */
public interface AdminInitService {

    /**
     * 初始化超级管理员账号
     * 系统首次启动时调用，检查是否已存在 super_admin，
     * 若不存在则生成随机密码并创建超级管理员账号
     */
    void initSuperAdmin();
}
