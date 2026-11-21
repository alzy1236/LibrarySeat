package com.library.seat.util;

/**
 * 地理计算工具
 * <p>
 * 输入输出均为 WGS-84 十进制度，与浏览器 Geolocation API 一致。
 */
public final class GeoUtil {

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS = 6371000d;

    private GeoUtil() {
    }

    /**
     * Haversine 公式计算两点球面距离（米）
     */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * 距离的友好展示：<1km 显示米，否则显示公里
     */
    public static String humanize(double meters) {
        if (meters < 1000) {
            return Math.round(meters) + " 米";
        }
        return String.format("%.2f 公里", meters / 1000d);
    }

    /**
     * 经纬度合法性校验
     */
    public static boolean isValid(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }
}