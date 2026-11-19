package com.library.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期固定座位申请 / 绑定记录
 *
 * <p>状态流转：</p>
 * <pre>
 *   学生提交申请 ──> PENDING(待审核) ──管理员通过──> ACTIVE(使用中)
 *                        │                              │
 *                        ├──管理员拒绝──> REJECTED        ├──学生放弃 / 管理员释放──> CANCELLED
 *                        └──学生撤回────> CANCELLED       └──月度考核不合格──────> CANCELLED
 * </pre>
 */
@Data
@TableName("long_term_seat")
public class LongTermSeat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roomId;

    private Long seatId;

    /** PENDING 待审核 / ACTIVE 使用中 / REJECTED 未通过 / CANCELLED 已取消 */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bindTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;

    private String cancelReason;

    /** 管理员审核意见 */
    private String reviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

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
    private String seatNo;

    @TableField(exist = false)
    private String phone;

    /** 本月已签到天数 */
    @TableField(exist = false)
    private Integer monthCheckInDays;

    /** 本月已签退天数 */
    @TableField(exist = false)
    private Integer monthCheckOutDays;

    @TableField(exist = false)
    private String statusName;

    public String getStatusName() {
        switch (status == null ? "" : status) {
            case "PENDING":
                return "待审核";
            case "ACTIVE":
                return "使用中";
            case "REJECTED":
                return "未通过";
            case "CANCELLED":
                return "已取消";
            default:
                return status;
        }
    }
}