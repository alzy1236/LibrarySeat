package com.library.seat.controller;

import com.library.seat.common.Constants;
import com.library.seat.common.Result;
import com.library.seat.common.UserContext;
import com.library.seat.dto.ClockStatus;
import com.library.seat.dto.PageResult;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatReservation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.service.ClockService;
import com.library.seat.service.ReservationService;
import com.library.seat.service.SeatService;
import com.library.seat.service.StudyRoomService;
import com.library.seat.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生端接口：自习室浏览、选座、签到签退、我的记录
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudyRoomService roomService;
    private final SeatService seatService;
    private final ReservationService reservationService;
    private final SysConfigService configService;
    private final ClockService clockService;

    /**
     * 开放的自习室列表（含今日预约统计）
     */
    @GetMapping("/rooms")
    public Result<List<StudyRoom>> rooms(@RequestParam(required = false) String roomType) {
        return Result.success(roomService.listWithStatistics(roomType, null, 1));
    }

    /**
     * 自习室详情 + 座位图
     */
    @GetMapping("/rooms/{roomId}")
    public Result<Map<String, Object>> roomDetail(@PathVariable Long roomId) {
        StudyRoom room = roomService.getByIdRequired(roomId);
        List<Seat> seats = seatService.seatMap(roomId);
        List<StudyRoom> stat = roomService.listWithStatistics(room.getRoomType(), null, null);
        Map<String, Object> map = new HashMap<>();
        map.put("room", room);
        for (StudyRoom r : stat) {
            if (r.getId().equals(roomId)) {
                map.put("room", r);
                break;
            }
        }
        map.put("seats", seats);
        map.put("total", seats.size());
        long available = seats.stream().filter(s -> "AVAILABLE".equals(s.getSeatState())).count();
        map.put("available", available);
        map.put("today", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        return Result.success(map);
    }

    /**
     * 选座
     */
    @PostMapping("/reserve")
    public Result<SeatReservation> reserve(@RequestParam Long seatId) {
        return Result.success("选座成功", reservationService.reserve(UserContext.getUserId(), seatId));
    }

    /**
     * 取消预约
     */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam Long reservationId) {
        reservationService.cancel(reservationId, UserContext.getUserId(), false);
        return Result.success("已取消预约", null);
    }

    /**
     * 签到
     */
    @PostMapping("/checkin")
    public Result<SeatReservation> checkIn(@RequestParam Long reservationId) {
        return Result.success("签到成功", reservationService.checkIn(reservationId, UserContext.getUserId()));
    }

    /**
     * 签退
     */
    @PostMapping("/checkout")
    public Result<SeatReservation> checkOut(@RequestParam Long reservationId) {
        return Result.success("签退成功", reservationService.checkOut(reservationId, UserContext.getUserId()));
    }

    /**
     * 今日预约（含已签退 / 已取消的当日记录，便于首页展示当日出勤）
     */
    @GetMapping("/today")
    public Result<SeatReservation> today() {
        return Result.success(reservationService.todayReservation(UserContext.getUserId()));
    }

    /**
     * 今日仍生效的预约（用于选座前判断是否已占用名额）
     */
    @GetMapping("/today/active")
    public Result<SeatReservation> todayActive() {
        return Result.success(reservationService.activeReservation(UserContext.getUserId()));
    }

    /**
     * 我的选座记录（分页）
     */
    @GetMapping("/reservations")
    public Result<PageResult<SeatReservation>> reservations(@RequestParam(defaultValue = "1") Long pageNum,
                                                           @RequestParam(defaultValue = "10") Long pageSize,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String startDate,
                                                           @RequestParam(required = false) String endDate,
                                                           @RequestParam(required = false) Long roomId) {
        Map<String, Object> query = new HashMap<>();
        query.put("userId", UserContext.getUserId());
        query.put("status", emptyToNull(status));
        query.put("roomId", roomId);
        query.put("startDate", emptyToNull(startDate));
        query.put("endDate", emptyToNull(endDate));
        query.put("offset", (pageNum - 1) * pageSize);
        query.put("limit", pageSize);
        return Result.success(reservationService.page(query));
    }

    /**
     * 实时服务器时间（签到 / 签退实时工具，WebSocket 断线时的备用校时接口）
     */
    @GetMapping("/clock")
    public Result<ClockStatus> clock() {
        Long userId = UserContext.getUserId();
        SeatReservation today = userId == null ? null : reservationService.todayReservation(userId);
        return Result.success(clockService.build(
                configService.getValue(Constants.CFG_CHECKIN_EARLY_OPEN, "07:00"),
                configService.getValue(Constants.CFG_CHECKIN_DEADLINE, "08:30"),
                configService.getValue(Constants.CFG_CHECKOUT_START, "21:00"),
                configService.getValue(Constants.CFG_CHECKOUT_END, "23:59"),
                today));
    }

    private String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}