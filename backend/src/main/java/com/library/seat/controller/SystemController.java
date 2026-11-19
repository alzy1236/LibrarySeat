package com.library.seat.controller;

import com.library.seat.common.Constants;
import com.library.seat.common.Result;
import com.library.seat.dto.ClockStatus;
import com.library.seat.service.ClockService;
import com.library.seat.service.SysConfigService;
import com.library.seat.websocket.TimeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统接口（免登录）：服务器时间、系统信息
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final ClockService clockService;
    private final SysConfigService configService;
    private final TimeWebSocketHandler webSocketHandler;

    /**
     * 服务器时间（登录页即可调用，用于全站统一时间）
     */
    @GetMapping("/server-time")
    public Result<Map<String, Object>> serverTime() {
        return Result.success(clockService.serverFrame());
    }

    /**
     * 签到 / 签退时间规则（实时工具的窗口配置）
     */
    @GetMapping("/clock")
    public Result<ClockStatus> clock() {
        return Result.success(clockService.build(
                configService.getValue(Constants.CFG_CHECKIN_EARLY_OPEN, "07:00"),
                configService.getValue(Constants.CFG_CHECKIN_DEADLINE, "08:30"),
                configService.getValue(Constants.CFG_CHECKOUT_START, "21:00"),
                configService.getValue(Constants.CFG_CHECKOUT_END, "23:59"),
                null));
    }

    /**
     * 系统信息（管理端「系统设置」页展示运行环境）
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "自习室座位预约管理系统");
        map.put("version", "1.0.0");
        map.put("serverTime", LocalDateTime.now().toString());
        map.put("timeZone", java.util.TimeZone.getDefault().getID());
        map.put("wsClients", webSocketHandler.onlineCount());
        map.put("javaVersion", System.getProperty("java.version"));
        map.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        map.put("uptimeSeconds", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        map.put("startTime", LocalDateTime.now()
                .minusSeconds(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .withNano(0).toString().replace('T', ' '));
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long totalMb = rt.totalMemory() / 1024 / 1024;
        map.put("memory", usedMb + " / " + totalMb + " MB");
        map.put("rules", Map.of(
                "checkInDeadline", configService.getValue(Constants.CFG_CHECKIN_DEADLINE, "08:30"),
                "checkOutStart", configService.getValue(Constants.CFG_CHECKOUT_START, "21:00"),
                "checkOutEnd", configService.getValue(Constants.CFG_CHECKOUT_END, "23:59"),
                "shortClear", configService.getValue(Constants.CFG_SHORT_CLEAR_DEADLINE, "24:00"),
                "longMinDays", configService.getValue(Constants.CFG_LONG_MIN_DAYS, "20")));
        return Result.success(map);
    }
}