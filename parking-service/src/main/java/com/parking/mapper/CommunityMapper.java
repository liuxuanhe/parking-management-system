package com.parking.mapper;

import com.parking.model.Community;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 小区 Mapper 接口
 */
@Mapper
public interface CommunityMapper {

    /**
     * 查询所有小区列表
     *
     * @return 小区列表
     */
    List<Community> selectAll();

    /**
     * 根据ID查询小区
     *
     * @param id 小区ID
     * @return 小区实体
     */
    Community selectById(@Param("id") Long id);
}
