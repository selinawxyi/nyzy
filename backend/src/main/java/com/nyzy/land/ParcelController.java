package com.nyzy.land;

import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import com.nyzy.land.dto.ParcelQuery;
import com.nyzy.land.entity.LandAnnotation;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.entity.LandParcelHistory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parcel")
public class ParcelController {

    private final ParcelService service;
    private final AnnotationService annotationService;

    public ParcelController(ParcelService service, AnnotationService annotationService) {
        this.service = service;
        this.annotationService = annotationService;
    }

    @GetMapping("/parcels")
    public Result<PageResult<LandParcel>> page(ParcelQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/parcels/{id}")
    public Result<LandParcel> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping("/parcels")
    public Result<Long> create(@RequestBody LandParcel parcel) {
        return Result.ok(service.create(parcel));
    }

    @PutMapping("/parcels/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody LandParcel parcel,
                               @RequestParam(required = false) String reason) {
        parcel.setId(id);
        service.update(parcel, reason);
        return Result.ok();
    }

    @DeleteMapping("/parcels/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.delete(id, reason);
        return Result.ok();
    }

    public static class BatchRequest {
        public List<Long> ids;
        public LandParcel updates;
        public String reason;
    }

    @PostMapping("/parcels/batch")
    public Result<Integer> batch(@RequestBody BatchRequest req) {
        return Result.ok(service.batchUpdate(req.ids, req.updates, req.reason));
    }

    // ---- 几何编辑(A1.4) ----
    @PutMapping("/parcels/{id}/geometry")
    public Result<Void> updateGeometry(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateGeometry(id, body.get("boundary"), body.get("reason"));
        return Result.ok();
    }

    @PostMapping("/parcels/{id}/split")
    public Result<Long> split(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(service.split(id, body.get("line"), body.get("newCode"), body.get("reason")));
    }

    @PostMapping("/parcels/merge")
    public Result<Long> merge(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        return Result.ok(service.merge(ids, (String) body.get("newCode"), (String) body.get("reason")));
    }

    // ---- 版本历史 ----
    @GetMapping("/parcels/{id}/history")
    public Result<List<LandParcelHistory>> history(@PathVariable Long id) {
        return Result.ok(service.history(id));
    }

    @GetMapping("/history/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long v1, @RequestParam Long v2) {
        return Result.ok(service.compare(v1, v2));
    }

    // ---- 地块标注 ----
    @GetMapping("/parcels/{id}/annotations")
    public Result<List<LandAnnotation>> annotations(@PathVariable Long id) {
        return Result.ok(annotationService.list(id));
    }

    @PostMapping("/parcels/{id}/annotations")
    public Result<Long> addAnnotation(@PathVariable Long id, @RequestBody LandAnnotation annotation) {
        annotation.setParcelId(id);
        return Result.ok(annotationService.add(annotation));
    }

    @DeleteMapping("/annotations/{id}")
    public Result<Void> deleteAnnotation(@PathVariable Long id) {
        annotationService.delete(id);
        return Result.ok();
    }
}
