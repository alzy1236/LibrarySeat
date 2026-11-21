package com.library.seat.dto;

import lombok.Data;

/**
 * 短信验证码重置密码请求
 */
@Data
public class ResetPasswordDTO {

    private String phone;

    private String code;

    private String newPassword;
}