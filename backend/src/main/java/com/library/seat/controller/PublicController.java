package com.library.seat.controller;

import com.library.seat.common.Constants;
import com.library.seat.common.Result;
import com.library.seat.common.UserContext;
import com.library.seat.entity.SysNotice;
import com.library.seat.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公共接口：公告、规则（登录页 / 学生端均可访问）
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final SysNoticeService noticeService;

    @GetMapping("/notices")
    public Result<List<SysNotice>> notices(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(noticeService.listPublished(limit));
    }

    @GetMapping("/notices/{id}")
    public Result<SysNotice> notice(@PathVariable Long id) {
        return Result.success(noticeService.getById(id));
    }

    /**
     * 自习室使用规则文本
     */
    @GetMapping("/rules")
    public Result<List<String>> rules() {
        return Result.success(java.util.Arrays.asList(
                "1. 自习楼分为 1—5 层：1、2、5 层为普通座位层，3、4 层为固定座位层。",
                "2. 每位学生每天只能预约一个座位，预约后请在自习楼内使用。",
                "3. 固定座位层需先提交申请，经管理员审核通过后方可使用本层座位；每日 08:30 前签到，21:00 - 24:00 签退。",
                "4. 固定座位层每月有效出勤（同一天既签到又签退）不足 20 天，系统将于次月 1 日自动取消该学生的固定座位。",
                "5. 普通座位层禁止存放个人物品，每天 24:00 前必须带走物品并离场，超时将记录违规。",
                "6. 座位资源有限，预约后无法到场的请提前取消，避免影响他人使用。",
                "7. 签到 / 签退时间以系统服务器时间为准，页面右上角实时时钟可核对时间。"
        ));
    }

    /**
     * 当前用户（匿名返回 401 由拦截器处理，未登录时前端不会调用）
     */
    @GetMapping("/me")
    public Result<Long> me() {
        return Result.success(UserContext.getUserId());
    }
}