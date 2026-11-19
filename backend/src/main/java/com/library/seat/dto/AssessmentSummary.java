package com.library.seat.dto;

import lombok.Data;

/**
 * 长期座位考核结果
 */
@Data
public class AssessmentSummary {

    /** 考核月份 yyyy-MM */
    private String month;

    /** 参与考核的长期座位人数 */
    private int total;

    /** 合格人数 */
    private int qualified;

    /** 不合格人数 */
    private int unqualified;

    /** 因不合格被取消座位人数 */
    private int cancelled;

    /** 考核规则描述 */
    private String rule;
}