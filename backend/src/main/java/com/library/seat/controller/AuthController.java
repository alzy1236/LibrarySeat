package com.library.seat.controller;

import com.library.seat.common.Constants;
import com.library.seat.common.Result;
import com.library.seat.common.UserContext;
import com.library.seat.dto.LoginDTO;
import com.library.seat.dto.PasswordDTO;
import com.library.seat.dto.ResetPasswordDTO;
import com.library.seat.dto.SmsRequest;
import com.library.seat.dto.UserDTO;
import com.library.seat.entity.SysUser;
import com.library.seat.service.SmsCodeService;
import com.library.seat.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final SmsCodeService smsCodeService;

    /**
     * 账号密码登录：根据账号类型返回跳转路径（管理员 -> 管理端，学生 -> 学生端）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginDTO dto) {
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
     * <p>
     * 开启短信校验（sms.verify.register=1）时，必须携带手机号 + 验证码。
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserDTO dto) {
        dto.setRole(Constants.ROLE_STUDENT);
        dto.setStatus(1);

        if (smsCodeService.registerVerifyRequired()) {
            if (!org.springframework.util.StringUtils.hasText(dto.getPhone())) {
                throw new com.library.seat.common.BusinessException("请填写手机号");
            }
            if (smsCodeService.existsPhone(dto.getPhone())) {
                throw new com.library.seat.common.BusinessException("该手机号已被注册");
            }
            smsCodeService.verifyCode(dto.getPhone(), dto.getSmsCode(), SmsCodeService.SCENE_REGISTER);
        }

        userService.saveUser(dto);
        return Result.success("注册成功，请登录", null);
    }

    /**
     * 发送短信验证码
     * <p>
     * scene=REGISTER 注册；scene=RESET 重置密码。
     * 演示模式（sms.provider=mock）下会把验证码放在返回数据里，前端直接提示。
     */
    @PostMapping("/sms-code")
    public Result<Map<String, Object>> sendSmsCode(@RequestBody SmsRequest dto) {
        Map<String, Object> data = smsCodeService.sendCode(dto.getPhone(), dto.getScene());
        return Result.success(String.valueOf(data.get("tip")), data);
    }

    /**
     * 通过手机验证码重置密码（忘记密码自助找回，无需管理员）
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody ResetPasswordDTO dto) {
        smsCodeService.resetPassword(dto.getPhone(), dto.getCode(), dto.getNewPassword());
        return Result.success("密码已重置，请使用新密码登录", null);
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