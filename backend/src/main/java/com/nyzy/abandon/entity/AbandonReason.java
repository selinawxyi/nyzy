package com.nyzy.abandon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("abandon_reason")
public class AbandonReason {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long abandonId;
    private String reasonTypes;   // 逗号分隔多选
    private String detail;
    private String suggestion;
    private String reporter;
    private LocalDateTime createdAt;
}
