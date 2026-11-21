package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.seat.common.BusinessException;
import com.library.seat.common.Constants;
import com.library.seat.entity.CheckinLocation;
import com.library.seat.mapper.CheckinLocationMapper;
import com.library.seat.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到地点（地理围栏）服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinLocationService {

    private final CheckinLocationMapper locationMapper;
    private final SysConfigService configService;

    /* ==================== 管理端 ==================== */

    public List<CheckinLocation> list(String keyword) {
        return locationMapper.selectWithRoom(keyword);
    }

    @Transactional(rollbackFor = Exception.class)
    public CheckinLocation save(CheckinLocation dto) {
        validate(dto);
        if (dto.getRadius() == null || dto.getRadius() < 10) {
            dto.setRadius(100);
        }
        dto.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        dto.setCreateTime(LocalDateTime.now());
        dto.setUpdateTime(LocalDateTime.now());
        locationMapper.insert(dto);
        log.info("新增签到点 {} ({}, {}) r={}m", dto.getName(), dto.getLatitude(), dto.getLongitude(), dto.getRadius());
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public CheckinLocation update(CheckinLocation dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少签到点 ID");
        }
        CheckinLocation exists = locationMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BusinessException("签到点不存在");
        }
        validate(dto);
        dto.setUpdateTime(LocalDateTime.now());
        locationMapper.updateById(dto);
        return locationMapper.selectById(dto.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (locationMapper.selectById(id) == null) {
            throw new BusinessException("签到点不存在");
        }
        locationMapper.deleteById(id);
    }

    private void validate(CheckinLocation dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new BusinessException("请填写签到点名称");
        }
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            throw new BusinessException("请在地图上选择签到点位置");
        }
        double lat = dto.getLatitude().doubleValue();
        double lng = dto.getLongitude().doubleValue();
        if (!GeoUtil.isValid(lat, lng)) {
            throw new BusinessException("经纬度不合理，请重新在地图上取点");
        }
        if (dto.getRadius() != null && (dto.getRadius() < 10 || dto.getRadius() > 2000)) {
            throw new BusinessException("允许半径需在 10 ~ 2000 米之间");
        }
    }

    /* ==================== 学生端 ==================== */

    /**
     * 是否启用定位校验
     */
    public boolean isEnabled() {
        return "1".equals(configService.getValue(Constants.CFG_CHECKIN_LOCATION_ENABLED, "1"));
    }

    /**
     * 查询对某自习室生效的签到点（可能为 null）
     */
    public CheckinLocation effective(Long roomId) {
        if (roomId == null) {
            return null;
        }
        return locationMapper.selectEffective(roomId);
    }

    /**
     * 签到 / 签退前的位置校验
     * <p>
     * 规则：
     * 1. 总开关关闭 -> 不校验（兼容原有流程）
     * 2. 未配置签到点 -> 放行并打日志，避免把所有人挡在门外
     * 3. 前端未提供坐标 -> 拒绝，提示需要开启定位
     * 4. 超出半径 -> 拒绝，并提示实际距离
     *
     * @return 校验通过时返回距离（米），未校验时返回 -1
     */
    public double assertInRange(Long roomId, Double lat, Double lng, String action) {
        if (!isEnabled()) {
            return -1;
        }
        CheckinLocation loc = effective(roomId);
        if (loc == null) {
            log.warn("房间 {} 未配置签到点，{} 未做位置校验", roomId, action);
            return -1;
        }
        if (lat == null || lng == null) {
            throw new BusinessException(120, "该操作需要获取您的位置，请允许浏览器定位后重试");
        }
        if (!GeoUtil.isValid(lat, lng)) {
            throw new BusinessException(121, "定位数据异常，请重新获取位置");
        }

        double distance = GeoUtil.distanceMeters(
                loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue(), lat, lng);
        int radius = loc.getRadius() == null ? 100 : loc.getRadius();
        if (distance > radius) {
            throw new BusinessException(122, String.format(
                    "您当前距离「%s」约 %s，超出允许范围（%d 米），请到达签到点后再%s",
                    loc.getName(), GeoUtil.humanize(distance), radius, action));
        }
        log.info("{} 位置校验通过：距「{}」{} 米（限制 {} 米）",
                action, loc.getName(), Math.round(distance), radius);
        return distance;
    }

    /**
     * 清理：删除某自习室的全部签到点（删除自习室时级联已由外键处理，这里留作备用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByRoom(Long roomId) {
        locationMapper.delete(new LambdaQueryWrapper<CheckinLocation>()
                .eq(CheckinLocation::getRoomId, roomId));
    }

    /**
     * 供 DTO 转换：BigDecimal 便捷构造
     */
    public static BigDecimal of(double v) {
        return BigDecimal.valueOf(v);
    }
}