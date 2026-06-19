package com.nyzy.audit;

import com.nyzy.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-center")
public class AuditController {

    private final AuditCenterService service;

    public AuditController(AuditCenterService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public Result<List<AuditItem>> list(@RequestParam(required = false) String bizType) {
        return Result.ok(service.list(bizType));
    }

    @PostMapping("/audit")
    public Result<Void> audit(@RequestParam String bizType, @RequestParam Long id, @RequestParam boolean pass) {
        service.audit(bizType, id, pass);
        return Result.ok();
    }
}
