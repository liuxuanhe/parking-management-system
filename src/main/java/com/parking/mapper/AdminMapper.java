package com.parking.mapper;

import com.parking.model.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员 Mapper 接口
 */
@Mapper
public interface AdminMapper {

    /**
     * 插入管理员记录
     *
     * @param admin 管理员实体
     */
    void insert(Admin admin);

    /**
     * 根据用户名查询管理员
     *
     * @param username 用户名
     * @return 管理员实体
     */
    Admin selectByUsername(@Param("username") String username);

    /**
     * 统计指定角色的管理员数量
     *
     * @param role 角色
     * @return 数量
     */
    int countByRole(@Param("role") String role);

    /**
     * 更新管理员密码
     *
     * @param id                 管理员ID
     * @param password           新密码（BCrypt 加密后）
     * @param mustChangePassword 是否必须修改密码：0-否，1-是
     */
    void updatePassword(@Param("id") Long id,
                        @Param("password") String password,
                        @Param("mustChangePassword") Integer mustChangePassword);
}
