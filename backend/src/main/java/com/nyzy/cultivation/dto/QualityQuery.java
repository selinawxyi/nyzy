package com.nyzy.cultivation.dto;

import lombok.Data;

/** 耕地质量评价列表查询 */
@Data
public class QualityQuery {
    private String keyword;       // 地块编码/地块名/承包方
    private Integer evalYear;
    private Integer gradeMin;     // 地力等级范围
    private Integer gradeMax;
    private String soilType;
    private String obstacle;
    private long page = 1;
    private long size = 10;
}
