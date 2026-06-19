package com.nyzy.recycle;

import com.nyzy.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recycle")
public class RecycleController {

    private final RecycleService service;

    public RecycleController(RecycleService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public Result<List<RecycleItem>> list(@RequestParam(required = false) String bizType) {
        return Result.ok(service.list(bizType));
    }

    @PostMapping("/restore")
    public Result<Void> restore(@RequestParam String bizType, @RequestParam Long id) {
        service.restore(bizType, id);
        return Result.ok();
    }

    @DeleteMapping("/purge")
    public Result<Void> purge(@RequestParam String bizType, @RequestParam Long id) {
        service.purge(bizType, id);
        return Result.ok();
    }
}
