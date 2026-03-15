package com.parking.mapper;

import com.parking.model.VisitorApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Visitor 申请 Mapper 接口
 */
@Mapper
public interface VisitorApplicationMapper {

    /**
     * 插入申请记录
     */
    int insert(VisitorApplication application);

    /**
     * 根据ID查询申请
     */
    VisitorApplication selectById(@Param("id") Long id);

    /**
     * 使用行级锁查询申请（SELECT ... FOR UPDATE）
     */
    VisitorApplication selectByIdForUpdate(@Param("id") Long id);

    /**
     * 更新申请状态
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason,
                     @Param("auditAdminId") Long auditAdminId);

    /**
     * 查询指定房屋号下的 Visitor 申请列表
     */
    List<VisitorApplication> selectByHouse(@Param("communityId") Long communityId,
                                           @Param("houseNo") String houseNo);
}
