package com.nyzy.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("water_facility")
public class WaterFacility {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;             // 机井/泵站/水闸/渠道/滴灌系统/喷灌系统/蓄水池
    private Long regionId;
    private String regionPath;
    private BigDecimal lng;
    private BigDecimal lat;
    private Integer buildYear;
    private BigDecimal coverArea;
    private String benefitVillages;
    private String runStatus;        // 正常/维修中/废弃/待改造
    private String manager;
    private String phone;
    private LocalDate lastMaintainDate;
    private String techParams;
    private String auditStatus;      // PENDING/APPROVED/REJECTED
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

    @Version
    private Integer version;
}
