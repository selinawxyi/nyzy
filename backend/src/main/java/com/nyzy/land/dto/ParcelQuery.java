package com.nyzy.land.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 确权地块条件查询 (A1.3) */
@Data
public class ParcelQuery {
    private String keyword;        // 地块编码/名称/承包方姓名
    private String landUse;        // 地块用途
    private BigDecimal areaMin;    // 面积范围
    private BigDecimal areaMax;
    private long page = 1;
    private long size = 10;
}
