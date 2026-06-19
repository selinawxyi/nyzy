package com.nyzy.resource;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.resource.dto.SupportQuery;
import com.nyzy.resource.entity.SupportFacility;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
public class SupportFacilityController {

    private final SupportFacilityService service;

    public SupportFacilityController(SupportFacilityService service) {
        this.service = service;
    }

    @GetMapping("/facilities")
    public Result<PageResult<SupportFacility>> page(SupportQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/facilities/{id}")
    public Result<SupportFacility> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping("/facilities")
    public Result<Long> create(@RequestBody SupportFacility facility) {
        return Result.ok(service.create(facility));
    }

    @PutMapping("/facilities/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SupportFacility facility) {
        facility.setId(id);
        service.update(facility);
        return Result.ok();
    }

    @PostMapping("/facilities/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam boolean pass) {
        service.audit(id, pass);
        return Result.ok();
    }

    @DeleteMapping("/facilities/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.delete(id, reason);
        return Result.ok();
    }
}
