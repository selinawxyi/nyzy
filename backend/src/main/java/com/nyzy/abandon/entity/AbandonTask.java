package com.nyzy.abandon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("abandon_task")
public class AbandonTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String name;
    private Long abandonId;
    private String parcelCode;
    private String description;
    private String respUnit;
    private String respPerson;
    private BigDecimal targetArea;
    private String standard;
    private String plan;          // 治理方案
    private LocalDate deadline;
    private Integer progress;
    private String taskStatus;    // ISSUED / HANDLING / ACCEPTING / DONE / RETURNED
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
