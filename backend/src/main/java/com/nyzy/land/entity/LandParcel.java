package com.nyzy.land.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("land_parcel")
public class LandParcel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String parcelCode;
    private String name;
    private Long regionId;
    private String regionPath;
    private String contractorName;
    private String contractorCode;
    private BigDecimal area;
    private String landUse;
    private String boundEast;
    private String boundSouth;
    private String boundWest;
    private String boundNorth;
    private BigDecimal centerLng;
    private BigDecimal centerLat;
    private String boundary;          // GeoJSON
    private String mergeStatus;       // NORMAL / MERGED(已合并入其他地块)
    private String mergedIntoCode;    // 合并去向的新地块编码
    private LocalDate contractStart;
    private LocalDate contractEnd;
    private String remark;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
    private String deleteReason;
    private String deletedBy;
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
