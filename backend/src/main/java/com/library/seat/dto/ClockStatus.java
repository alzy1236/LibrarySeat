package com.library.seat.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 状态时间表单：服务器时间 + 各阶段窗口（前端实时时钟据此计算）
 */
@Data
public class ClockStatus {

    /** 服务器当前时间 */
    private String serverTime;
    private long serverTimestamp;
    /** yyyy-MM-dd */
    private String today;
    private String weekday;
    /** HH:mm:ss */
    private String time;

    /** 当前是否在长期自习室签到窗口内 */
    private boolean canCheckIn;
    /** 当前是否在长期自习室签退窗口内 */
    private boolean canCheckOut;
    /** 签到截止时间 HH:mm */
    private String checkInDeadline;
    /** 允许签到最早时间 HH:mm */
    private String checkInOpen;
    /** 签退开始 / 结束时间 */
    private String checkOutStart;
    private String checkOutEnd;
    /** 距签到截止还剩秒数（负数表示已过） */
    private long secondsToCheckInDeadline;
    /** 距 24:00 清场还剩秒数（短期自习室） */
    private long secondsToMidnight;
    /** 距签退开始时间还剩秒数（负数表示已过） */
    private long secondsToCheckOutStart;
    /** 当前是否在自习室开放时段内 */
    private boolean openForUse;

    /** 当前登录学生今日预约情况（未登录为 null） */
    private TodayReservation todayReservation;

    @Data
    public static class TodayReservation {
        private Long reservationId;
        private String roomName;
        private String roomType;
        private String seatNo;
        private String status;
        private String statusName;
        private String checkInTime;
        private String checkOutTime;
    }
}