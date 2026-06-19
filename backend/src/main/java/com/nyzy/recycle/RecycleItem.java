package com.nyzy.recycle;

import lombok.Data;

import java.time.LocalDateTime;

/** 回收站统一条目 */
@Data
public class RecycleItem {
    private String bizType;        // 业务类型 key
    private String bizTypeName;    // 业务类型中文名 (服务层填充)
    private Long bizId;
    private String title;          // 主标题(名称)
    private String subtitle;       // 副标题(编码/年度等)
    private String deleteReason;
    private String deletedBy;
    private LocalDateTime deletedAt;
    private Integer daysLeft;      // 距物理删除剩余天数 (服务层计算)
}
