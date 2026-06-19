package com.nyzy.abandon.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("abandon_parcel")
public class AbandonParcel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String parcelCode;
    private String parcelName;
    private Long regionId;
    private String regionPath;
    private Integer abandonYear;
    private BigDecimal area;
    private BigDecimal partialRatio;
    private String degree;           // FULL / PARTIAL
    private LocalDate startTime;
    private LocalDate foundDate;
    private String source;           // REMOTE / PATROL / REPORT
    private String reasonType;       // LABOR / ECON / INFRA / SOIL / DISASTER / TRANSFER / OTHER
    private String reasonText;
    private String reporter;
    private String governStatus;     // PENDING / UNGOVERNED / GOVERNING / GOVERNED / REJECTED
    private String manager;
    private String remark;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
    private String deleteReason;
    private String deletedBy;
    private LocalDateTime deletedAt;

    private String createdBy;
    private LocalDateTime createdAt;  // DB default CURRENT_TIMESTAMP
    private LocalDateTime updatedAt;
}
