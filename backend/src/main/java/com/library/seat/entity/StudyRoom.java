package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自习室
 */
@Data
@TableName("study_room")
public class StudyRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** LONG 长期 / SHORT 短期 */
    private String roomType;

    private String location;

    private Integer capacity;

    /** HH:mm */
    private String openStart;

    private String openEnd;

    private String description;

    /** 1 开放 0 停用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ---------------- 统计字段（不落库） ----------------

    /** 今日已预约座位数 */
    @TableField(exist = false)
    private Long reservedCount;

    /** 今日已签到人数（在座） */
    @TableField(exist = false)
    private Long seatedCount;

    /** 今日可预约余量 */
    @TableField(exist = false)
    private Long availableCount;

    @TableField(exist = false)
    private String roomTypeName;

    public String getRoomTypeName() {
        return "LONG".equals(roomType) ? "长期" : "短期";
    }
}