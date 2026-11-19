package com.library.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.seat.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 查询某自习室座位图（附带今日占用情况）
     */
    List<Seat> selectSeatMap(@Param("roomId") Long roomId);

    /**
     * 批量新增（用于按行门生成座位）
     */
    int insertBatch(@Param("list") List<Seat> list);
}