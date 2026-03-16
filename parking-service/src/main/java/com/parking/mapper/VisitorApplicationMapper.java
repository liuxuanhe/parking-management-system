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

    /**
     * 根据ID列表批量查询申请（使用行级锁）
     */
    List<VisitorApplication> selectByIdsForUpdate(@Param("ids") List<Long> ids);

    /**
     * 按小区分页查询 Visitor 申请列表（Admin_Portal 使用）
     * 支持按状态筛选，houseNo 可选
     *
     * @param communityId 小区ID
     * @param status      状态筛选（可选）
     * @param offset      偏移量
     * @param pageSize    每页条数
     * @return 申请列表
     */
    List<VisitorApplication> selectByCommunityPaged(@Param("communityId") Long communityId,
                                                     @Param("status") String status,
                                                     @Param("offset") int offset,
                                                     @Param("pageSize") int pageSize);

    /**
     * 按小区统计 Visitor 申请总数（Admin_Portal 分页用）
     *
     * @param communityId 小区ID
     * @param status      状态筛选（可选）
     * @return 总记录数
     */
    long countByCommunity(@Param("communityId") Long communityId,
                          @Param("status") String status);
}
