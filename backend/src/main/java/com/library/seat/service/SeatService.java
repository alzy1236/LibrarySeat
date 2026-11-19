package com.library.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.library.seat.common.BusinessException;
import com.library.seat.common.Constants;
import com.library.seat.dto.SeatDTO;
import com.library.seat.entity.LongTermSeat;
import com.library.seat.entity.Seat;
import com.library.seat.entity.SeatReservation;
import com.library.seat.mapper.LongTermSeatMapper;
import com.library.seat.mapper.SeatMapper;
import com.library.seat.mapper.SeatReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 座位服务：座位图、座位增删改、座位图快照
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService extends ServiceImpl<SeatMapper, Seat> {

    private final LongTermSeatMapper longTermSeatMapper;
    private final SeatReservationMapper reservationMapper;

    /**
     * 座位图（含今日占用状态）
     */
    public List<Seat> seatMap(Long roomId) {
        List<Seat> seats = baseMapper.selectSeatMap(roomId);
        for (Seat seat : seats) {
            if (seat.getStatus() != null && seat.getStatus() == 0) {
                seat.setSeatState("DISABLED");
            } else if (StringUtils.hasText(seat.getLongTermOwner())) {
                seat.setSeatState("LOCKED");
            } else if (seat.getSeatState() == null) {
                seat.setSeatState("AVAILABLE");
            }
        }
        return seats;
    }

    public List<Seat> listByRoom(Long roomId) {
        return list(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getRoomId, roomId)
                .orderByAsc(Seat::getRowNo).orderByAsc(Seat::getColNo));
    }

    public Seat getByIdRequired(Long id) {
        Seat seat = getById(id);
        if (seat == null) {
            throw new BusinessException("座位不存在");
        }
        return seat;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSeat(SeatDTO dto) {
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getRoomId, dto.getRoomId())
                .eq(Seat::getRowNo, dto.getRowNo() == null ? 1 : dto.getRowNo())
                .eq(Seat::getColNo, dto.getColNo() == null ? 1 : dto.getColNo()));
        if (exists > 0) {
            throw new BusinessException("该位置已存在座位");
        }
        Seat seat = new Seat();
        apply(seat, dto);
        save(seat);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSeat(SeatDTO dto) {
        Seat seat = getByIdRequired(dto.getId());
        // 停用前检查是否有使用中的长期绑定
        if (dto.getStatus() != null && dto.getStatus() == 0) {
            Long active = longTermSeatMapper.selectCount(new LambdaQueryWrapper<LongTermSeat>()
                    .eq(LongTermSeat::getSeatId, seat.getId())
                    .eq(LongTermSeat::getStatus, Constants.BIND_ACTIVE));
            if (active > 0) {
                throw new BusinessException("该座位已被长期绑定，无法停用");
            }
        }
        if (StringUtils.hasText(dto.getSeatNo())) {
            seat.setSeatNo(dto.getSeatNo());
        }
        if (dto.getRowNo() != null) {
            seat.setRowNo(dto.getRowNo());
        }
        if (dto.getColNo() != null) {
            seat.setColNo(dto.getColNo());
        }
        if (dto.getHasPower() != null) {
            seat.setHasPower(dto.getHasPower());
        }
        if (dto.getStatus() != null) {
            seat.setStatus(dto.getStatus());
        }
        updateById(seat);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSeat(Long id) {
        Seat seat = getByIdRequired(id);
        Long active = longTermSeatMapper.selectCount(new LambdaQueryWrapper<LongTermSeat>()
                .eq(LongTermSeat::getSeatId, id)
                .eq(LongTermSeat::getStatus, Constants.BIND_ACTIVE));
        if (active > 0) {
            throw new BusinessException("该座位已被长期绑定，无法删除");
        }
        SeatReservation res = reservationMapper.selectActiveBySeat(id, LocalDate.now());
        if (res != null) {
            throw new BusinessException("该座位今日存在有效预约，无法删除");
        }
        removeById(id);
        log.info("删除座位: {}", seat.getSeatNo());
    }

    private void apply(Seat seat, SeatDTO dto) {
        seat.setRoomId(dto.getRoomId());
        seat.setSeatNo(StringUtils.hasText(dto.getSeatNo())
                ? dto.getSeatNo()
                : String.format("R%d-%02d", dto.getRoomId(), (dto.getRowNo() == null ? 1 : dto.getRowNo()) * 10 + (dto.getColNo() == null ? 1 : dto.getColNo())));
        seat.setRowNo(dto.getRowNo() == null ? 1 : dto.getRowNo());
        seat.setColNo(dto.getColNo() == null ? 1 : dto.getColNo());
        seat.setHasPower(dto.getHasPower() == null ? 0 : dto.getHasPower());
        seat.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }
}