package com.library.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.seat.entity.StudyRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StudyRoomMapper extends BaseMapper<StudyRoom> {

    /**
     * 查询自习室列表（含今日预约统计）
     */
    List<StudyRoom> selectRoomStatistics(@Param("roomType") String roomType,
                                         @Param("keyword") String keyword,
                                         @Param("status") Integer status,
                                         @Param("today") LocalDate today);
}