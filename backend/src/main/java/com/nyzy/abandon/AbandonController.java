package com.nyzy.abandon;

import com.nyzy.abandon.dto.AbandonDetail;
import com.nyzy.abandon.dto.AbandonQuery;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.entity.AbandonReason;
import com.nyzy.abandon.entity.AbandonTask;
import com.nyzy.common.PageResult;
import com.nyzy.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/abandon")
public class AbandonController {

    private final AbandonService service;

    public AbandonController(AbandonService service) {
        this.service = service;
    }

    @GetMapping("/parcels")
    public Result<PageResult<AbandonParcel>> page(AbandonQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/parcels/{id}")
    public Result<AbandonDetail> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/parcels")
    public Result<Long> create(@RequestBody AbandonParcel parcel) {
        return Result.ok(service.create(parcel));
    }

    @PutMapping("/parcels/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AbandonParcel parcel) {
        parcel.setId(id);
        service.update(parcel);
        return Result.ok();
    }

    @PostMapping("/parcels/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.changeStatus(id, body.get("governStatus"), body.get("remark"));
        return Result.ok();
    }

    @DeleteMapping("/parcels/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.delete(id, reason);
        return Result.ok();
    }

    @PostMapping("/parcels/{id}/reasons")
    public Result<Long> addReason(@PathVariable Long id, @RequestBody AbandonReason reason) {
        reason.setAbandonId(id);
        return Result.ok(service.addReason(reason));
    }

    @PostMapping("/parcels/{id}/tasks")
    public Result<Long> createTask(@PathVariable Long id, @RequestBody AbandonTask task) {
        task.setAbandonId(id);
        return Result.ok(service.createTask(task));
    }

    // ---- 治理任务办理 / 验收 ----
    @GetMapping("/tasks")
    public Result<java.util.List<AbandonTask>> tasks(@RequestParam(required = false) String status) {
        return Result.ok(service.tasks(status));
    }

    @PostMapping("/tasks/{taskId}/progress")
    public Result<Void> progress(@PathVariable Long taskId, @RequestBody java.util.Map<String, Integer> body) {
        service.updateTaskProgress(taskId, body.get("progress"));
        return Result.ok();
    }

    @PostMapping("/tasks/{taskId}/plan")
    public Result<Void> plan(@PathVariable Long taskId, @RequestBody java.util.Map<String, String> body) {
        service.updateTaskPlan(taskId, body.get("plan"));
        return Result.ok();
    }

    @PostMapping("/tasks/{taskId}/feedback")
    public Result<Void> feedback(@PathVariable Long taskId, @RequestBody java.util.Map<String, String> body) {
        service.taskFeedback(taskId, body.get("content"));
        return Result.ok();
    }

    @PostMapping("/tasks/{taskId}/accept")
    public Result<Void> accept(@PathVariable Long taskId, @RequestBody java.util.Map<String, Object> body) {
        boolean pass = Boolean.TRUE.equals(body.get("pass"));
        String crop = (String) body.get("crop");
        java.math.BigDecimal area = body.get("area") == null ? null
                : new java.math.BigDecimal(body.get("area").toString());
        Integer year = body.get("year") == null ? null : Integer.valueOf(body.get("year").toString());
        service.acceptTask(taskId, pass, crop, area, year);
        return Result.ok();
    }
}
