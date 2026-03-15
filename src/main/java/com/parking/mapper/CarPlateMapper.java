package com.parking.mapper;

import com.parking.model.CarPlate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 车牌 Mapper 接口
 * Validates: Requirements 3.1, 3.3, 14.2, 14.5
 */
@Mapper
public interface CarPlateMapper {

    /**
     * 统计指定 Data_Domain 下在场车辆数（简化实现：统计 status 为 normal 或 primary 的车牌数）
     * 后续入场模块会通过 parking_car_record 分表中 status='entered' 来判断
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @return 在场车辆数
     */
    int countEnteredByOwnerHouse(@Param("communityId") Long communityId,
                                 @Param("houseNo") String houseNo);

    /**
     * 批量禁用指定 Data_Domain 下所有车牌
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @return 更新行数
     */
    int disableByOwnerHouse(@Param("communityId") Long communityId,
                            @Param("houseNo") String houseNo);

    /**
     * 统计业主在指定 Data_Domain 下的有效车牌数量
     * Validates: Requirements 3.1, 3.5
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @param ownerId 业主ID
     * @return 有效车牌数量
     */
    int countByOwner(@Param("communityId") Long communityId,
                     @Param("houseNo") String houseNo,
                     @Param("ownerId") Long ownerId);

    /**
     * 查询车牌在指定小区内是否已被其他业主绑定
     * Validates: Requirements 3.3
     *
     * @param communityId 小区ID
     * @param carNumber 车牌号
     * @param ownerId 当前业主ID（排除自身）
     * @return 绑定数量，大于0表示已被其他业主绑定
     */
    int countByCarNumberInCommunity(@Param("communityId") Long communityId,
                                    @Param("carNumber") String carNumber,
                                    @Param("ownerId") Long ownerId);

    /**
     * 插入车牌记录
     *
     * @param carPlate 车牌实体
     */
    void insert(CarPlate carPlate);

    /**
     * 根据 ID 查询车牌记录（未删除的）
     * Validates: Requirements 3.6
     *
     * @param id 车牌记录ID
     * @return 车牌实体，不存在则返回 null
     */
    CarPlate selectById(@Param("id") Long id);

    /**
     * 逻辑删除车牌记录（设置 is_deleted = 1）
     * Validates: Requirements 3.8
     *
     * @param id 车牌记录ID
     * @return 更新行数
     */
    int logicalDelete(@Param("id") Long id);

    /**
     * 查询指定 Data_Domain（community_id + house_no）下所有未删除车牌
     * 支持同房屋号多业主场景，返回该房屋号下所有业主的车牌
     * Validates: Requirements 11.1, 11.5
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @return 车牌列表
     */
    List<CarPlate> selectByHouse(@Param("communityId") Long communityId,
                                 @Param("houseNo") String houseNo);

    /**
     * 使用行级锁查询指定 Data_Domain 下所有未删除车牌（SELECT ... FOR UPDATE）
     * 用于 Primary 车辆切换时防止并发冲突
     * Validates: Requirements 4.10
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @return 车牌列表（已加行级锁）
     */
    List<CarPlate> selectForUpdate(@Param("communityId") Long communityId,
                                   @Param("houseNo") String houseNo);

    /**
     * 将指定 Data_Domain 下 status='primary' 的车牌更新为 normal
     * Validates: Requirements 4.8
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @return 更新行数
     */
    int updatePrimaryToNormal(@Param("communityId") Long communityId,
                              @Param("houseNo") String houseNo);

    /**
     * 将指定车牌的 status 更新为 primary
     * Validates: Requirements 4.8
     *
     * @param id 车牌记录ID
     * @return 更新行数
     */
    int updateStatusToPrimary(@Param("id") Long id);
}
