package com.library.seat.controller;

import com.library.seat.common.Result;
import com.library.seat.entity.CheckinLocation;
import com.library.seat.entity.StudyRoom;
import com.library.seat.mapper.StudyRoomMapper;
import com.library.seat.service.CheckinLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端：签到点（地理围栏）管理
 */
@RestController
@RequestMapping("/api/admin/locations")
@RequiredArgsConstructor
public class AdminLocationController {

    private final CheckinLocationService locationService;
    private final StudyRoomMapper roomMapper;

    /** 列表（可按名称/自习室搜索） */
    @GetMapping
    public Result<List<CheckinLocation>> list(@RequestParam(required = false) String keyword) {
        return Result.success(locationService.list(keyword));
    }

    /** 新增 */
    @PostMapping
    public Result<CheckinLocation> add(@RequestBody CheckinLocation dto) {
        dto.setId(null);
        return Result.success("签到点已添加", locationService.save(dto));
    }

    /** 修改 */
    @PutMapping
    public Result<CheckinLocation> update(@RequestBody CheckinLocation dto) {
        return Result.success("签到点已更新", locationService.update(dto));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return Result.success("已删除", null);
    }

    /** 启用/停用 */
    @PostMapping("/{id}/status")
    public Result<CheckinLocation> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        CheckinLocation dto = new CheckinLocation();
        dto.setId(id);
        dto.setEnabled(enabled);
        CheckinLocation old = locationService.list(null).stream()
                .filter(l -> l.getId().equals(id)).findFirst().orElse(null);
        if (old == null) {
            return Result.error("签到点不存在");
        }
        dto.setName(old.getName());
        dto.setLatitude(old.getLatitude());
        dto.setLongitude(old.getLongitude());
        dto.setRadius(old.getRadius());
        dto.setRoomId(old.getRoomId());
        dto.setRemark(old.getRemark());
        return Result.success(enabled == 1 ? "已启用" : "已停用", locationService.update(dto));
    }

    /** 可选自习室（供表单下拉） */
    @GetMapping("/rooms")
    public Result<List<StudyRoom>> rooms() {
        return Result.success(roomMapper.selectList(null));
    }
}