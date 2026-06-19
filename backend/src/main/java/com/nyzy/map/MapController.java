package com.nyzy.map;

import com.nyzy.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService service;

    public MapController(MapService service) {
        this.service = service;
    }

    @GetMapping("/points")
    public Result<Map<String, Object>> points() {
        return Result.ok(service.points());
    }
}
