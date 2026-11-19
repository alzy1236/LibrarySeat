package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 座位预约记录
 */
@Data
@TableName("seat_reservation")
public class SeatReservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roomId;

    private Long seatId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reserveDate;

    /** WAITING 待签到 / SEATED 已签到 / LEFT 已离开 / CANCELLED 已取消 / TIMEOUT 未签到失效 */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkInTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkOutTime;

    /** 是否超时未清场 */
    private Integer overstay;

    private String cancelReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ---------------- 关联展示字段 ----------------

    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String studentNo;

    @TableField(exist = false)
    private String college;

    @TableField(exist = false)
    private String roomName;

    @TableField(exist = false)
    private String roomType;

    @TableField(exist = false)
    private String seatNo;

    @TableField(exist = false)
    private String statusName;

    public String getStatusName() {
        switch (status == null ? "" : status) {
            case "WAITING":
                return "待签到";
            case "SEATED":
                return "使用中";
            case "LEFT":
                return "已离开";
            case "CANCELLED":
                return "已取消";
            case "TIMEOUT":
                return "超时失效";
            default:
                return status;
        }
    }
}