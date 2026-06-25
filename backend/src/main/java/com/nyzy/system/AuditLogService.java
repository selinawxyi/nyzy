package com.nyzy.system;

import com.nyzy.auth.UserContext;
import com.nyzy.system.entity.AuditLog;
import com.nyzy.system.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

/**
 * 全平台通用操作审计日志写入点。各业务模块的增删改/状态变更都应调用这里记一笔,
 * 避免 sys_audit_log 名为"系统通用"实际只有个别模块在写。
 * land 模块例外: 它有更完整的 land_parcel_history(版本号+快照+变更对比), 不重复记一份简单日志。
 */
@Service
public class AuditLogService {

    private final AuditLogMapper mapper;

    public AuditLogService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String bizType, Long bizId, String action, String detail) {
        AuditLog log = new AuditLog();
        log.setBizType(bizType);
        log.setBizId(bizId == null ? null : String.valueOf(bizId));
        log.setAction(action);
        log.setOperator(UserContext.username());
        log.setDetail(detail);
        mapper.insert(log);
    }
}
