package com.library.seat.dto;

import lombok.Data;

/**
 * 发送短信验证码请求
 */
@Data
public class SmsRequest {

    private String phone;

    /** REGISTER 注册 / RESET 重置密码 */
    private String scene;
}