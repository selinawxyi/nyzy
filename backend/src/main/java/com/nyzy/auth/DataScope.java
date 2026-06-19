package com.nyzy.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.system.entity.Region;
import com.nyzy.system.mapper.RegionMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据权限 / 可见范围.
 * admin: 不限制(看全部);
 * 其他角色: 仅可见 其所属区划 + 所有下级区划 内的数据.
 */
@Component
public class DataScope {

    private final RegionMapper regionMapper;

    public DataScope(RegionMapper regionMapper) {
        this.regionMapper = regionMapper;
    }

    /**
     * 当前用户可见的区划ID集合.
     * 返回 null 表示不限制(管理员); 返回空集合表示无任何可见数据.
     */
    public Set<Long> currentScope() {
        UserContext.CurrentUser u = UserContext.get();
        if (u == null || "admin".equals(u.role)) return null;       // 管理员不限制
        if (u.regionId == null) return Collections.emptySet();       // 无归属区划 -> 看不到
        return descendantsOf(u.regionId);
    }

    /** 给列表查询追加区划过滤; column 为该表的区划字段名(如 region_id) */
    public <T> void apply(QueryWrapper<T> wrapper, String column) {
        Set<Long> scope = currentScope();
        if (scope == null) return;                 // 管理员: 不加条件
        if (scope.isEmpty()) {
            wrapper.apply("1 = 0");                 // 无可见数据
        } else {
            wrapper.in(column, scope);
        }
    }

    /** 指定区划及其全部下级区划ID */
    private Set<Long> descendantsOf(Long rootId) {
        List<Region> all = regionMapper.selectList(null);
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (Region r : all) {
            Long p = r.getParentId() == null ? 0L : r.getParentId();
            childrenMap.computeIfAbsent(p, k -> new ArrayList<>()).add(r.getId());
        }
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!result.add(id)) continue;
            List<Long> children = childrenMap.get(id);
            if (children != null) queue.addAll(children);
        }
        return result;
    }
}
