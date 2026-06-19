package com.nyzy.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("support_facility")
public class SupportFacility {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long categoryId;
    @TableField(exist = false)
    private String categoryName;     // 关联展示用, 非表字段
    private Long regionId;
    private String regionPath;
    private BigDecimal lng;
    private BigDecimal lat;
    private String serviceRange;
    private String serviceAbility;
    private String operateStatus;    // 正常/停业/建设中
    private String operateSubject;   // 企业/合作社/个体户
    private String phone;
    private String businessHours;
    private String qualification;
    private String auditStatus;
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
