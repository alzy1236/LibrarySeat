package com.library.seat.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 自习室新增 / 修改
 */
@Data
public class StudyRoomDTO {

    private Long id;

    @NotBlank(message = "自习室名称不能为空")
    private String name;

    @NotBlank(message = "请选择自习室类型")
    private String roomType;

    private String location;

    @NotNull(message = "座位数不能为空")
    @Min(value = 1, message = "座位数至少为 1")
    private Integer capacity;

    private String openStart;

    private String openEnd;

    private String description;

    private Integer status;

    /** 修改时若座位数变化，是否同步重建座位（1 是） */
    private Integer rebuildSeats;
}