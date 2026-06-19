package com.nyzy.resource;

import com.nyzy.common.Result;
import com.nyzy.resource.entity.FacilityCategory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facility-category")
public class FacilityCategoryController {

    private final FacilityCategoryService service;

    public FacilityCategoryController(FacilityCategoryService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    public Result<List<FacilityCategory>> tree() {
        return Result.ok(service.tree());
    }

    @GetMapping("/leaves")
    public Result<List<FacilityCategory>> leaves() {
        return Result.ok(service.leafList());
    }

    @PostMapping
    public Result<Long> create(@RequestBody FacilityCategory category) {
        return Result.ok(service.create(category));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody FacilityCategory category) {
        category.setId(id);
        service.update(category);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
