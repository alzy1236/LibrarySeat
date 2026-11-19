package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.SeatReservation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.mapper.LongTermSeatMapper;
import com.library.seat.mapper.SeatReservationMapper;
import com.library.seat.mapper.StudyRoomMapper;
import com.library.seat.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计服务（预约数量、座位使用率、趋势图表）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private final SeatReservationMapper reservationMapper;
    private final StudyRoomMapper roomMapper;
    private final SysUserMapper userMapper;
    private final LongTermSeatMapper longTermSeatMapper;

    /**
     * 管理端首页概览：今日预约学生数、在座人数、座位使用率等
     */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        List<StudyRoom> rooms = roomMapper.selectRoomStatistics(null, null, null, today);

        long totalSeats = 0, reserved = 0, seated = 0, waiting = 0;
        long longSeats = 0, longSeated = 0, shortSeats = 0, shortSeated = 0;
        for (StudyRoom room : rooms) {
            totalSeats += room.getCapacity() == null ? 0 : room.getCapacity();
            reserved += room.getReservedCount() == null ? 0 : room.getReservedCount();
            seated += room.getSeatedCount() == null ? 0 : room.getSeatedCount();
            if ("LONG".equals(room.getRoomType())) {
                longSeats += room.getCapacity() == null ? 0 : room.getCapacity();
                longSeated += room.getSeatedCount() == null ? 0 : room.getSeatedCount();
            } else {
                shortSeats += room.getCapacity() == null ? 0 : room.getCapacity();
                shortSeated += room.getSeatedCount() == null ? 0 : room.getSeatedCount();
            }
        }
        long todayLeft = reservationMapper.selectCount(new LambdaQueryWrapper<SeatReservation>()
                .eq(SeatReservation::getReserveDate, today)
                .eq(SeatReservation::getStatus, "LEFT"));
        long todayWaiting = Math.max(0, reserved - seated);
        long todayCancelled = reservationMapper.selectCount(new LambdaQueryWrapper<SeatReservation>()
                .eq(SeatReservation::getReserveDate, today)
                .eq(SeatReservation::getStatus, "CANCELLED"));
        long todayTimeout = reservationMapper.selectCount(new LambdaQueryWrapper<SeatReservation>()
                .eq(SeatReservation::getReserveDate, today)
                .eq(SeatReservation::getStatus, "TIMEOUT"));
        long activeBinds = longTermSeatMapper.selectCount(new LambdaQueryWrapper<LongTermSeat>()
                .eq(LongTermSeat::getStatus, "ACTIVE"));
        long studentCount = userMapper.selectCount(new LambdaQueryWrapper<com.library.seat.entity.SysUser>()
                .eq(com.library.seat.entity.SysUser::getRole, "STUDENT"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("today", today.toString());
        map.put("totalSeats", totalSeats);
        map.put("todayReserved", reserved);
        map.put("todaySeated", seated);
        map.put("todayWaiting", todayWaiting);
        map.put("todayLeft", todayLeft);
        map.put("todayCancelled", todayCancelled);
        map.put("todayTimeout", todayTimeout);
        map.put("roomCount", rooms.size());
        map.put("studentCount", studentCount);
        map.put("activeLongTermBinds", activeBinds);
        map.put("usageRate", totalSeats == 0 ? 0 : Math.round(reserved * 1000.0 / totalSeats) / 10.0);
        map.put("longRoomUsage", longSeats == 0 ? 0 : Math.round(longSeated * 1000.0 / longSeats) / 10.0);
        map.put("shortRoomUsage", shortSeats == 0 ? 0 : Math.round(shortSeated * 1000.0 / shortSeats) / 10.0);
        map.put("rooms", rooms);

        // 今日预约学生明细（管理端首页“预定学生的数量”卡片下钻）
        List<SeatReservation> todayList = reservationMapper.selectReservationPage(todayQuery(0L, 200L));
        map.put("todayList", todayList);
        return map;
    }

    private Map<String, Object> todayQuery(Long offset, Long limit) {
        Map<String, Object> q = new HashMap<>();
        q.put("startDate", LocalDate.now());
        q.put("endDate", LocalDate.now());
        q.put("offset", offset);
        q.put("limit", limit);
        return q;
    }

    /**
     * 图表数据：近 N 天趋势、自习室使用率、时段分布
     */
    public Map<String, Object> charts(String type, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(1, Math.min(days, 31)) - 1);
        String chartType = type == null ? "all" : type;

        Map<String, Object> result = new LinkedHashMap<>();
        if ("all".equals(chartType) || "trend".equals(chartType)) {
            List<Map<String, Object>> trend = reservationMapper.selectDailyTrend(start, end);
            List<String> labels = new ArrayList<>();
            List<Object> reserved = new ArrayList<>();
            List<Object> checkedIn = new ArrayList<>();
            List<Object> checkedOut = new ArrayList<>();
            for (Map<String, Object> row : trend) {
                labels.add(formatLabel(row.get("date")));
                reserved.add(row.get("reserved"));
                checkedIn.add(row.get("checkedIn"));
                checkedOut.add(row.get("checkedOut"));
            }
            Map<String, Object> trendChart = new LinkedHashMap<>();
            trendChart.put("labels", labels);
            trendChart.put("reserved", reserved);
            trendChart.put("checkedIn", checkedIn);
            trendChart.put("checkedOut", checkedOut);
            result.put("trend", trendChart);
        }
        if ("all".equals(chartType) || "roomUsage".equals(chartType)) {
            List<Map<String, Object>> usage = reservationMapper.selectRoomUsage(LocalDate.now());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : usage) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                long capacity = toLong(row.get("capacity"));
                long seatedCnt = toLong(row.get("seated"));
                long reservedCnt = toLong(row.get("reserved"));
                item.put("seatedRate", capacity == 0 ? 0 : Math.round(seatedCnt * 1000.0 / capacity) / 10.0);
                item.put("reservedRate", capacity == 0 ? 0 : Math.round(reservedCnt * 1000.0 / capacity) / 10.0);
                rows.add(item);
            }
            result.put("roomUsage", rows);
        }
        if ("all".equals(chartType) || "hourly".equals(chartType)) {
            List<Map<String, Object>> hourly = reservationMapper.selectHourlyCheckIn(LocalDate.now());
            List<String> hours = new ArrayList<>();
            List<Object> counts = new ArrayList<>();
            for (Map<String, Object> row : hourly) {
                hours.add(toLong(row.get("hour")) + "时");
                counts.add(row.get("cnt"));
            }
            Map<String, Object> hourlyChart = new LinkedHashMap<>();
            hourlyChart.put("labels", hours);
            hourlyChart.put("counts", counts);
            result.put("hourly", hourlyChart);
        }
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());
        return result;
    }

    /**
     * 各类状态占比（饼图）
     */
    public Map<String, Object> statusDistribution() {
        LocalDate today = LocalDate.now();
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> pie = new ArrayList<>();
        String[][] statuses = {{"WAITING", "待签到"}, {"SEATED", "在座使用"}, {"LEFT", "已离开"}, {"CANCELLED", "已取消"}, {"TIMEOUT", "超时失效"}};
        for (String[] s : statuses) {
            long cnt = reservationMapper.selectCount(new LambdaQueryWrapper<SeatReservation>()
                    .eq(SeatReservation::getReserveDate, today)
                    .eq(SeatReservation::getStatus, s[0]));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", s[1]);
            item.put("value", cnt);
            item.put("status", s[0]);
            pie.add(item);
        }
        map.put("distribution", pie);
        return map;
    }

    public Map<String, Object> roomTrend(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(1, Math.min(days, 31)) - 1);
        List<Map<String, Object>> rows = reservationMapper.selectRoomTrend(start, end);
        List<String> labels = new ArrayList<>();
        Map<String, Map<String, Object>> seriesMap = new LinkedHashMap<>();
        for (int i = 0; i < daysRange(start, end); i++) {
            LocalDate d = start.plusDays(i);
            labels.add(d.format(MM_DD));
            for (Map<String, Object> row : rows) {
                String roomName = String.valueOf(row.get("roomName"));
                seriesMap.putIfAbsent(roomName, new LinkedHashMap<>());
                seriesMap.get(roomName).put(d.format(MM_DD), row.get("checkedIn"));
            }
        }
        List<Map<String, Object>> series = new ArrayList<>();
        seriesMap.forEach((name, values) -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", name);
            List<Object> data = new ArrayList<>();
            for (String label : labels) {
                data.add(values.getOrDefault(label, 0));
            }
            s.put("data", data);
            series.add(s);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("series", series);
        return result;
    }

    private int daysRange(LocalDate start, LocalDate end) {
        return (int) (end.toEpochDay() - start.toEpochDay()) + 1;
    }

    private String formatLabel(Object date) {
        String s = String.valueOf(date);
        return s.length() >= 10 ? s.substring(5) : s;
    }

    private long toLong(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return (long) Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}