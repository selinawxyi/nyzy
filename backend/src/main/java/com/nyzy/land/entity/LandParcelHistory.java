package com.nyzy.land.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("land_parcel_history")
public class LandParcelHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parcelId;
    private String parcelCode;
    private Integer version;
    private String changeType;       // CREATE/UPDATE/DELETE
    private String changeFields;
    private String snapshot;         // JSON
    private String operator;
    private String reason;
    private LocalDateTime createdAt;
}
