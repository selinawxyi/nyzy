package com.nyzy.audit;

import lombok.Data;

import java.time.LocalDateTime;

/** 待审核统一条目 */
@Data
public class AuditItem {
    private String bizType;        // water / support / abandon
    private String bizTypeName;
    private Long bizId;
    private String title;
    private String subtitle;
    private String submittedBy;
    private LocalDateTime submittedAt;
}
