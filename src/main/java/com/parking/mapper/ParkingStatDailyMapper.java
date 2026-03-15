package com.parking.mapper;

import com.parking.model.ParkingStatDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 每日统计预聚合 Mapper 接口
 */
@Mapper
public interface ParkingStatDailyMapper {

    /**
     * 增量更新入场计数
     * 如果当天记录不存在则插入，存在则累加
     */
    int incrementEntryCount(@Param("communityId") Long communityId,
                            @Param("statDate") LocalDate statDate,
                            @Param("vehicleType") String vehicleType);

    /**
     * 增量更新出场计数
     */
    int incrementExitCount(@Param("communityId") Long communityId,
                           @Param("statDate") LocalDate statDate);

    /**
     * 根据小区ID和日期查询统计记录
     */
    ParkingStatDaily selectByDate(@Param("communityId") Long communityId,
                                  @Param("statDate") LocalDate statDate);

    /**
     * 回补/覆盖写入每日统计数据（upsert）
     * 如果当天记录不存在则插入，存在则全量覆盖
     */
    int upsertDailyStat(ParkingStatDaily stat);

    /**
     * 按日期范围查询每日统计数据
     */
    java.util.List<ParkingStatDaily> selectByDateRange(@Param("communityId") Long communityId,
                                                        @Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate);
}
