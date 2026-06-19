package com.nyzy.cultivation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("planting_record")
public class PlantingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String parcelCode;
    private String parcelName;
    private Long regionId;
    private String regionPath;
    private Integer plantYear;
    private String season;            // SPRING / SUMMER / AUTUMN
    private String crop;
    private String variety;
    private BigDecimal area;
    private LocalDate sowDate;
    private LocalDate expectHarvestDate;
    private LocalDate actualHarvestDate;  // 非空 = 已收获
    private BigDecimal yieldPerMu;
    private String dataSource;        // REMOTE / STAT / FARMER / PATROL
    private String reporter;
    private LocalDate reportDate;
    private String status;            // VALID / INVALID
    private String remark;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
    private String deleteReason;
    private String deletedBy;
    private LocalDateTime deletedAt;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
