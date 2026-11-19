package com.library.seat.service;

import com.library.seat.dto.ClockStatus;
import com.library.seat.entity.SeatReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时时钟服务（“实时工具”：以服务器时间为准，保障签到时间准确）
 *
 * <p>通过 WebSocket 以固定间隔广播服务器时间帧，客户端据此计算与服务器的时钟偏差，
 * 并使用高精度计时器平滑推进显示，避免本机时间被修改导致签到 / 签退时间不准。
 * 所有业务校验仍以服务器时间为唯一标准。</p>
 */
@Service
@RequiredArgsConstructor
public class ClockService {

    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEKDAYS = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};

    private final long bootTime = System.currentTimeMillis();
    private final AtomicLong tickSeq = new AtomicLong(0);

    public ClockStatus build(String checkInOpen, String checkInDeadline,
                             String checkOutStart, String checkOutEnd,
                             SeatReservation todayReservation) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime t = now.toLocalTime();
        ClockStatus status = new ClockStatus();
        status.setServerTime(now.format(DATETIME_FMT));
        status.setServerTimestamp(System.currentTimeMillis());
        status.setToday(now.format(DATE_FMT));
        status.setWeekday(WEEKDAYS[now.getDayOfWeek().getValue() - 1]);
        status.setTime(now.format(TIME_FMT));

        LocalTime open = parse(checkInOpen, LocalTime.of(7, 0));
        LocalTime deadline = parse(checkInDeadline, LocalTime.of(8, 30));
        LocalTime outStart = parse(checkOutStart, LocalTime.of(21, 0));
        LocalTime outEnd = parse(checkOutEnd, LocalTime.of(23, 59));
        if (outEnd.equals(LocalTime.MIDNIGHT)) {
            outEnd = LocalTime.of(23, 59, 59);
        }

        status.setCheckInOpen(checkInOpen);
        status.setCheckInDeadline(checkInDeadline);
        status.setCheckOutStart(checkOutStart);
        status.setCheckOutEnd(checkOutEnd);
        status.setCanCheckIn(!t.isBefore(open) && !t.isAfter(deadline));
        status.setCanCheckOut(!t.isBefore(outStart) && !t.isAfter(outEnd));
        status.setSecondsToCheckInDeadline(Duration.between(now, now.toLocalDate().atTime(deadline)).getSeconds());
        status.setSecondsToCheckOutStart(Duration.between(now, now.toLocalDate().atTime(outStart)).getSeconds());
        status.setSecondsToMidnight(secondsToMidnight());

        if (todayReservation != null) {
            ClockStatus.TodayReservation tr = new ClockStatus.TodayReservation();
            tr.setReservationId(todayReservation.getId());
            tr.setRoomName(todayReservation.getRoomName());
            tr.setRoomType(todayReservation.getRoomType());
            tr.setSeatNo(todayReservation.getSeatNo());
            tr.setStatus(todayReservation.getStatus());
            tr.setStatusName(todayReservation.getStatusName());
            tr.setCheckInTime(todayReservation.getCheckInTime() == null ? null
                    : todayReservation.getCheckInTime().format(DATETIME_FMT));
            tr.setCheckOutTime(todayReservation.getCheckOutTime() == null ? null
                    : todayReservation.getCheckOutTime().format(DATETIME_FMT));
            status.setTodayReservation(tr);
        }
        return status;
    }

    /**
     * 用于 WebSocket 的轻量时间帧
     */
    public Map<String, Object> serverFrame() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "tick");
        frame.put("seq", tickSeq.incrementAndGet());
        frame.put("timestamp", System.currentTimeMillis());
        frame.put("serverTime", now.format(DATETIME_FMT));
        frame.put("date", now.format(DATE_FMT));
        frame.put("time", now.format(TIME_FMT));
        frame.put("weekday", WEEKDAYS[now.getDayOfWeek().getValue() - 1]);
        frame.put("uptimeSeconds", (System.currentTimeMillis() - bootTime) / 1000);
        return frame;
    }

    /**
     * 距离次日 00:00（短期自习室清场时间）的秒数
     */
    public long secondsToMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    private LocalTime parse(String s, LocalTime def) {
        try {
            if (s == null || s.trim().isEmpty()) {
                return def;
            }
            String[] p = s.trim().split(":");
            return LocalTime.of(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
        } catch (Exception e) {
            return def;
        }
    }
}