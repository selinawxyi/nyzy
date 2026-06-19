package com.nyzy.resource;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.resource.dto.WaterQuery;
import com.nyzy.resource.entity.WaterFacility;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/water")
public class WaterFacilityController {

    private final WaterFacilityService service;

    public WaterFacilityController(WaterFacilityService service) {
        this.service = service;
    }

    @GetMapping("/facilities")
    public Result<PageResult<WaterFacility>> page(WaterQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/facilities/{id}")
    public Result<WaterFacility> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping("/facilities")
    public Result<Long> create(@RequestBody WaterFacility facility) {
        return Result.ok(service.create(facility));
    }

    @PutMapping("/facilities/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WaterFacility facility) {
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

    public static class BatchRequest {
        public java.util.List<Long> ids;
        public WaterFacility updates;
    }

    @PostMapping("/facilities/batch")
    public Result<Integer> batch(@RequestBody BatchRequest req) {
        return Result.ok(service.batchUpdate(req.ids, req.updates));
    }
}
