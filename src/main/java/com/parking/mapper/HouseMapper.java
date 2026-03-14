package com.parking.mapper;

import com.parking.model.House;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 房屋号 Mapper 接口
 */
@Mapper
public interface HouseMapper {

    /**
     * 根据小区ID和房屋号查询房屋
     *
     * @param communityId 小区ID
     * @param houseNo     房屋号
     * @return 房屋实体
     */
    House selectByCommunityAndHouseNo(@Param("communityId") Long communityId,
                                       @Param("houseNo") String houseNo);
}
