package com.library.seat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.common.Result;
import com.library.seat.dto.PageResult;
import com.library.seat.dto.UserDTO;
import com.library.seat.entity.SysConfig;
import com.library.seat.entity.SysNotice;
import com.library.seat.entity.SysUser;
import com.library.seat.service.SysConfigService;
import com.library.seat.service.SysNoticeService;
import com.library.seat.service.SysUserService;
import com.library.seat.common.UserContext;
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
 * 管理端：用户管理、公告管理、系统参数
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserService userService;
    private final SysNoticeService noticeService;
    private final SysConfigService configService;

    // ========================= 用户管理 =========================

    @GetMapping("/users")
    public Result<PageResult<SysUser>> users(@RequestParam(defaultValue = "1") Long pageNum,
                                             @RequestParam(defaultValue = "10") Long pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String role,
                                             @RequestParam(required = false) Integer status) {
        Page<SysUser> page = userService.pageQuery(new Page<>(pageNum, pageSize), keyword, role, status);
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/users/{id}")
    public Result<SysUser> user(@PathVariable Long id) {
        return Result.success(userService.getByIdRequired(id));
    }

    @PostMapping("/users")
    public Result<Void> addUser(@Validated @RequestBody UserDTO dto) {
        userService.saveUser(dto);
        return Result.success("学生账号新增成功", null);
    }

    @PutMapping("/users")
    public Result<Void> updateUser(@RequestBody UserDTO dto) {
        userService.updateUser(dto);
        return Result.success("用户信息已更新", null);
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.removeUser(id);
        return Result.success("账号已删除", null);
    }

    @PostMapping("/users/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success("密码已重置为 123456", null);
    }

    @PostMapping("/users/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        SysUser user = userService.getByIdRequired(id);
        user.setStatus(status);
        userService.updateById(user);
        return Result.success(status == 1 ? "账号已启用" : "账号已禁用", null);
    }

    // ========================= 公告管理 =========================

    @GetMapping("/notices")
    public Result<List<SysNotice>> notices(@RequestParam(required = false) String keyword) {
        return Result.success(noticeService.listAll(keyword));
    }

    @PostMapping("/notices")
    public Result<Void> addNotice(@RequestBody SysNotice notice) {
        noticeService.saveNotice(notice, UserContext.getUserId());
        return Result.success("公告发布成功", null);
    }

    @PutMapping("/notices")
    public Result<Void> updateNotice(@RequestBody SysNotice notice) {
        noticeService.updateNotice(notice);
        return Result.success("公告已更新", null);
    }

    @DeleteMapping("/notices/{id}")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        noticeService.removeById(id);
        return Result.success("公告已删除", null);
    }

    // ========================= 系统参数 =========================

    @GetMapping("/configs")
    public Result<List<SysConfig>> configs() {
        return Result.success(configService.all());
    }

    @PutMapping("/configs")
    public Result<Void> updateConfigs(@RequestBody Map<String, String> values) {
        configService.updateBatch(values);
        return Result.success("系统参数已保存", null);
    }

    /**
     * 首页系统状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> map = new HashMap<>();
        map.put("studentCount", userService.countStudents());
        map.put("configs", configService.allAsMap());
        return Result.success(map);
    }
}