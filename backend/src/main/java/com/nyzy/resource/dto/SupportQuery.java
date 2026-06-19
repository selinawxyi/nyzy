package com.nyzy.resource.dto;

import lombok.Data;

/** 配套设施列表查询 */
@Data
public class SupportQuery {
    private String keyword;        // 名称
    private Long categoryId;       // 二级分类
    private String operateStatus;  // 运营状态
    private String operateSubject; // 运营主体
    private long page = 1;
    private long size = 10;
}
