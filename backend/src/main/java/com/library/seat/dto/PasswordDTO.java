package com.library.seat.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 修改密码
 */
@Data
public class PasswordDTO {

    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}