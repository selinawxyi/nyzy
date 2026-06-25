package com.nyzy.gis;

import com.nyzy.common.Result;
import com.nyzy.gis.dto.SpatialQueryRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gis")
public class SpatialQueryController {

    private final SpatialQueryService service;

    public SpatialQueryController(SpatialQueryService service) {
        this.service = service;
    }

    @PostMapping("/spatial-query")
    public Result<List<Map<String, Object>>> spatialQuery(@RequestBody SpatialQueryRequest req) {
        return Result.ok(service.query(req));
    }
}
