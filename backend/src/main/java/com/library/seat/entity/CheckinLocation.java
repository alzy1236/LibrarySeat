package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到地点（地理围栏）
 * <p>
 * latitude / longitude 为 WGS-84 坐标，与浏览器 navigator.geolocation 一致。
 * 不要用高德、腾讯地图取点（GCJ-02 偏移），否则会出现几十到几百米的误差。
 */
@Data
@TableName("checkin_location")
public class CheckinLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 签到点名称，如"一层大厅签到处" */
    private String name;

    /** 所属自习室，null 表示全楼通用 */
    private Long roomId;

    /** 纬度 WGS-84 */
    private BigDecimal latitude;

    /** 经度 WGS-84 */
    private BigDecimal longitude;

    /** 允许签到的半径（米） */
    private Integer radius;

    /** 1 启用 0 停用 */
    private Integer enabled;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 关联自习室名称（非数据库字段，列表展示用） */
    @TableField(exist = false)
    private String roomName;
}