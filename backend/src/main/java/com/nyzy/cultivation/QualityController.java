package com.nyzy.cultivation;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.cultivation.dto.QualityQuery;
import com.nyzy.cultivation.entity.LandQuality;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quality")
public class QualityController {

    private final QualityService service;

    public QualityController(QualityService service) {
        this.service = service;
    }

    @GetMapping("/records")
    public Result<PageResult<LandQuality>> page(QualityQuery query) {
        return Result.ok(service.page(query));
    }

    @PostMapping("/records")
    public Result<Long> create(@RequestBody LandQuality record) {
        return Result.ok(service.create(record));
    }

    @PutMapping("/records/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody LandQuality record) {
        record.setId(id);
        service.update(record);
        return Result.ok();
    }

    @DeleteMapping("/records/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.delete(id, reason);
        return Result.ok();
    }

    public static class BatchRequest {
        public java.util.List<Long> ids;
        public LandQuality updates;
    }

    @PostMapping("/batch")
    public Result<Integer> batch(@RequestBody BatchRequest req) {
        return Result.ok(service.batchUpdate(req.ids, req.updates));
    }
}
