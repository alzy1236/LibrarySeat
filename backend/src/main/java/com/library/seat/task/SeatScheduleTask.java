package com.library.seat.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.library.seat.common.Constants;
import com.library.seat.dto.AssessmentSummary;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatReservation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.SeatReservationMapper;
import com.library.seat.mapper.StudyRoomMapper;
import com.library.seat.service.LongTermSeatService;
import com.library.seat.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 自习室定时任务
 *
 * <ul>
 *     <li>每月 1 号 00:00：开放本月长期固定座位；</li>
 *     <li>每月 1 号 02:00：对上月长期座位做签到签退考核（不满 20 天取消座位）；</li>
 *     <li>每日 00:05：短期自习室 24:00 未清场记录标记违规；</li>
 *     <li>每分钟：长期自习室 08:30 未签到预约自动失效。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatScheduleTask {

    private final LongTermSeatService longTermSeatService;
    private final SeatReservationMapper reservationMapper;
    private final StudyRoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final SysConfigService configService;

    /**
     * 每月 1 号零点：开放本月长期自习室固定座位
     */
    @Scheduled(cron = "${app.schedule.long-term-open-cron:0 0 0 1 * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void openLongTermMonth() {
        log.info("[定时任务] 每月 1 号开放长期固定座位");
        longTermSeatService.openNewMonth();
    }

    /**
     * 每月 1 号凌晨 2 点：对上月长期座位进行考核
     */
    @Scheduled(cron = "${app.schedule.long-term-assess-cron:0 0 2 1 * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void monthlyAssessment() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("[定时任务] 开始 {} 长期自习室签到签退考核", lastMonth);
        AssessmentSummary summary = longTermSeatService.assess(lastMonth, false);
        log.info("[定时任务] 考核完成：{}", summary);
    }

    /**
     * 短期自习室超时未清场检测（每日 00:05 检查前一天）
     */
    @Scheduled(cron = "${app.schedule.short-clear-check-cron:0 5 0 * * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void checkShortRoomClear() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int affected = reservationMapper.update(null, new LambdaUpdateWrapper<SeatReservation>()
                .set(SeatReservation::getOverstay, 1)
                .set(SeatReservation::getUpdateTime, LocalDateTime.now())
                .eq(SeatReservation::getReserveDate, yesterday)
                .eq(SeatReservation::getStatus, Constants.RES_SEATED)
                .inSql(SeatReservation::getRoomId, "SELECT id FROM study_room WHERE room_type = 'SHORT'"));
        if (affected > 0) {
            log.warn("[定时任务] {} 共有 {} 条短期自习室预约在 24:00 前未签退，已标记违规", yesterday, affected);
        }
    }

    /**
     * 每分钟：长期自习室 08:30 前未签到的预约自动失效
     */
    @Scheduled(cron = "${app.schedule.minute-cron:0 * * * * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void expireLongTermCheckIn() {
        String deadline = configService.getValue(Constants.CFG_CHECKIN_DEADLINE, "08:30");
        LocalTime now = LocalTime.now();
        String[] parts = deadline.split(":");
        LocalTime deadlineTime = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        if (now.isBefore(deadlineTime)) {
            return;
        }
        int affected = reservationMapper.update(null, new LambdaUpdateWrapper<SeatReservation>()
                .set(SeatReservation::getStatus, Constants.RES_TIMEOUT)
                .set(SeatReservation::getCancelReason, "超过签到截止时间 " + deadline + " 未签到")
                .set(SeatReservation::getUpdateTime, LocalDateTime.now())
                .eq(SeatReservation::getReserveDate, LocalDate.now())
                .eq(SeatReservation::getStatus, Constants.RES_WAITING)
                .inSql(SeatReservation::getRoomId, "SELECT id FROM study_room WHERE room_type = 'LONG'"));
        if (affected > 0) {
            log.info("[定时任务] 长期自习室 {} 条预约超时未签到，已失效", affected);
        }
    }

    /**
     * 系统启动后延迟执行一次超时检查（弥补服务停机期间遗漏）
     */
    @Scheduled(initialDelay = 15000, fixedDelay = 3600000)
    public void startupCatchUp() {
        try {
            expireLongTermCheckIn();
        } catch (Exception e) {
            log.warn("启动补偿检查失败: {}", e.getMessage());
        }
    }

    /**
     * 管理端手动触发长期座位考核
     *
     * @param monthStr yyyy-MM，为空默认上月
     */
    public AssessmentSummary manualAssess(String monthStr, boolean dryRun) {
        YearMonth month = (monthStr == null || monthStr.isEmpty()) ? null : YearMonth.parse(monthStr);
        return longTermSeatService.assess(month, dryRun);
    }

    /**
     * 今日长期自习室实时概览（未签到提醒）
     */
    public List<SeatReservation> todayWaiting() {
        return reservationMapper.selectReservationPage(queryWaiting());
    }

    private java.util.Map<String, Object> queryWaiting() {
        java.util.Map<String, Object> q = new java.util.HashMap<>();
        q.put("startDate", LocalDate.now());
        q.put("endDate", LocalDate.now());
        q.put("status", Constants.RES_WAITING);
        q.put("roomType", Constants.ROOM_TYPE_LONG);
        q.put("offset", 0L);
        q.put("limit", 500L);
        return q;
    }

    /**
     * 自习室总览（定时任务面板）
     */
    public List<StudyRoom> roomSnapshot() {
        return roomMapper.selectRoomStatistics(null, null, null, LocalDate.now());
    }

    public List<LongTermSeat> activeBinds() {
        return longTermSeatService.listActive(null, null);
    }

    public List<Seat> seatMap(Long roomId) {
        return seatMapper.selectSeatMap(roomId);
    }
}