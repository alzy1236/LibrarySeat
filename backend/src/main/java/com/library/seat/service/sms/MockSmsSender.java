package com.library.seat.service.sms;

import com.library.seat.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示模式短信发送器
 * <p>
 * 不调用任何外部服务，只把验证码写入日志并回传给前端展示。
 * 适合毕业设计演示 / 没有购买短信服务的场景。
 * 配置 sys_config: sms.provider = mock 时启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockSmsSender implements SmsSender {

    private final SysConfigService configService;

    @Override
    public boolean send(String phone, String code, String scene) {
        String sceneName = "RESET".equals(scene) ? "重置密码" : "注册";
        log.info("【模拟短信】{} -> 手机号 {} 验证码 {}（{}）", phone, phone, code, sceneName);
        return true;
    }

    @Override
    public boolean isMock() {
        String provider = configService.getValue("sms.provider", "mock");
        return !"aliyun".equalsIgnoreCase(provider);
    }
}