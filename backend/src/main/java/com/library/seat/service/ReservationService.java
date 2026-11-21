package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.library.seat.common.BusinessException;
import com.library.seat.common.Constants;
import com.library.seat.dto.PageResult;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatReservation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.SeatReservationMapper;
import com.library.seat.mapper.StudyRoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 座位预约服务：选座、取消、签到、签退、记录查询
 *
 * 规则：
 * 1. 学生每天只能预约一个座位（当天有效）；
 * 2. 长期自习室只允许持有该自习室固定座位的学生使用（需先申请长期座位）；
 * 3. 短期自习室禁止存放个人物品，24:00 前须签退离场，超时记违规；
 * 4. 签到 / 签退时间以服务器时间为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final SeatReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final StudyRoomMapper roomMapper;
    private final SysConfigService configService;
    private final LongTermSeatService longTermSeatService;
    private final CheckinLocationService locationService;

    /**
     * 学生选座
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatReservation reserve(Long userId, Long seatId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new BusinessException(103, "座位不存在");
        }
        if (seat.getStatus() != null && seat.getStatus() == 0) {
            throw new BusinessException(104, "该座位正在维修，暂不可选");
        }
        StudyRoom room = roomMapper.selectById(seat.getRoomId());
        if (room == null || room.getStatus() == null || room.getStatus() == 0) {
            throw new BusinessException(104, "该自习室未开放");
        }
        if (isOutsideOpenTime(room, now.toLocalTime())) {
            throw new BusinessException(105, "当前不在 " + room.getName() + " 的开放时间（" + room.getOpenStart() + "-" + room.getOpenEnd() + "）内");
        }

        SeatReservation active = reservationMapper.selectActiveByUser(userId, today);
        if (active != null) {
            throw new BusinessException(106, "今日已预约座位 " + active.getSeatNo() + "，每天只能预约一个座位");
        }

        // 长期自习室：必须持有该自习室的固定座位
        if (Constants.ROOM_TYPE_LONG.equals(room.getRoomType())) {
            LongTermSeat bind = longTermSeatService.getActiveBinding(userId);
            if (bind == null) {
                throw new BusinessException(107, "长期自习室仅限已申请固定座位的学生使用，请先申请");
            }
            if (!bind.getRoomId().equals(room.getId())) {
                throw new BusinessException(107, "您的固定座位在 " + bind.getRoomName() + "，无法预约其他长期自习室");
            }
            if (!bind.getSeatId().equals(seatId)) {
                throw new BusinessException(107, "长期自习室须使用您的固定座位 " + bind.getSeatNo());
            }
        }

        // 座位占用校验（行锁防止并发抢座）
        SeatReservation occupied = reservationMapper.selectActiveBySeat(seatId, today);
        if (occupied != null) {
            throw new BusinessException(108, "该座位今日已被占用，请选择其他座位");
        }

        SeatReservation res = new SeatReservation();
        res.setUserId(userId);
        res.setRoomId(room.getId());
        res.setSeatId(seatId);
        res.setReserveDate(today);
        res.setStatus(Constants.RES_WAITING);
        res.setOverstay(0);
        res.setCreateTime(now);
        res.setUpdateTime(now);
        reservationMapper.insert(res);
        log.info("学生 {} 预约座位 {} ({}) 成功", userId, seat.getSeatNo(), room.getName());
        return reservationMapper.selectDetail(res.getId());
    }

    /**
     * 取消预约
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long reservationId, Long userId, boolean admin) {
        SeatReservation res = reservationMapper.selectById(reservationId);
        if (res == null) {
            throw new BusinessException("预约记录不存在");
        }
        if (!admin && !res.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的预约记录");
        }
        if (Constants.RES_SEATED.equals(res.getStatus())) {
            throw new BusinessException("已签到的座位请使用签退功能释放");
        }
        if (!Constants.RES_WAITING.equals(res.getStatus())) {
            throw new BusinessException("该预约记录已失效，无法取消");
        }
        res.setStatus(Constants.RES_CANCELLED);
        res.setCancelReason(admin ? "管理员取消" : "学生主动取消");
        res.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(res);
    }

    /**
     * 签到（仅长期自习室：每天 08:30 前签到）
     *
     * @param lat 学生当前纬度（WGS-84），未开启定位校验时可传 null
     * @param lng 学生当前经度（WGS-84），未开启定位校验时可传 null
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatReservation checkIn(Long reservationId, Long userId, Double lat, Double lng) {
        SeatReservation res = getOwnedReservation(reservationId, userId);
        StudyRoom room = roomMapper.selectById(res.getRoomId());
        LocalDateTime now = LocalDateTime.now();

        if (!Constants.ROOM_TYPE_LONG.equals(room.getRoomType())) {
            throw new BusinessException(110, "短期自习室无需签到，预约后即可使用，请注意 24:00 前签退清场");
        }
        if (!Constants.RES_WAITING.equals(res.getStatus())) {
            throw new BusinessException(111, "当前预约状态不能签到：" + res.getStatusName());
        }

        String open = configService.getValue(Constants.CFG_CHECKIN_EARLY_OPEN, "07:00");
        String deadline = configService.getValue(Constants.CFG_CHECKIN_DEADLINE, "08:30");
        if (now.toLocalTime().isBefore(parseTime(open))) {
            throw new BusinessException(112, "签到通道将于 " + open + " 开启，请稍后再试");
        }
        if (now.toLocalTime().isAfter(parseTime(deadline))) {
            throw new BusinessException(113, "已超过签到截止时间 " + deadline + "，今日签到失败，将影响本月考核");
        }

        // 地理围栏校验：必须在签到点允许范围内
        locationService.assertInRange(res.getRoomId(), lat, lng, "签到");

        res.setStatus(Constants.RES_SEATED);
        res.setCheckInTime(now);
        res.setUpdateTime(now);
        reservationMapper.updateById(res);
        log.info("学生 {} 于 {} 签到座位 {}", userId, now, res.getSeatId());
        return reservationMapper.selectDetail(res.getId());
    }

    /**
     * 兼容旧调用（不做定位校验）
     */
    public SeatReservation checkIn(Long reservationId, Long userId) {
        return checkIn(reservationId, userId, null, null);
    }

    /**
     * 签退
     *
     * @param lat 学生当前纬度（WGS-84）
     * @param lng 学生当前经度（WGS-84）
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatReservation checkOut(Long reservationId, Long userId, Double lat, Double lng) {
        SeatReservation res = getOwnedReservation(reservationId, userId);
        StudyRoom room = roomMapper.selectById(res.getRoomId());
        LocalDateTime now = LocalDateTime.now();
        boolean longTerm = Constants.ROOM_TYPE_LONG.equals(room.getRoomType());

        if (Constants.RES_LEFT.equals(res.getStatus())) {
            throw new BusinessException(114, "您今日已签退");
        }
        if (Constants.RES_WAITING.equals(res.getStatus())) {
            throw new BusinessException(114, "尚未签到，无需签退");
        }
        if (!Constants.RES_SEATED.equals(res.getStatus())) {
            throw new BusinessException(114, "当前预约状态不能签退：" + res.getStatusName());
        }

        String start = configService.getValue(Constants.CFG_CHECKOUT_START, "21:00");
        String end = configService.getValue(Constants.CFG_CHECKOUT_END, "23:59");
        if (longTerm) {
            String early = configService.getValue(Constants.CFG_CHECKIN_EARLY_OPEN, "07:00");
            if (now.toLocalTime().isBefore(parseTime(start)) && now.toLocalTime().isAfter(parseTime(early))) {
                throw new BusinessException(115, "签退通道于 " + start + " 开启，当前时间不能签退，将不计入本月考核天数");
            }
        }

        // 地理围栏校验：必须在签到点允许范围内
        locationService.assertInRange(res.getRoomId(), lat, lng, "签退");

        res.setStatus(Constants.RES_LEFT);
        res.setCheckOutTime(now);
        res.setUpdateTime(now);
        reservationMapper.updateById(res);
        log.info("学生 {} 于 {} 签退座位 {} ({})", userId, now, res.getSeatId(), room.getName());
        return reservationMapper.selectDetail(res.getId());
    }

    /**
     * 兼容旧调用（不做定位校验）
     */
    public SeatReservation checkOut(Long reservationId, Long userId) {
        return checkOut(reservationId, userId, null, null);
    }

    /**
     * 管理员代签 / 强制释放
     */
    @Transactional(rollbackFor = Exception.class)
    public void adminRelease(Long reservationId, String reason) {
        SeatReservation res = reservationMapper.selectById(reservationId);
        if (res == null) {
            throw new BusinessException("预约记录不存在");
        }
        if (!Constants.RES_SEATED.equals(res.getStatus()) && !Constants.RES_WAITING.equals(res.getStatus())) {
            throw new BusinessException("该记录已结束，无需释放");
        }
        res.setStatus(Constants.RES_LEFT);
        res.setCheckOutTime(LocalDateTime.now());
        res.setCancelReason(StringUtils.hasText(reason) ? reason : "管理员释放");
        res.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(res);
    }

    /**
     * 分页查询预约记录
     */
    public PageResult<SeatReservation> page(Map<String, Object> query) {
        long total = reservationMapper.countReservation(query);
        List<SeatReservation> records = total == 0 ? java.util.Collections.emptyList()
                : reservationMapper.selectReservationPage(query);
        return PageResult.of(total, records);
    }

    public SeatReservation detail(Long id) {
        SeatReservation res = reservationMapper.selectDetail(id);
        if (res == null) {
            throw new BusinessException("预约记录不存在");
        }
        return res;
    }

    /**
     * 今日预约（学生端首页 / 实时工具使用）
     *
     * <p>优先返回仍在生效（待签到 / 使用中）的预约；若已签退或已取消，
     * 则返回今天的最后一条记录，便于首页展示当日出勤情况。</p>
     */
    public SeatReservation todayReservation(Long userId) {
        LocalDate today = LocalDate.now();
        SeatReservation active = reservationMapper.selectActiveByUser(userId, today);
        if (active != null) {
            return reservationMapper.selectDetail(active.getId());
        }
        SeatReservation latest = reservationMapper.selectOne(new LambdaQueryWrapper<SeatReservation>()
                .eq(SeatReservation::getUserId, userId)
                .eq(SeatReservation::getReserveDate, today)
                .orderByDesc(SeatReservation::getId)
                .last("LIMIT 1"));
        return latest == null ? null : reservationMapper.selectDetail(latest.getId());
    }

    /**
     * 今日仍在生效的预约（同日只能存在一条，选座时用于拦截重复预约）
     */
    public SeatReservation activeReservation(Long userId) {
        return reservationMapper.selectActiveByUser(userId, LocalDate.now());
    }

    /**
     * 学生历史记录
     */
    public List<SeatReservation> history(Long userId, int limit) {
        return reservationMapper.selectList(new LambdaQueryWrapper<SeatReservation>()
                .eq(SeatReservation::getUserId, userId)
                .orderByDesc(SeatReservation::getReserveDate)
                .orderByDesc(SeatReservation::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    /**
     * 超时未签到处理（长期自习室 08:30 未签到自动置为 TIMEOUT）
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireUnchecked(Long userId) {
        int affected = reservationMapper.update(null, new LambdaUpdateWrapper<SeatReservation>()
                .set(SeatReservation::getStatus, Constants.RES_TIMEOUT)
                .set(SeatReservation::getUpdateTime, LocalDateTime.now())
                .eq(SeatReservation::getUserId, userId)
                .eq(SeatReservation::getReserveDate, LocalDate.now())
                .eq(SeatReservation::getStatus, Constants.RES_WAITING)
                .inSql(SeatReservation::getRoomId, "select id from study_room where room_type = 'LONG'"));
        return affected;
    }

    private SeatReservation getOwnedReservation(Long reservationId, Long userId) {
        SeatReservation res = reservationMapper.selectById(reservationId);
        if (res == null) {
            throw new BusinessException("预约记录不存在");
        }
        if (!res.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的预约记录");
        }
        return res;
    }

    private boolean isOutsideOpenTime(StudyRoom room, LocalTime now) {
        if (!StringUtils.hasText(room.getOpenStart()) || !StringUtils.hasText(room.getOpenEnd())) {
            return false;
        }
        LocalTime start = parseTime(room.getOpenStart());
        LocalTime end = parseTime(room.getOpenEnd());
        return now.isBefore(start) || now.isAfter(end);
    }

    private LocalTime parseTime(String time) {
        try {
            String[] parts = time.trim().split(":");
            return LocalTime.of(Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
        } catch (Exception e) {
            return LocalTime.MIDNIGHT;
        }
    }

    public SeatReservationMapper getMapper() {
        return reservationMapper;
    }
}