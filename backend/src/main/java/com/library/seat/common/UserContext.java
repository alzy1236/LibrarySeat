package com.library.seat.common;

import lombok.Data;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    public static String getRole() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static boolean isAdmin() {
        return Constants.ROLE_ADMIN.equals(getRole());
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    public static class LoginUser {
        private Long userId;
        private String username;
        private String realName;
        private String role;
    }
}