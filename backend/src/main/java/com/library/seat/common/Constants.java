package com.library.seat.common;

/**
 * 系统常量
 */
public final class Constants {

    private Constants() {
    }

    /** 角色 */
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STUDENT = "STUDENT";

    /** 自习室类型 */
    public static final String ROOM_TYPE_LONG = "LONG";
    public static final String ROOM_TYPE_SHORT = "SHORT";

    /** 预约状态 */
    public static final String RES_WAITING = "WAITING";     // 待签到
    public static final String RES_SEATED = "SEATED";       // 已签到
    public static final String RES_LEFT = "LEFT";           // 已签退离开
    public static final String RES_CANCELLED = "CANCELLED"; // 已取消
    public static final String RES_TIMEOUT = "TIMEOUT";     // 超时未签到失效

    /** 长期固定座位状态 */
    public static final String BIND_PENDING = "PENDING";
    public static final String BIND_ACTIVE = "ACTIVE";
    public static final String BIND_REJECTED = "REJECTED";
    public static final String BIND_CANCELLED = "CANCELLED";

    /** 系统参数 key */
    public static final String CFG_CHECKIN_DEADLINE = "checkin.deadline";
    public static final String CFG_CHECKIN_EARLY_OPEN = "checkin.early.open";
    public static final String CFG_CHECKOUT_START = "checkout.start";
    public static final String CFG_CHECKOUT_END = "checkout.end";
    public static final String CFG_SHORT_CLEAR_DEADLINE = "short.clear.deadline";
    public static final String CFG_LONG_MIN_DAYS = "long.min.days";
    public static final String CFG_LONG_ACTIVE_MONTH = "long.active.month";
    /** 是否启用签到定位校验（1 启用 / 0 关闭） */
    public static final String CFG_CHECKIN_LOCATION_ENABLED = "checkin.location.enabled";

    /** 请求上下文中的登录用户 ID */
    public static final String REQ_USER_ID = "LOGIN_USER_ID";
    public static final String REQ_USER_ROLE = "LOGIN_USER_ROLE";
}