package com.nyzy.abandon.dto;

import lombok.Data;

/** 撂荒地块列表查询参数 */
@Data
public class AbandonQuery {
    private String keyword;        // 地块编码/地块名/上报人 模糊
    private String governStatus;  // 治理状态
    private Integer abandonYear;  // 撂荒年份
    private String source;        // 来源
    private String reasonType;    // 原因大类
    private long page = 1;
    private long size = 10;
}
