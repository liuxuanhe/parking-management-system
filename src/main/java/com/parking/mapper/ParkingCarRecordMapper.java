package com.parking.mapper;

import com.parking.model.ParkingCarRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入场记录 Mapper 接口
 * 支持按月分表路由，表名格式: parking_car_record_yyyymm
 * Validates: Requirements 15.2
 */
@Mapper
public interface ParkingCarRecordMapper {

    /**
     * 插入入场记录到指定月份分表
     *
     * @param tableName 分表名称（如 parking_car_record_202501）
     * @param record    入场记录实体
     */
    void insertToTable(@Param("tableName") String tableName,
                       @Param("record") ParkingCarRecord record);

    /**
     * 统计指定小区当前在场车辆数（跨分表查询当前月份）
     *
     * @param tableName   分表名称
     * @param communityId 小区ID
     * @return 在场车辆数
     */
    int countEnteredByTable(@Param("tableName") String tableName,
                            @Param("communityId") Long communityId);

    /**
     * 查找指定小区和车牌的在场记录（status='entered'）
     *
     * @param tableName   分表名称
     * @param communityId 小区ID
     * @param carNumber   车牌号
     * @return 在场记录，不存在则返回 null
     */
    ParkingCarRecord selectEnteredRecord(@Param("tableName") String tableName,
                                         @Param("communityId") Long communityId,
                                         @Param("carNumber") String carNumber);

    /**
     * 更新入场记录为出场状态
     *
     * @param tableName 分表名称
     * @param record    包含 id、exitTime、duration、status 的记录
     */
    void updateExitRecord(@Param("tableName") String tableName,
                          @Param("record") ParkingCarRecord record);

    /**
     * 根据ID查询异常出场记录
     *
     * @param tableName   分表名称
     * @param id          记录ID
     * @param communityId 小区ID
     * @return 异常出场记录，不存在则返回 null
     */
    ParkingCarRecord selectById(@Param("tableName") String tableName,
                                @Param("id") Long id,
                                @Param("communityId") Long communityId);

    /**
     * 更新异常出场记录的处理信息
     *
     * @param tableName 分表名称
     * @param record    包含 id、handlerAdminId、handleTime、handleRemark、status 的记录
     */
    void updateExceptionHandle(@Param("tableName") String tableName,
                               @Param("record") ParkingCarRecord record);

    /**
     * 跨月分表查询入场记录（UNION ALL + 游标分页）
     * 根据时间范围涉及的分表列表，使用 UNION ALL 合并查询结果，
     * 基于 (enter_time, id) 游标分页
     *
     * @param tableNames  涉及的分表名称列表
     * @param communityId 小区ID
     * @param houseNo     房屋号
     * @param startTime   查询开始时间
     * @param endTime     查询结束时间
     * @param cursorTime  游标时间（可选）
     * @param cursorId    游标ID（可选）
     * @param limit       查询条数
     * @return 入场记录列表，按 enter_time DESC, id DESC 排序
     */
    List<ParkingCarRecord> selectRecordsByUnionAll(
            @Param("tableNames") List<String> tableNames,
            @Param("communityId") Long communityId,
            @Param("houseNo") String houseNo,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );
}
