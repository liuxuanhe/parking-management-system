package com.parking.mapper;

import com.parking.model.ZombieVehicle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 僵尸车辆 Mapper 接口
 */
@Mapper
public interface ZombieVehicleMapper {

    /**
     * 插入僵尸车辆记录
     */
    void insert(@Param("zombie") ZombieVehicle zombie);

    /**
     * 根据入场记录ID查询是否已存在僵尸车辆记录
     */
    ZombieVehicle selectByEntryRecordId(@Param("entryRecordId") Long entryRecordId);

    /**
     * 按小区和状态查询僵尸车辆列表
     */
    List<ZombieVehicle> selectByCommunityAndStatus(@Param("communityId") Long communityId,
                                                    @Param("status") String status);

    /**
     * 根据ID查询僵尸车辆
     */
    ZombieVehicle selectById(@Param("id") Long id);

    /**
     * 更新僵尸车辆处理信息
     */
    void updateHandle(@Param("zombie") ZombieVehicle zombie);
}
