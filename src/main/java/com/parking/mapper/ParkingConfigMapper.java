package com.parking.mapper;

import com.parking.model.ParkingConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 停车场配置 Mapper 接口
 * Validates: Requirements 9.5, 9.6, 9.7, 9.8
 */
@Mapper
public interface ParkingConfigMapper {

    /**
     * 根据小区ID查询停车场配置
     *
     * @param communityId 小区ID
     * @return 停车场配置，不存在则返回 null
     */
    ParkingConfig selectByCommunityId(@Param("communityId") Long communityId);

    /**
     * 使用乐观锁更新停车场配置
     * 仅当 version 匹配时才更新，version 自动 +1
     *
     * @param config 停车场配置（包含新值和当前 version）
     * @return 更新行数，0 表示乐观锁冲突
     */
    int updateByOptimisticLock(ParkingConfig config);

    /**
     * 统计指定小区当前在场车辆数
     * 查询 parking_car_record 分表中 status='entered' 的记录数
     * 简化实现：当前查询 sys_car_plate 中 status IN ('normal','primary') 且未删除的记录
     * 后续入场模块完善后改为查询分表
     *
     * @param communityId 小区ID
     * @return 当前在场车辆数
     */
    int countEnteredVehicles(@Param("communityId") Long communityId);

    /**
     * 查询所有小区的 community_id 列表
     *
     * @return 所有小区 ID
     */
    java.util.List<Long> selectAllCommunityIds();
}
