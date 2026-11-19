package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 座位
 */
@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String seatNo;

    private Integer rowNo;

    private Integer colNo;

    /** 1 带电源 */
    private Integer hasPower;

    /** 1 可用 0 停用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ---------------- 座位图展示字段（不落库） ----------------

    /** 座位状态：AVAILABLE 空闲 / RESERVED 已预约 / SEATED 使用中 / DISABLED 停用 / LOCKED 长期固定 / OVERDUE 超时未清场 */
    @TableField(exist = false)
    private String seatState;

    /** 占用人姓名（管理员可见） */
    @TableField(exist = false)
    private String occupantName;

    @TableField(exist = false)
    private Long occupantId;

    /** 该座位长期绑定人姓名 */
    @TableField(exist = false)
    private String longTermOwner;
}