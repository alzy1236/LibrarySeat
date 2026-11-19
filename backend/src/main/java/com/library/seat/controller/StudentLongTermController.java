package com.library.seat.controller;

import com.library.seat.common.Result;
import com.library.seat.common.UserContext;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.StudyRoom;
import com.library.seat.service.ClockService;
import com.library.seat.service.LongTermSeatService;
import com.library.seat.service.SeatService;
import com.library.seat.service.StudyRoomService;
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
 * 学生端：长期自习室固定座位申请、签到日历、清场倒计时
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentLongTermController {

    private final LongTermSeatService longTermSeatService;
    private final StudyRoomService roomService;
    private final SeatService seatService;
    private final ClockService clockService;

    /**
     * 我的长期固定座位 & 本月签到情况 & 申请状态
     */
    @GetMapping("/long-term/mine")
    public Result<Map<String, Object>> mine() {
        Long userId = UserContext.getUserId();
        LongTermSeat bind = longTermSeatService.getActiveBinding(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("binding", bind);
        map.put("pending", longTermSeatService.getPendingBinding(userId));
        map.put("applies", longTermSeatService.listMine(userId));
        map.put("requiredDays", longTermSeatService.getRequiredDays());
        map.put("calendar", longTermSeatService.attendanceCalendar(userId,
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))));
        return Result.success(map);
    }

    /**
     * 长期自习室列表
     */
    @GetMapping("/long-term/rooms")
    public Result<List<StudyRoom>> longRooms() {
        return Result.success(roomService.listLongRooms());
    }

    /**
     * 长期自习室座位图（申请固定座位用）
     */
    @GetMapping("/long-term/rooms/{roomId}/seats")
    public Result<List<Seat>> roomSeats(@PathVariable Long roomId) {
        return Result.success(seatService.seatMap(roomId));
    }

    /**
     * 申请固定座位（提交后需管理员审核）
     */
    @PostMapping("/long-term/apply")
    public Result<LongTermSeat> apply(@RequestParam Long roomId, @RequestParam Long seatId) {
        return Result.success("申请已提交，请等待管理员审核", longTermSeatService.apply(UserContext.getUserId(), roomId, seatId));
    }

    /**
     * 撤回待审核的申请
     */
    @PostMapping("/long-term/withdraw")
    public Result<Void> withdraw(@RequestParam Long bindingId) {
        longTermSeatService.withdraw(bindingId, UserContext.getUserId());
        return Result.success("已撤回申请", null);
    }

    /**
     * 放弃固定座位
     */
    @PostMapping("/long-term/cancel")
    public Result<Void> cancel(@RequestParam Long bindingId) {
        longTermSeatService.cancel(bindingId, UserContext.getUserId(), false, "学生主动放弃");
        return Result.success("已放弃固定座位", null);
    }

    /**
     * 我的考核记录
     */
    @GetMapping("/long-term/assessments")
    public Result<Map<String, Object>> assessments() {
        Map<String, Object> query = new HashMap<>();
        query.put("userId", UserContext.getUserId());
        query.put("offset", 0L);
        query.put("limit", 12L);
        return Result.success(longTermSeatService.assessmentPage(query));
    }

    /**
     * 本月签到日历
     */
    @GetMapping("/long-term/calendar")
    public Result<Map<String, Object>> calendar(@RequestParam(required = false) String month) {
        return Result.success(longTermSeatService.attendanceCalendar(UserContext.getUserId(), month));
    }

    /**
     * 实时工具：短期自习室清场倒计时（距 24:00）
     */
    @GetMapping("/clear-countdown")
    public Result<Map<String, Object>> countdown() {
        Map<String, Object> map = new HashMap<>();
        map.put("secondsToMidnight", clockService.secondsToMidnight());
        map.put("rule", "短期自习室禁止存放个人物品，每日 24:00 前必须带走物品并离场");
        return Result.success(map);
    }
}