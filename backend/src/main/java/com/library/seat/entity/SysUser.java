package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（学生 / 管理员）
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt），序列化时不返回给前端 */
    @JsonIgnore
    private String password;

    @JsonProperty("realName")
    private String realName;

    /** ADMIN / STUDENT */
    private String role;

    private String studentNo;

    private String college;

    private String major;

    private String phone;

    /** 1 正常 0 禁用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 仅用于查询展示，不落库 */
    @TableField(exist = false)
    private String roleName;

    public String getRoleName() {
        return "ADMIN".equals(role) ? "管理员" : "学生";
    }
}