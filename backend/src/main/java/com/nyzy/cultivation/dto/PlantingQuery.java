package com.nyzy.cultivation.dto;

import lombok.Data;

/** 种植记录列表查询 */
@Data
public class PlantingQuery {
    private String keyword;     // 地块编码/地块名/承包方
    private Integer plantYear;
    private String crop;
    private String dataSource;
    private String status;
    private long page = 1;
    private long size = 10;
}
