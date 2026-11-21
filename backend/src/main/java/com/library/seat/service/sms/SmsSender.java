package com.library.seat.service.sms;

/**
 * 短信发送器
 * <p>
 * 实现类：
 * - {@link MockSmsSender}   演示模式，不发真实短信，验证码直接返回给前端展示
 * - {@link AliyunSmsSender} 阿里云短信（需配置 AccessKey / 签名 / 模板）
 */
public interface SmsSender {

    /**
     * 发送验证码
     *
     * @param phone 手机号
     * @param code  验证码
     * @param scene REGISTER / RESET
     * @return 是否成功
     */
    boolean send(String phone, String code, String scene);

    /**
     * 是否为演示模式（演示模式下可把验证码回传给前端）
     */
    boolean isMock();
}