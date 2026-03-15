package com.parking.mapper;

import com.parking.model.Owner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业主 Mapper 接口
 */
@Mapper
public interface OwnerMapper {

    /**
     * 插入业主记录
     *
     * @param owner 业主实体
     */
    void insert(Owner owner);

    /**
     * 根据ID查询业主
     *
     * @param id 业主ID
     * @return 业主实体
     */
    Owner selectById(@Param("id") Long id);

    /**
     * 根据手机号和小区ID查询业主
     *
     * @param phoneNumber 手机号
     * @param communityId 小区ID
     * @return 业主实体
     */
    Owner selectByPhoneAndCommunity(@Param("phoneNumber") String phoneNumber,
                                     @Param("communityId") Long communityId);

    /**
     * 更新业主账号状态
     *
     * @param id 业主ID
     * @param accountStatus 账号状态
     * @return 更新行数
     */
    int updateAccountStatus(@Param("id") Long id,
                            @Param("accountStatus") String accountStatus);

    /**
     * 更新业主手机号
     *
     * @param id          业主ID
     * @param phoneNumber 新手机号
     * @return 更新行数
     */
    int updatePhoneNumber(@Param("id") Long id,
                          @Param("phoneNumber") String phoneNumber);

    /**
     * 更新业主真实姓名
     *
     * @param id       业主ID
     * @param realName 新真实姓名
     * @return 更新行数
     */
    int updateRealName(@Param("id") Long id,
                       @Param("realName") String realName);

    /**
     * 根据ID列表批量查询业主（使用行级锁）
     *
     * @param ids 业主ID列表
     * @return 业主列表
     */
    List<Owner> selectByIdsForUpdate(@Param("ids") List<Long> ids);

    /**
     * 更新业主审核状态
     *
     * @param id           业主ID
     * @param status       新状态
     * @param rejectReason 驳回原因
     * @param auditAdminId 审核管理员ID
     * @return 更新行数
     */
    int updateAuditStatus(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("rejectReason") String rejectReason,
                          @Param("auditAdminId") Long auditAdminId);
}
