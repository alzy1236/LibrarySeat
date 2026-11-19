package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.library.seat.common.BusinessException;
import com.library.seat.common.Constants;
import com.library.seat.dto.StudyRoomDTO;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.StudyRoom;
import com.library.seat.mapper.LongTermSeatMapper;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.StudyRoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 自习室服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyRoomService extends ServiceImpl<StudyRoomMapper, StudyRoom> {

    /** 座位图每行列数 */
    public static final int COLUMNS = 6;

    private final SeatMapper seatMapper;
    private final LongTermSeatMapper longTermSeatMapper;

    /**
     * 查询自习室列表（含今日使用统计）
     */
    public List<StudyRoom> listWithStatistics(String roomType, String keyword, Integer status) {
        return baseMapper.selectRoomStatistics(roomType, keyword, status, LocalDate.now());
    }

    public List<StudyRoom> listLongRooms() {
        return baseMapper.selectRoomStatistics(Constants.ROOM_TYPE_LONG, null, 1, LocalDate.now());
    }

    public List<StudyRoom> listShortRooms() {
        return baseMapper.selectRoomStatistics(Constants.ROOM_TYPE_SHORT, null, 1, LocalDate.now());
    }

    public StudyRoom getByIdRequired(Long id) {
        StudyRoom room = getById(id);
        if (room == null) {
            throw new BusinessException(101, "自习室不存在");
        }
        return room;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveRoom(StudyRoomDTO dto) {
        validate(dto);
        StudyRoom room = new StudyRoom();
        copy(room, dto);
        if (!StringUtils.hasText(dto.getOpenStart())) {
            room.setOpenStart("08:00");
        }
        if (!StringUtils.hasText(dto.getOpenEnd())) {
            room.setOpenEnd("23:30");
        }
        room.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        save(room);
        generateSeats(room.getId(), dto.getCapacity());
        return room.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRoom(StudyRoomDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少自习室 ID");
        }
        validate(dto);
        StudyRoom room = getByIdRequired(dto.getId());
        Integer oldCapacity = room.getCapacity();
        String oldType = room.getRoomType();
        boolean typeChanged = !dto.getRoomType().equals(oldType);
        if (typeChanged && hasActiveBindings(room.getId())) {
            throw new BusinessException("该自习室存在使用中的长期固定座位，请先释放座位后再修改类型");
        }
        copy(room, dto);
        if (dto.getStatus() != null) {
            room.setStatus(dto.getStatus());
        }
        updateById(room);

        boolean capacityChanged = dto.getCapacity() != null && !dto.getCapacity().equals(oldCapacity);
        if (capacityChanged) {
            if (dto.getRebuildSeats() != null && dto.getRebuildSeats() == 1) {
                rebuildSeats(room.getId(), dto.getCapacity());
            } else {
                long seatCount = seatMapper.selectCount(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, room.getId()));
                if (dto.getCapacity() > seatCount) {
                    generateSeats(room.getId(), (int) (dto.getCapacity() - seatCount));
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRoom(Long id) {
        StudyRoom room = getByIdRequired(id);
        if (hasActiveBindings(id)) {
            throw new BusinessException("该自习室存在使用中的长期固定座位，无法删除");
        }
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, id));
        removeById(id);
        log.info("删除自习室: {}", room.getName());
    }

    /**
     * 按行（每行 6 个）批量生成座位，座位号为 “R序号-序号”
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateSeats(Long roomId, int count) {
        StudyRoom room = getByIdRequired(roomId);
        long existing = seatMapper.selectCount(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, roomId));
        String prefix = "R" + roomId;
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int index = (int) existing + i;
            Seat seat = new Seat();
            seat.setRoomId(roomId);
            seat.setSeatNo(String.format("%s-%02d", prefix, index));
            seat.setRowNo((index - 1) / COLUMNS + 1);
            seat.setColNo((index - 1) % COLUMNS + 1);
            seat.setHasPower((index - 1) % COLUMNS == 0 || (index - 1) % COLUMNS == COLUMNS - 1 ? 1 : 0);
            seat.setStatus(1);
            seats.add(seat);
        }
        int total = 0;
        for (int i = 0; i < seats.size(); i += 200) {
            List<Seat> part = seats.subList(i, Math.min(i + 200, seats.size()));
            total += seatMapper.insertBatch(part);
        }
        long seatCount = seatMapper.selectCount(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, roomId));
        room.setCapacity((int) seatCount);
        updateById(room);
        log.info("自习室 [{}] 新增座位 {} 个，当前共 {} 个", room.getName(), total, seatCount);
        return total;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebuildSeats(Long roomId, int capacity) {
        if (hasActiveBindings(roomId)) {
            throw new BusinessException("该自习室存在使用中的长期固定座位，无法重建座位");
        }
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, roomId));
        generateSeats(roomId, capacity);
    }

    public boolean hasActiveBindings(Long roomId) {
        Long cnt = longTermSeatMapper.selectCount(new LambdaQueryWrapper<LongTermSeat>()
                .eq(LongTermSeat::getRoomId, roomId)
                .eq(LongTermSeat::getStatus, Constants.BIND_ACTIVE));
        return cnt != null && cnt > 0;
    }

    private void validate(StudyRoomDTO dto) {
        if (!Constants.ROOM_TYPE_LONG.equals(dto.getRoomType()) && !Constants.ROOM_TYPE_SHORT.equals(dto.getRoomType())) {
            throw new BusinessException("自习室类型只能为长期(LONG)或短期(SHORT)");
        }
        if (dto.getCapacity() == null || dto.getCapacity() < 1) {
            throw new BusinessException("座位数至少为 1");
        }
        if (dto.getCapacity() > 200) {
            throw new BusinessException("单个自习室座位数不能超过 200");
        }
    }

    private void copy(StudyRoom room, StudyRoomDTO dto) {
        room.setName(dto.getName());
        room.setRoomType(dto.getRoomType());
        room.setLocation(dto.getLocation());
        room.setCapacity(dto.getCapacity());
        if (StringUtils.hasText(dto.getOpenStart())) {
            room.setOpenStart(dto.getOpenStart());
        }
        if (StringUtils.hasText(dto.getOpenEnd())) {
            room.setOpenEnd(dto.getOpenEnd());
        }
        room.setDescription(dto.getDescription());
    }
}