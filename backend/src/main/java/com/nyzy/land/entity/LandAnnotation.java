package com.nyzy.land.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("land_annotation")
public class LandAnnotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parcelId;
    private String parcelCode;
    private String type;             // TEXT/COLOR/TAG
    private String content;
    private String color;
    private String tags;
    private String visibleScope;     // SELF/ALL
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
}
