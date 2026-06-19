package com.nyzy.resource.dto;

import lombok.Data;

/** 水利设施列表查询 */
@Data
public class WaterQuery {
    private String keyword;      // 名称/管护责任人
    private String type;         // 设施类型
    private String runStatus;    // 运行状态
    private String auditStatus;  // 审核状态
    private long page = 1;
    private long size = 10;
}
