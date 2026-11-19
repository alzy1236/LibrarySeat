package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月度考核记录
 */
@Data
@TableName("seat_assessment")
public class SeatAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roomId;

    private Long actualSeatId;

    /** yyyy-MM */
    private String assessMonth;

    private Integer requiredDays;

    private Integer checkinDays;

    private Integer checkoutDays;

    /** 1 合格 0 不合格 */
    private Integer qualified;

    /** 是否因此取消座位 */
    private Integer cancelledBinding;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ---------------- 关联展示字段 ----------------

    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String studentNo;

    @TableField(exist = false)
    private String roomName;

    @TableField(exist = false)
    private String seatNo;
}