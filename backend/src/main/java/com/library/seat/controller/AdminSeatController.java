package com.library.seat.controller;

import com.library.seat.common.BusinessException;
import com.library.seat.common.Result;
import com.library.seat.dto.AssessmentSummary;
import com.library.seat.dto.PageResult;
import com.library.seat.dto.SeatDTO;
import com.library.seat.dto.StudyRoomDTO;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatAssessment;
import com.library.seat.entity.SeatReservation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.service.LongTermSeatService;
import com.library.seat.service.ReservationService;
import com.library.seat.service.SeatService;
import com.library.seat.service.StatisticsService;
import com.library.seat.service.StudyRoomService;
import com.library.seat.service.SysConfigService;
import com.library.seat.task.SeatScheduleTask;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端接口（/api/admin/** 仅管理员可访问，由 AuthInterceptor 统一校验）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSeatController {

    private final StudyRoomService roomService;
    private final SeatService seatService;
    private final ReservationService reservationService;
    private final LongTermSeatService longTermSeatService;
    private final StatisticsService statisticsService;
    private final SeatScheduleTask scheduleTask;
    private final SysConfigService configService;

    // ========================= 自习室管理 =========================

    @GetMapping("/rooms")
    public Result<List<StudyRoom>> rooms(@RequestParam(required = false) String roomType,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        return Result.success(roomService.listWithStatistics(empty(roomType), empty(keyword), status));
    }

    @PostMapping("/rooms")
    public Result<Long> addRoom(@Validated @RequestBody StudyRoomDTO dto) {
        return Result.success("自习室新增成功", roomService.saveRoom(dto));
    }

    @PutMapping("/rooms")
    public Result<Void> updateRoom(@Validated @RequestBody StudyRoomDTO dto) {
        roomService.updateRoom(dto);
        return Result.success("自习室修改成功", null);
    }

    @DeleteMapping("/rooms/{id}")
    public Result<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return Result.success("自习室已删除", null);
    }

    @PostMapping("/rooms/{id}/seats/generate")
    public Result<Integer> generateSeats(@PathVariable Long id, @RequestParam(defaultValue = "0") int count) {
        int num = count > 0 ? count : 0;
        if (num == 0) {
            throw new BusinessException("请输入要生成座位数量");
        }
        return Result.success("座位生成成功", roomService.generateSeats(id, num));
    }

    // ========================= 座位管理 =========================

    @GetMapping("/rooms/{roomId}/seats")
    public Result<List<Seat>> seats(@PathVariable Long roomId) {
        return Result.success(seatService.seatMap(roomId));
    }

    @GetMapping("/seats/{roomId}")
    public Result<List<Seat>> seatsAlias(@PathVariable Long roomId) {
        return Result.success(seatService.seatMap(roomId));
    }

    @PostMapping("/seats")
    public Result<Void> addSeat(@Validated @RequestBody SeatDTO dto) {
        seatService.saveSeat(dto);
        return Result.success("座位新增成功", null);
    }

    @PutMapping("/seats")
    public Result<Void> updateSeat(@Validated @RequestBody SeatDTO dto) {
        seatService.updateSeat(dto);
        return Result.success("座位修改成功", null);
    }

    @DeleteMapping("/seats/{id}")
    public Result<Void> deleteSeat(@PathVariable Long id) {
        seatService.deleteSeat(id);
        return Result.success("座位已删除", null);
    }

    // ========================= 预约记录 =========================

    @GetMapping("/reservations")
    public Result<PageResult<SeatReservation>> reservations(@RequestParam(defaultValue = "1") Long pageNum,
                                                           @RequestParam(defaultValue = "10") Long pageSize,
                                                           @RequestParam(required = false) Long userId,
                                                           @RequestParam(required = false) Long roomId,
                                                           @RequestParam(required = false) String roomType,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String startDate,
                                                           @RequestParam(required = false) String endDate,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer overstay) {
        Map<String, Object> query = new HashMap<>();
        query.put("userId", userId);
        query.put("roomId", roomId);
        query.put("roomType", empty(roomType));
        query.put("status", empty(status));
        query.put("startDate", empty(startDate));
        query.put("endDate", empty(endDate));
        query.put("keyword", empty(keyword));
        query.put("overstay", overstay);
        query.put("offset", (pageNum - 1) * pageSize);
        query.put("limit", pageSize);
        return Result.success(reservationService.page(query));
    }

    @PostMapping("/reservations/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestParam(required = false) String reason) {
        reservationService.adminRelease(id, reason);
        return Result.success("座位已释放", null);
    }

    @PostMapping("/reservations/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(id, null, true);
        return Result.success("预约已取消", null);
    }

    // ========================= 长期固定座位 =========================

    @GetMapping("/long-term")
    public Result<List<LongTermSeat>> longTermList(@RequestParam(required = false) Long roomId,
                                                   @RequestParam(required = false) String keyword) {
        return Result.success(longTermSeatService.listActive(roomId, empty(keyword)));
    }

    @PostMapping("/long-term/{id}/release")
    public Result<Void> releaseLongTerm(@PathVariable Long id,
                                        @RequestParam(required = false) String reason) {
        longTermSeatService.cancel(id, null, true, reason);
        return Result.success("固定座位已释放", null);
    }

    @GetMapping("/long-term/preview/{roomId}")
    public Result<Map<String, Object>> previewRoom(@PathVariable Long roomId) {
        return Result.success(longTermSeatService.previewRoom(roomId));
    }

    /**
     * 待审核的固定座位申请
     */
    @GetMapping("/long-term/pending")
    public Result<List<LongTermSeat>> pendingList(@RequestParam(required = false) Long roomId,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(longTermSeatService.listPending(roomId, empty(keyword)));
    }

    /**
     * 审核固定座位申请
     *
     * @param approve true 通过 / false 拒绝
     */
    @PostMapping("/long-term/{id}/review")
    public Result<LongTermSeat> review(@PathVariable Long id,
                                       @RequestParam(defaultValue = "true") boolean approve,
                                       @RequestParam(required = false) String remark) {
        LongTermSeat result = longTermSeatService.review(id, approve, remark);
        return Result.success(approve ? "已通过该申请" : "已拒绝该申请", result);
    }

    // ========================= 月度考核 =========================

    /**
     * 手动执行长期座位考核（默认考核上月；dryRun=true 只预览不取消）
     */
    @PostMapping("/assessment/run")
    public Result<AssessmentSummary> runAssessment(@RequestParam(required = false) String month,
                                                   @RequestParam(defaultValue = "false") boolean dryRun) {
        return Result.success(scheduleTask.manualAssess(month, dryRun));
    }

    @GetMapping("/assessment")
    public Result<Map<String, Object>> assessmentList(@RequestParam(defaultValue = "1") Long pageNum,
                                                      @RequestParam(defaultValue = "10") Long pageSize,
                                                      @RequestParam(required = false) String assessMonth,
                                                      @RequestParam(required = false) Integer qualified,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long userId) {
        Map<String, Object> query = new HashMap<>();
        query.put("assessMonth", empty(assessMonth));
        query.put("qualified", qualified);
        query.put("keyword", empty(keyword));
        query.put("userId", userId);
        query.put("offset", (pageNum - 1) * pageSize);
        query.put("limit", pageSize);
        return Result.success(longTermSeatService.assessmentPage(query));
    }

    // ========================= 数据统计 / 图表 =========================

    @GetMapping("/statistics/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(statisticsService.overview());
    }

    @GetMapping("/statistics/charts")
    public Result<Map<String, Object>> charts(@RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "7") int days) {
        return Result.success(statisticsService.charts(type, days));
    }

    @GetMapping("/statistics/distribution")
    public Result<Map<String, Object>> distribution() {
        return Result.success(statisticsService.statusDistribution());
    }

    @GetMapping("/statistics/room-trend")
    public Result<Map<String, Object>> roomTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(statisticsService.roomTrend(days));
    }

    // ========================= 规则查看 =========================

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(configService.allAsMap());
    }

    @PutMapping("/config")
    public Result<Void> updateConfig(@RequestBody Map<String, String> values) {
        configService.updateBatch(values);
        return Result.success("参数已更新", null);
    }

    private String empty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}