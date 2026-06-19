package com.nyzy.analysis;

import com.nyzy.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @GetMapping("/years")
    public Result<List<Integer>> years() {
        return Result.ok(service.years());
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Integer year) {
        return Result.ok(service.overview(year));
    }

    @GetMapping("/yearly")
    public Result<Map<String, Object>> yearly() {
        return Result.ok(service.yearly());
    }

    @GetMapping("/region")
    public Result<List<Map<String, Object>>> region(@RequestParam(required = false) Integer year) {
        return Result.ok(service.byRegion(year));
    }

    @GetMapping("/land-use")
    public Result<List<Map<String, Object>>> landUse(@RequestParam(required = false) Integer year) {
        return Result.ok(service.landUse(year));
    }

    @GetMapping("/advantage-zones")
    public Result<Map<String, Object>> advantageZones(@RequestParam(required = false) String crop) {
        return Result.ok(service.advantageZones(crop));
    }

    @GetMapping("/sankey")
    public Result<Map<String, Object>> sankey(@RequestParam(required = false) Integer fromYear,
                                              @RequestParam(required = false) Integer toYear) {
        return Result.ok(service.sankey(fromYear, toYear));
    }

    @GetMapping("/region-compare")
    public Result<Map<String, Object>> regionCompare(@RequestParam(required = false) Integer year) {
        return Result.ok(service.regionCompare(year));
    }
}
