package com.parking.mapper;

import com.parking.model.OwnerHouseRel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业主房屋号关联 Mapper 接口
 */
@Mapper
public interface OwnerHouseRelMapper {

    /**
     * 插入业主房屋号关联记录
     *
     * @param rel 关联实体
     */
    void insert(OwnerHouseRel rel);
}
