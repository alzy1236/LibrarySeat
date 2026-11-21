package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信验证码
 */
@Data
@TableName("sms_code")
public class SmsCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    /** 4 位数字验证码 */
    private String code;

    /** REGISTER 注册 / RESET 重置密码 */
    private String scene;

    private LocalDateTime expireAt;

    /** 1 已使用 */
    private Integer used;

    /** 已校验次数（防止暴力猜解） */
    private Integer attempts;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}