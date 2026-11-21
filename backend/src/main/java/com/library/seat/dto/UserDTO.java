package com.library.seat.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 用户新增 / 修改参数（管理员维护学生信息）
 */
@Data
public class UserDTO {

    private Long id;

    @NotBlank(message = "账号不能为空")
    @Size(max = 32, message = "账号长度不能超过 32")
    private String username;

    /** 新增时必填，修改时为空表示不修改密码 */
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String role;

    private String studentNo;

    private String college;

    private String major;

    private String phone;

    private Integer status;

    /** 注册时的短信验证码（自助注册必填） */
    private String smsCode;
}