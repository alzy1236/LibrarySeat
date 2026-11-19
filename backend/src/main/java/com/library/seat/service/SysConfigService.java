package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.library.seat.entity.SysConfig;
import com.library.seat.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数服务（签到截止时间、签退时间、考核天数等可在管理端配置）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService extends ServiceImpl<SysConfigMapper, SysConfig> {

    public String getValue(String key, String defaultValue) {
        SysConfig cfg = getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .last("LIMIT 1"));
        return cfg == null || cfg.getConfigValue() == null || cfg.getConfigValue().trim().isEmpty()
                ? defaultValue : cfg.getConfigValue().trim();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 全部参数（转为 key -> value 的 Map，便于前端渲染）
     */
    public Map<String, Object> allAsMap() {
        List<SysConfig> list = list(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
        Map<String, Object> map = new LinkedHashMap<>();
        for (SysConfig c : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", c.getConfigValue());
            item.put("remark", c.getRemark());
            item.put("updateTime", c.getUpdateTime());
            map.put(c.getConfigKey(), item);
        }
        return map;
    }

    public List<SysConfig> all() {
        return list(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String key, String value) {
        SysConfig cfg = getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .last("LIMIT 1"));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            save(cfg);
        } else {
            cfg.setConfigValue(value);
            updateById(cfg);
        }
        log.info("更新系统参数 {} = {}", key, value);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(Map<String, String> values) {
        if (values == null) {
            return;
        }
        values.forEach(this::updateConfig);
    }
}