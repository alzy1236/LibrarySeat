package com.library.seat.common;

import lombok.Data;

@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 关键字 */
    private String keyword;

    public Long getOffset() {
        long num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return (num - 1) * size;
    }

    public Long getLimit() {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }
}