package com.nyzy.system;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.common.Result;
import com.nyzy.system.entity.Region;
import com.nyzy.system.mapper.RegionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/region")
public class RegionController {

    private final RegionMapper mapper;

    public RegionController(RegionMapper mapper) {
        this.mapper = mapper;
    }

    /** 行政区划嵌套树 (省→市→区县→乡镇→村) */
    @GetMapping("/tree")
    public Result<List<Region>> tree() {
        List<Region> all = mapper.selectList(new QueryWrapper<Region>().orderByAsc("level", "sort", "id"));
        Map<Long, List<Region>> byParent = all.stream()
                .collect(Collectors.groupingBy(r -> r.getParentId() == null ? 0L : r.getParentId()));
        all.forEach(r -> r.setChildren(byParent.get(r.getId())));
        List<Region> roots = new ArrayList<>();
        for (Region r : all) {
            if (r.getParentId() == null || r.getParentId() == 0) roots.add(r);
        }
        return Result.ok(roots);
    }
}
