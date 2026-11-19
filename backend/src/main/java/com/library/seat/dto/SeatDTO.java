package com.library.seat.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 座位新增 / 修改
 */
@Data
public class SeatDTO {

    private Long id;

    @NotNull(message = "所属自习室不能为空")
    private Long roomId;

    private String seatNo;

    private Integer rowNo;

    private Integer colNo;

    private Integer hasPower;

    private Integer status;
}