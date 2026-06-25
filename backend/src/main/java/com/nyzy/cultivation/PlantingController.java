package com.nyzy.cultivation;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.cultivation.dto.PlantingQuery;
import com.nyzy.cultivation.entity.PlantingRecord;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/planting")
public class PlantingController {

    private final PlantingService service;

    public PlantingController(PlantingService service) {
        this.service = service;
    }

    @GetMapping("/records")
    public Result<PageResult<PlantingRecord>> page(PlantingQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/history")
    public Result<List<PlantingRecord>> history(@RequestParam String parcelCode) {
        return Result.ok(service.history(parcelCode));
    }

    @PostMapping("/records")
    public Result<Long> create(@RequestBody PlantingRecord record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/records/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PlantingRecord record) {
        record.setId(id);
        service.update(record);
        return Result.ok();
    }

    @PostMapping("/records/{id}/invalid")
    public Result<Void> markInvalid(@PathVariable Long id) {
        service.markInvalid(id);
        return Result.ok();
    }

    @DeleteMapping("/records/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.delete(id, reason);
        return Result.ok();
    }

    public static class BatchRequest {
        public List<Long> ids;
        public PlantingRecord updates;
    }

    @PostMapping("/records/batch")
    public Result<Integer> batch(@RequestBody BatchRequest req) {
        return Result.ok(service.batchUpdate(req.ids, req.updates));
    }

    public static class BatchDeleteRequest {
        public List<Long> ids;
        public String reason;
    }

    @PostMapping("/records/batch-delete")
    public Result<Integer> batchDelete(@RequestBody BatchDeleteRequest req) {
        return Result.ok(service.batchDelete(req.ids, req.reason));
    }
}
