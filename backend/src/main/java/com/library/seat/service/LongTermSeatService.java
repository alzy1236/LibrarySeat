package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.library.seat.common.BusinessException;
import com.library.seat.common.Constants;
import com.library.seat.dto.AssessmentSummary;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatAssessment;
import com.library.seat.entity.SeatReservation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.mapper.LongTermSeatMapper;
import com.library.seat.mapper.SeatAssessmentMapper;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.SeatReservationMapper;
import com.library.seat.mapper.StudyRoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 长期自习室固定座位服务
 *
 * 规则：
 * 1. 长期自习室固定座位每月 1 号零点自动开放，先到先得；
 * 2. 持有固定座位的学生每天须 08:30 前签到、21:00-24:00 签退；
 * 3. 每月 1 号凌晨对上月进行考核，签到并签退天数不满 20 天的，取消其座位信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermSeatService extends ServiceImpl<LongTermSeatMapper, LongTermSeat> {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SeatMapper seatMapper;
    private final StudyRoomMapper roomMapper;
    private final SeatReservationMapper reservationMapper;
    private final SeatAssessmentMapper assessmentMapper;
    private final SysConfigService configService;

    @Value("${app.assessment.min-days:20}")
    private int defaultMinDays;

    public int getRequiredDays() {
        return configService.getInt(Constants.CFG_LONG_MIN_DAYS, defaultMinDays);
    }

    public LongTermSeat getActiveBinding(Long userId) {
        return baseMapper.selectActiveByUser(userId);
    }

    /**
     * 查询学生当前“待审核”的申请（补充自习室 / 座位编号等展示信息）
     */
    public LongTermSeat getPendingBinding(Long userId) {
        List<LongTermSeat> list = getList(new LambdaQueryWrapper<LongTermSeat>()
                .eq(LongTermSeat::getUserId, userId)
                .eq(LongTermSeat::getStatus, Constants.BIND_PENDING)
                .orderByDesc(LongTermSeat::getId)
                .last("LIMIT 1"));
        if (list == null || list.isEmpty()) {
            return null;
        }
        return enrich(list.get(0));
    }

    /**
     * 补充自习室名称、座位编号等关联字段（基础 Mapper 查询不返回这些字段）
     */
    private LongTermSeat enrich(LongTermSeat bind) {
        if (bind == null) {
            return null;
        }
        if (bind.getSeatId() != null) {
            Seat seat = seatMapper.selectById(bind.getSeatId());
            if (seat != null) {
                bind.setSeatNo(seat.getSeatNo());
            }
        }
        Long roomId = bind.getRoomId();
        if (roomId == null && bind.getSeatId() != null) {
            Seat seat = seatMapper.selectById(bind.getSeatId());
            roomId = seat == null ? null : seat.getRoomId();
            bind.setRoomId(roomId);
        }
        if (roomId != null) {
            StudyRoom room = roomMapper.selectById(roomId);
            if (room != null) {
                bind.setRoomName(room.getName());
            }
        }
        return bind;
    }

    private List<LongTermSeat> getList(LambdaQueryWrapper<LongTermSeat> wrapper) {
        return baseMapper.selectList(wrapper);
    }

    /**
     * 提交长期固定座位申请（需管理员审核通过后才生效）
     */
    @Transactional(rollbackFor = Exception.class)
    public LongTermSeat apply(Long userId, Long roomId, Long seatId) {
        StudyRoom room = roomMapper.selectById(roomId);
        if (room == null || room.getStatus() == null || room.getStatus() == 0) {
            throw new BusinessException(120, "自习室不存在或未开放");
        }
        if (!Constants.ROOM_TYPE_LONG.equals(room.getRoomType())) {
            throw new BusinessException(121, "该自习室不提供长期固定座位");
        }
        // 当前月份是否已开放长期约定
        String activeMonth = configService.getValue(Constants.CFG_LONG_ACTIVE_MONTH,
                String.valueOf(LocalDate.now().getMonthValue()));
        if (LocalDate.now().getMonthValue() != Integer.parseInt(activeMonth)) {
            throw new BusinessException(122, "本月固定座位尚未开放，请于每月 1 号后申请");
        }
        LongTermSeat current = baseMapper.selectActiveByUser(userId);
        if (current != null) {
            throw new BusinessException(123, "您已有固定座位 " + current.getSeatNo() + "（" + current.getRoomName() + "）");
        }
        LongTermSeat pending = getPendingBinding(userId);
        if (pending != null) {
            throw new BusinessException(125, "您有一个座位申请正在审核中（" + pending.getSeatNo() + "），请等待管理员审核");
        }
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null || !seat.getRoomId().equals(roomId)) {
            throw new BusinessException(103, "座位不存在");
        }
        if (seat.getStatus() != null && seat.getStatus() == 0) {
            throw new BusinessException(104, "该座位正在维修，暂不可申请");
        }
        if (baseMapper.selectActiveBySeat(seatId) != null) {
            throw new BusinessException(124, "该座位已被占用，请选择其他座位");
        }
        // 同一座位只能存在一条待审核申请
        long seatPending = count(new LambdaQueryWrapper<LongTermSeat>()
                .eq(LongTermSeat::getSeatId, seatId)
                .eq(LongTermSeat::getStatus, Constants.BIND_PENDING));
        if (seatPending > 0) {
            throw new BusinessException(124, "该座位已有同学申请，正在等待审核，请选择其他座位");
        }

        LongTermSeat bind = new LongTermSeat();
        bind.setUserId(userId);
        bind.setRoomId(roomId);
        bind.setSeatId(seatId);
        // 申请状态：待管理员审核
        bind.setStatus(Constants.BIND_PENDING);
        bind.setApplyTime(LocalDateTime.now());
        bind.setCreateTime(LocalDateTime.now());
        save(bind);
        log.info("学生 {} 提交固定座位申请 {} ({})，等待管理员审核", userId, seat.getSeatNo(), room.getName());
        return getById(bind.getId());
    }

    /**
     * 管理员审核申请
     *
     * @param bindingId 申请记录 id
     * @param approve   true 通过 / false 拒绝
     * @param remark    审核意见
     */
    @Transactional(rollbackFor = Exception.class)
    public LongTermSeat review(Long bindingId, boolean approve, String remark) {
        LongTermSeat bind = getById(bindingId);
        if (bind == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!Constants.BIND_PENDING.equals(bind.getStatus())) {
            throw new BusinessException("该申请已处理，无需重复审核");
        }
        if (approve) {
            // 通过前再次校验座位是否已被他人占用
            if (baseMapper.selectActiveBySeat(bind.getSeatId()) != null) {
                throw new BusinessException("该座位已被占用，无法通过审核");
            }
            if (baseMapper.selectActiveByUser(bind.getUserId()) != null) {
                throw new BusinessException("该学生已有固定座位，无法通过审核");
            }
            bind.setStatus(Constants.BIND_ACTIVE);
            bind.setBindTime(LocalDateTime.now());
            bind.setCancelReason(null);
        } else {
            bind.setStatus(Constants.BIND_REJECTED);
            bind.setCancelReason(remark == null || remark.trim().isEmpty() ? "管理员未通过审核" : remark.trim());
        }
        bind.setReviewRemark(remark);
        bind.setReviewTime(LocalDateTime.now());
        updateById(bind);
        log.info("管理员{}固定座位申请 {}：{}", approve ? "通过" : "拒绝", bindingId, bind.getSeatNo());
        return baseMapper.selectById(bindingId);
    }

    /**
     * 学生撤回待审核的申请
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long bindingId, Long userId) {
        LongTermSeat bind = getById(bindingId);
        if (bind == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!bind.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作他人的申请");
        }
        if (!Constants.BIND_PENDING.equals(bind.getStatus())) {
            throw new BusinessException("只有待审核的申请可以撤回");
        }
        bind.setStatus(Constants.BIND_CANCELLED);
        bind.setCancelTime(LocalDateTime.now());
        bind.setCancelReason("学生撤回申请");
        updateById(bind);
    }

    /**
     * 学生放弃 / 管理员释放固定座位
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long bindingId, Long operatorId, boolean admin, String reason) {
        LongTermSeat bind = getById(bindingId);
        if (bind == null) {
            throw new BusinessException("固定座位记录不存在");
        }
        if (!admin && !bind.getUserId().equals(operatorId)) {
            throw new BusinessException(403, "无权操作他人的固定座位");
        }
        if (!Constants.BIND_ACTIVE.equals(bind.getStatus())) {
            throw new BusinessException("该固定座位已释放");
        }
        bind.setStatus(Constants.BIND_CANCELLED);
        bind.setCancelTime(LocalDateTime.now());
        bind.setCancelReason(reason == null ? (admin ? "管理员释放" : "学生主动放弃") : reason);
        updateById(bind);
        // 同步释放今日预约
        SeatReservation res = reservationMapper.selectActiveBySeat(bind.getSeatId(), LocalDate.now());
        if (res != null && res.getUserId().equals(bind.getUserId())) {
            res.setStatus(Constants.RES_CANCELLED);
            res.setCancelReason("固定座位已释放");
            res.setUpdateTime(LocalDateTime.now());
            reservationMapper.updateById(res);
        }
        log.info("长期固定座位 {} 已释放：{}", bindingId, bind.getCancelReason());
    }

    /**
     * 使用中的固定座位列表（含本月签到签退天数）
     */
    public List<LongTermSeat> listActive(Long roomId, String keyword) {
        List<LongTermSeat> list = baseMapper.selectActiveList(roomId, keyword);
        YearMonth month = YearMonth.now();
        for (LongTermSeat item : list) {
            Map<String, Object> stat = reservationMapper.selectMonthAttendance(
                    item.getUserId(), month.atDay(1), month.atEndOfMonth());
            item.setMonthCheckInDays(toInt(stat, "checkInDays"));
            item.setMonthCheckOutDays(toInt(stat, "checkOutDays"));
        }
        return list;
    }

    /**
     * 待审核的固定座位申请（管理员审核列表）
     */
    public List<LongTermSeat> listPending(Long roomId, String keyword) {
        List<LongTermSeat> list = baseMapper.selectPendingList(roomId, keyword);
        YearMonth month = YearMonth.now();
        for (LongTermSeat item : list) {
            Map<String, Object> stat = reservationMapper.selectMonthAttendance(
                    item.getUserId(), month.atDay(1), month.atEndOfMonth());
            item.setMonthCheckInDays(toInt(stat, "checkInDays"));
            item.setMonthCheckOutDays(toInt(stat, "checkOutDays"));
        }
        return list;
    }

    /**
     * 我的申请历史（含待审核 / 已通过 / 被拒绝）
     */
    public List<LongTermSeat> listMine(Long userId) {
        List<LongTermSeat> list = baseMapper.selectList(new LambdaQueryWrapper<LongTermSeat>()
                .eq(LongTermSeat::getUserId, userId)
                .orderByDesc(LongTermSeat::getId)
                .last("LIMIT 10"));
        if (list == null) {
            return new java.util.ArrayList<>();
        }
        list.forEach(this::enrich);
        return list;
    }

    /**
     * 预约情况预览：某自习室所有固定座位 + 是否被占用
     */
    public Map<String, Object> previewRoom(Long roomId) {
        StudyRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("自习室不存在");
        }
        List<Seat> seats = seatMapper.selectSeatMap(roomId);
        Map<String, Object> result = new HashMap<>();
        result.put("room", room);
        result.put("seats", seats);
        result.put("requiredDays", getRequiredDays());
        return result;
    }

    /**
     * 月度考核（按月执行）：签到且签退天数 < 要求天数则取消固定座位
     *
     * @param month 考核月份，为空默认上个月
     */
    @Transactional(rollbackFor = Exception.class)
    public AssessmentSummary assess(YearMonth month, boolean dryRun) {
        if (month == null) {
            month = YearMonth.now().minusMonths(1);
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        String monthStr = month.format(MONTH_FMT);
        int required = getRequiredDays();

        List<LongTermSeat> activeBinds = baseMapper.selectActiveList(null, null);
        AssessmentSummary summary = new AssessmentSummary();
        summary.setMonth(monthStr);
        summary.setRule(String.format("签到且签退天数不少于 %d 天（考核区间 %s ~ %s）", required, start, end));

        int total = 0, qualified = 0, unqualified = 0, cancelled = 0;
        for (LongTermSeat bind : activeBinds) {
            // 绑定晚于考核月的，不参与本次考核
            if (bind.getBindTime() != null && bind.getBindTime().toLocalDate().isAfter(end)) {
                continue;
            }
            Map<String, Object> stat = reservationMapper.selectMonthAttendance(bind.getUserId(), start, end);
            int bothDays = toInt(stat, "bothDays");
            int checkInDays = toInt(stat, "checkInDays");
            int checkOutDays = toInt(stat, "checkOutDays");

            // 考核月份内没有任何预约记录：
            //  - 若固定座位是考核月中途（16 号及以后）才申请的，视为新用户，不参与当月考核，从下月起考核；
            //  - 否则说明整月均未使用，直接判为不合格并按规则取消座位。
            boolean noData = bothDays == 0 && checkInDays == 0 && checkOutDays == 0;
            if (noData && bind.getBindTime() != null && bind.getBindTime().toLocalDate().isAfter(end.minusDays(15))) {
                log.info("跳过新申请用户 {} 的 {} 考核（申请时间 {}）", bind.getUserId(), monthStr, bind.getBindTime());
                continue;
            }
            total++;
            boolean ok = bothDays >= required;
            if (ok) {
                qualified++;
            } else {
                unqualified++;
            }

            // 记录考核结果（唯一键 user+room+month）
            SeatAssessment exists = assessmentMapper.selectOne(new LambdaQueryWrapper<SeatAssessment>()
                    .eq(SeatAssessment::getUserId, bind.getUserId())
                    .eq(SeatAssessment::getRoomId, bind.getRoomId())
                    .eq(SeatAssessment::getAssessMonth, monthStr));
            SeatAssessment record = exists == null ? new SeatAssessment() : exists;
            record.setUserId(bind.getUserId());
            record.setRoomId(bind.getRoomId());
            record.setActualSeatId(bind.getId());
            record.setAssessMonth(monthStr);
            record.setRequiredDays(required);
            record.setCheckinDays(checkInDays);
            record.setCheckoutDays(checkOutDays);
            record.setQualified(ok ? 1 : 0);
            record.setRemark(ok ? "考核合格" : String.format("签到且签退 %d 天，低于 %d 天", bothDays, required));

            boolean cancelThis = false;
            if (!ok && Constants.BIND_ACTIVE.equals(bind.getStatus()) && !dryRun) {
                bind.setStatus(Constants.BIND_CANCELLED);
                bind.setCancelTime(LocalDateTime.now());
                bind.setCancelReason(String.format("系统考核：%s 签到且签退 %d 天，不足 %d 天，自动取消座位", monthStr, bothDays, required));
                updateById(bind);
                cancelThis = true;
                cancelled++;
                log.warn("月度考核取消固定座位: 用户 {} 座位 {} 原因: {}", bind.getUserId(), bind.getSeatNo(), bind.getCancelReason());
            }
            record.setCancelledBinding(cancelThis ? 1 : (ok ? 0 : 0));
            if (exists == null) {
                assessmentMapper.insert(record);
            } else {
                assessmentMapper.updateById(record);
            }
        }
        summary.setTotal(total);
        summary.setQualified(qualified);
        summary.setUnqualified(unqualified);
        summary.setCancelled(cancelled);
        log.info("长期座位月度考核完成 {}：参与 {} 人，合格 {} 人，不合格 {} 人，取消 {} 人",
                monthStr, total, qualified, unqualified, cancelled);
        return summary;
    }

    /**
     * 每月 1 号零点：开放本月长期固定座位
     */
    @Transactional(rollbackFor = Exception.class)
    public void openNewMonth() {
        configService.updateConfig(Constants.CFG_LONG_ACTIVE_MONTH, String.valueOf(LocalDate.now().getMonthValue()));
        log.info("长期自习室固定座位已开放，当前月份：{}", LocalDate.now().getMonthValue());
    }

    /**
     * 考核记录分页
     */
    public Map<String, Object> assessmentPage(Map<String, Object> query) {
        long total = assessmentMapper.count(query);
        List<SeatAssessment> records = total == 0 ? new ArrayList<>() : assessmentMapper.selectPage(query);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("records", records);
        return result;
    }

    /**
     * 个人本月签到日历
     */
    public Map<String, Object> attendanceCalendar(Long userId, String monthStr) {
        YearMonth month = monthStr == null || monthStr.isEmpty() ? YearMonth.now() : YearMonth.parse(monthStr);
        List<String> checkInDates = reservationMapper.selectMonthCheckInDates(userId, month.atDay(1), month.atEndOfMonth());
        Map<String, Object> stat = reservationMapper.selectMonthAttendance(userId, month.atDay(1), month.atEndOfMonth());
        Map<String, Object> result = new HashMap<>();
        result.put("month", month.format(MONTH_FMT));
        result.put("checkInDates", checkInDates);
        result.put("checkInDays", toInt(stat, "checkInDays"));
        result.put("checkOutDays", toInt(stat, "checkOutDays"));
        result.put("bothDays", toInt(stat, "bothDays"));
        result.put("requiredDays", getRequiredDays());
        LongTermSeat bind = baseMapper.selectActiveByUser(userId);
        result.put("binding", bind);
        return result;
    }

    private int toInt(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object v = map.get(key);
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return (int) Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}