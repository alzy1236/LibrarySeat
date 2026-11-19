package com.library.seat.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录参数
 */
@Data
public class LoginDTO {

    @NotBlank(message = "请输入账号")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;

    /** 登录角色提示（可空）：前端选择 ADMIN / STUDENT，用于账号角色不匹配时给出友好提示 */
    private String role;
}