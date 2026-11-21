package com.library.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.seat.entity.CheckinLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CheckinLocationMapper extends BaseMapper<CheckinLocation> {

    /**
     * 列表（带自习室名称）
     */
    List<CheckinLocation> selectWithRoom(@Param("keyword") String keyword);

    /**
     * 查询对指定自习室生效的签到点：优先精确匹配 room_id，否则取全楼通用点
     */
    CheckinLocation selectEffective(@Param("roomId") Long roomId);
}