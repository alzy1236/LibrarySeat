package com.library.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.seat.entity.SeatAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatAssessmentMapper extends BaseMapper<SeatAssessment> {

    List<SeatAssessment> selectPage(@Param("query") java.util.Map<String, Object> query);

    long count(@Param("query") java.util.Map<String, Object> query);
}