package com.parking.mapper;

import com.parking.model.VisitorAuthorization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Visitor 授权 Mapper 接口
 */
@Mapper
public interface VisitorAuthorizationMapper {

    /**
     * 插入授权记录
     */
    int insert(VisitorAuthorization authorization);

    /**
     * 根据ID查询授权
     */
    VisitorAuthorization selectById(@Param("id") Long id);

    /**
     * 根据车牌号查询待激活的授权
     */
    VisitorAuthorization selectPendingActivation(@Param("communityId") Long communityId,
                                                  @Param("carNumber") String carNumber);

    /**
     * 更新授权状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 查询已审批但超过激活窗口的授权（用于定时取消）
     */
    List<VisitorAuthorization> selectExpiredPendingActivation();

    /**
     * 批量更新状态为 unavailable（车位配置变更时）
     */
    int updateToUnavailable(@Param("communityId") Long communityId);

    /**
     * 查询指定房屋号下的授权列表
     */
    List<VisitorAuthorization> selectByHouse(@Param("communityId") Long communityId,
                                              @Param("houseNo") String houseNo);
}
