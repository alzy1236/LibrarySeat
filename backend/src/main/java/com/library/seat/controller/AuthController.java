package com.library.seat.controller;

import com.library.seat.common.Constants;
import com.library.seat.common.Result;
import com.library.seat.common.UserContext;
import com.library.seat.dto.LoginDTO;
import com.library.seat.dto.PasswordDTO;
import com.library.seat.dto.UserDTO;
import com.library.seat.entity.SysUser;
import com.library.seat.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口：登录 / 退出 / 当前用户 / 修改密码 / 注册
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;

    /**
     * 账号密码登录：根据账号类型返回跳转路径（管理员 -> 管理端，学生 -> 学生端）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /**
     * 账号密码登录（表单编码）
     * <p>
     * 前端用它登录：application/x-www-form-urlencoded 属于 CORS 简单请求，
     * 浏览器不会先发预检（OPTIONS），跨域部署时少一个网络往返（约 1-2 秒）。
     */
    @PostMapping(value = "/login", consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Result<Map<String, Object>> loginForm(@RequestParam String username,
                                                 @RequestParam String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return Result.success("登录成功", userService.login(dto));
    }

    /**
     * 退出登录（JWT 无状态，前端清除 token 即可）
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success("已退出登录", null);
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/info")
    public Result<SysUser> info() {
        SysUser user = userService.getByIdRequired(UserContext.getUserId());
        return Result.success(user);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody PasswordDTO dto) {
        userService.changePassword(UserContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码修改成功，请重新登录", null);
    }

    /**
     * 学生注册（自助注册默认为学生角色）
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserDTO dto) {
        dto.setRole(Constants.ROLE_STUDENT);
        dto.setStatus(1);
        userService.saveUser(dto);
        return Result.success("注册成功，请登录", null);
    }

    /**
     * 登录页健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> map = new HashMap<>(2);
        map.put("status", "UP");
        map.put("name", "自习室座位预约管理系统");
        return Result.success(map);
    }
}