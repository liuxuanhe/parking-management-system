package com.parking.mapper;

import com.parking.model.ExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 导出任务 Mapper 接口
 */
@Mapper
public interface ExportTaskMapper {

    /**
     * 插入导出任务
     */
    void insert(@Param("task") ExportTask task);

    /**
     * 根据ID查询导出任务
     */
    ExportTask selectById(@Param("id") Long id);

    /**
     * 更新导出任务状态
     */
    void updateStatus(@Param("task") ExportTask task);
}
