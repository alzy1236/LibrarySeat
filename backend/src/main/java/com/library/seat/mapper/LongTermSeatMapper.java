package com.library.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.seat.entity.LongTermSeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LongTermSeatMapper extends BaseMapper<LongTermSeat> {

    List<LongTermSeat> selectActiveList(@Param("roomId") Long roomId, @Param("keyword") String keyword);

    /** 待审核的固定座位申请 */
    List<LongTermSeat> selectPendingList(@Param("roomId") Long roomId, @Param("keyword") String keyword);

    LongTermSeat selectActiveByUser(@Param("userId") Long userId);

    LongTermSeat selectActiveBySeat(@Param("seatId") Long seatId);

    /** 统计某学生在某段时间内签到 / 签退天数 */
    List<java.util.Map<String, Object>> countAttendanceByUser(@Param("roomId") Long roomId,
                                                              @Param("startDate") LocalDate startDate,
                                                              @Param("endDate") LocalDate endDate);
}