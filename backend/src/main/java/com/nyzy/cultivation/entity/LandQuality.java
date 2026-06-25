package com.nyzy.cultivation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("land_quality")
public class LandQuality {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String parcelCode;
    private String parcelName;
    private Long regionId;
    private String regionPath;
    private String contractorName;
    private Integer evalYear;
    private Integer grade;            // 1-10, 1 最好
    private BigDecimal score;
    private String soilType;
    private BigDecimal organicMatter;
    private BigDecimal totalN;
    private BigDecimal availP;
    private BigDecimal availK;
    private BigDecimal ph;
    private String obstacle;
    private String suitableCrops;
    private String org;
    private String reportFile;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
    private String deleteReason;
    private String deletedBy;
    private LocalDateTime deletedAt;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
