package com.nyzy.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.auth.UserContext;
import com.nyzy.system.entity.AuditLog;
import com.nyzy.system.mapper.AuditLogMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

    private final AuditLogMapper mapper;

    public AuditLogController(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/list")
    public Result<PageResult<AuditLog>> list(@RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String operator,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "15") long size) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可查看审计日志");
        QueryWrapper<AuditLog> w = new QueryWrapper<>();
        if (StringUtils.hasText(bizType)) w.eq("biz_type", bizType);
        if (StringUtils.hasText(action)) w.eq("action", action);
        if (StringUtils.hasText(operator)) w.like("operator", operator);
        w.orderByDesc("id");
        IPage<AuditLog> p = mapper.selectPage(new Page<>(page, size), w);
        return Result.ok(new PageResult<>(p.getTotal(), p.getRecords()));
    }
}
