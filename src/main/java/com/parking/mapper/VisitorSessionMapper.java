package com.parking.mapper;

import com.parking.model.VisitorSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Visitor 会话 Mapper 接口
 */
@Mapper
public interface VisitorSessionMapper {

    /**
     * 查询指定房屋号在指定月份的累计时长（分钟）
     */
    Long sumMonthlyDuration(@Param("communityId") Long communityId,
                            @Param("houseNo") String houseNo,
                            @Param("year") int year,
                            @Param("month") int month);

    /**
     * 根据ID查询会话
     */
    VisitorSession selectById(@Param("id") Long id);

    /**
     * 更新会话累计时长和状态
     */
    int updateDurationAndStatus(@Param("id") Long id,
                                @Param("accumulatedDuration") int accumulatedDuration,
                                @Param("status") String status);

    /**
     * 查询超时会话（累计时长 ≥ 1440分钟且状态为 in_park，且未通知过）
     */
    List<VisitorSession> selectTimeoutSessions();

    /**
     * 插入会话记录
     */
    int insert(VisitorSession session);

    /**
     * 更新会话状态和最后入场时间
     */
    int updateStatusAndEntryTime(@Param("id") Long id,
                                 @Param("status") String status,
                                 @Param("lastEntryTime") java.time.LocalDateTime lastEntryTime);

    /**
     * 根据授权ID查询会话
     */
    VisitorSession selectByAuthorizationId(@Param("authorizationId") Long authorizationId);

    /**
     * 根据车牌号查询活跃会话（status='in_park'）
     */
    VisitorSession selectActiveByCarNumber(@Param("communityId") Long communityId,
                                           @Param("carNumber") String carNumber);

    /**
     * 根据车牌号查询已出场会话（status='out_of_park'），用于再次入场
     */
    VisitorSession selectOutOfParkByCarNumber(@Param("communityId") Long communityId,
                                              @Param("carNumber") String carNumber);

    /**
     * 标记超时已通知
     */
    int updateTimeoutNotified(@Param("id") Long id, @Param("timeoutNotified") int timeoutNotified);
}
