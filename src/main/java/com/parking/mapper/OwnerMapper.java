package com.parking.mapper;

import com.parking.model.Owner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
