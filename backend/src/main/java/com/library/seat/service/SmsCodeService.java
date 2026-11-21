package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.library.seat.common.BusinessException;
import com.library.seat.entity.SmsCode;
import com.library.seat.entity.SysUser;
import com.library.seat.mapper.SmsCodeMapper;
import com.library.seat.mapper.SysUserMapper;
import com.library.seat.service.sms.AliyunSmsSender;
import com.library.seat.service.sms.MockSmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 短信验证码服务：发送、校验、重置密码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    public static final String SCENE_REGISTER = "REGISTER";
    public static final String SCENE_RESET = "RESET";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsCodeMapper smsCodeMapper;
    private final SysUserMapper userMapper;
    private final SysConfigService configService;
    private final PasswordEncoder passwordEncoder;
    private final MockSmsSender mockSender;
    private final AliyunSmsSender aliyunSender;

    /* ==================== 发送 ==================== */

    /**
     * 发送验证码
     *
     * @return 演示模式下会返回验证码本身（供前端直接展示），正式短信不返回
     */
    public Map<String, Object> sendCode(String phone, String scene) {
        validatePhone(phone);
        String normalizedScene = normalizeScene(scene);

        if (SCENE_RESET.equals(normalizedScene) && findUserByPhone(phone) == null) {
            throw new BusinessException("该手机号未绑定任何账号");
        }

        int resendSeconds = intConfig("sms.resend.seconds", 60);
        int dailyLimit = intConfig("sms.daily.limit", 10);
        int expireMinutes = intConfig("sms.code.expire.minutes", 5);

        LocalDateTime now = LocalDateTime.now();

        // 60 秒内不能重复发送
        SmsCode last = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, normalizedScene)
                .orderByDesc(SmsCode::getId)
                .last("LIMIT 1"));
        if (last != null && last.getCreateTime() != null
                && last.getCreateTime().plusSeconds(resendSeconds).isAfter(now)) {
            long wait = resendSeconds - java.time.Duration.between(last.getCreateTime(), now).getSeconds();
            throw new BusinessException("请 " + Math.max(wait, 1) + " 秒后再重新获取验证码");
        }

        // 当日发送次数限制
        long todayCount = smsCodeMapper.selectCount(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .ge(SmsCode::getCreateTime, LocalDate.now().atStartOfDay()));
        if (todayCount >= dailyLimit) {
            throw new BusinessException("今日验证码发送次数已达上限（" + dailyLimit + " 次），请明天再试");
        }

        String code = String.format("%04d", RANDOM.nextInt(10000));

        SmsCode record = new SmsCode();
        record.setPhone(phone);
        record.setCode(code);
        record.setScene(normalizedScene);
        record.setExpireAt(now.plusMinutes(expireMinutes));
        record.setUsed(0);
        record.setAttempts(0);
        record.setCreateTime(now);
        smsCodeMapper.insert(record);

        boolean mock = mockSender.isMock();
        boolean sent = mock ? mockSender.send(phone, code, normalizedScene)
                : aliyunSender.send(phone, code, normalizedScene);
        if (!sent) {
            throw new BusinessException("短信发送失败，请稍后重试或联系管理员");
        }

        Map<String, Object> data = new HashMap<>(4);
        data.put("expireMinutes", expireMinutes);
        data.put("resendSeconds", resendSeconds);
        data.put("mock", mock);
        if (mock) {
            // 演示模式：把验证码返回前端，页面上直接提示，方便演示与答辩
            data.put("code", code);
            data.put("tip", "演示模式：验证码为 " + code + "（未真实发送短信）");
        } else {
            data.put("tip", "验证码已发送，请查收短信");
        }
        log.info("验证码已生成：phone={} scene={} mock={}", phone, normalizedScene, mock);
        return data;
    }

    /* ==================== 校验 ==================== */

    /**
     * 校验验证码（成功即标记为已使用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void verifyCode(String phone, String code, String scene) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("请输入验证码");
        }
        String normalizedScene = normalizeScene(scene);

        SmsCode record = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, normalizedScene)
                .eq(SmsCode::getUsed, 0)
                .orderByDesc(SmsCode::getId)
                .last("LIMIT 1"));

        if (record == null) {
            throw new BusinessException("请先获取验证码");
        }
        if (record.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (record.getAttempts() != null && record.getAttempts() >= 5) {
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        if (!record.getCode().equals(code.trim())) {
            smsCodeMapper.update(null, new LambdaUpdateWrapper<SmsCode>()
                    .eq(SmsCode::getId, record.getId())
                    .setSql("attempts = attempts + 1"));
            throw new BusinessException("验证码不正确");
        }

        smsCodeMapper.update(null, new LambdaUpdateWrapper<SmsCode>()
                .eq(SmsCode::getId, record.getId())
                .set(SmsCode::getUsed, 1));
    }

    /* ==================== 重置密码 ==================== */

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String phone, String code, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new BusinessException("新密码至少 6 位");
        }
        validatePhone(phone);
        verifyCode(phone, code, SCENE_RESET);

        SysUser user = findUserByPhone(phone);
        if (user == null) {
            throw new BusinessException("该手机号未绑定任何账号");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户 {} 通过短信验证码重置了密码", user.getUsername());
    }

    /* ==================== 工具 ==================== */

    public SysUser findUserByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone.trim())
                .last("LIMIT 1"));
    }

    public boolean existsPhone(String phone) {
        return findUserByPhone(phone) != null;
    }

    public boolean registerVerifyRequired() {
        return "1".equals(configService.getValue("sms.verify.register", "1"));
    }

    private void validatePhone(String phone) {
        if (!StringUtils.hasText(phone) || !phone.trim().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("请输入正确的 11 位手机号");
        }
    }

    private String normalizeScene(String scene) {
        return SCENE_RESET.equalsIgnoreCase(scene) ? SCENE_RESET : SCENE_REGISTER;
    }

    private int intConfig(String key, int def) {
        try {
            return Integer.parseInt(configService.getValue(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }
}