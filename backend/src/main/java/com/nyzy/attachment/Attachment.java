package com.nyzy.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_attachment")
public class Attachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String storedName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime createdAt;
}
