package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.library.seat.entity.SysNotice;
import com.library.seat.mapper.SysNoticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 公告服务
 */
@Service
public class SysNoticeService extends ServiceImpl<SysNoticeMapper, SysNotice> {

    public List<SysNotice> listPublished(int limit) {
        List<SysNotice> list = list(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1)
                .orderByDesc(SysNotice::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
        return list == null ? Collections.emptyList() : list;
    }

    public List<SysNotice> listAll(String keyword) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysNotice::getTitle, keyword);
        }
        wrapper.orderByDesc(SysNotice::getCreateTime);
        return list(wrapper);
    }

    public void saveNotice(SysNotice notice, Long publisherId) {
        notice.setId(null);
        notice.setPublisherId(publisherId);
        if (notice.getStatus() == null) {
            notice.setStatus(1);
        }
        notice.setCreateTime(LocalDateTime.now());
        notice.setUpdateTime(LocalDateTime.now());
        save(notice);
    }

    public void updateNotice(SysNotice notice) {
        SysNotice old = getById(notice.getId());
        if (old == null) {
            return;
        }
        old.setTitle(notice.getTitle());
        old.setContent(notice.getContent());
        old.setStatus(notice.getStatus() == null ? old.getStatus() : notice.getStatus());
        old.setUpdateTime(LocalDateTime.now());
        updateById(old);
    }
}