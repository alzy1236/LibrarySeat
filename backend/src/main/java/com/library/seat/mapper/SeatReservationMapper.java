package com.library.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.seat.entity.SeatReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface SeatReservationMapper extends BaseMapper<SeatReservation> {

    /** 条件分页查询（学生看自己，管理员看全部） */
    List<SeatReservation> selectReservationPage(@Param("query") Map<String, Object> query);

    long countReservation(@Param("query") Map<String, Object> query);

    /** 查询详情（含关联信息） */
    SeatReservation selectDetail(@Param("id") Long id);

    /** 今日某座位对应的有效预约 */
    SeatReservation selectActiveBySeat(@Param("seatId") Long seatId, @Param("date") LocalDate date);

    /** 今日某学生的有效预约 */
    SeatReservation selectActiveByUser(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 近 N 天每日预约 / 签到 / 签退数量（图表） */
    List<Map<String, Object>> selectDailyTrend(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    /** 各自习室今日使用情况（图表） */
    List<Map<String, Object>> selectRoomUsage(@Param("date") LocalDate date);

    /** 各自习室近 N 天签到人数趋势 */
    List<Map<String, Object>> selectRoomTrend(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    /** 小时分布：今日各时段签到人数 */
    List<Map<String, Object>> selectHourlyCheckIn(@Param("date") LocalDate date);

    /** 某学生某月签到 / 签退天数统计 */
    Map<String, Object> selectMonthAttendance(@Param("userId") Long userId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    /** 某学生某月有签到的日期列表 */
    List<String> selectMonthCheckInDates(@Param("userId") Long userId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /** 全部有效签到记录（用于全局日历热力图，按日期+自习室） */
    List<Map<String, Object>> selectGlobalDaily(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /** 今日已到馆（签到）总人数 */
    long countTodayCheckIn(@Param("date") LocalDate date);
}