package com.nyzy.auth;

import com.nyzy.system.entity.Region;
import com.nyzy.system.mapper.RegionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DataScopeTest {

    private final RegionMapper regionMapper = Mockito.mock(RegionMapper.class);
    private final DataScope dataScope = new DataScope(regionMapper);

    private Region r(long id, long parent) {
        Region x = new Region();
        x.setId(id);
        x.setParentId(parent);
        return x;
    }

    private void stubTree() {
        // 1 > 2 > 3 > {10,11} ; 10 > 100 ; 11 > 110
        List<Region> all = Arrays.asList(
                r(1, 0), r(2, 1), r(3, 2), r(10, 3), r(11, 3), r(100, 10), r(110, 11));
        when(regionMapper.selectList(any())).thenReturn(all);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void admin_noRestriction() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin", 3L));
        assertNull(dataScope.currentScope(), "管理员应返回 null(不限制)");
    }

    @Test
    void nonAdmin_nullRegion_seesNothing() {
        UserContext.set(new UserContext.CurrentUser(2L, "op", "operator", null));
        Set<Long> scope = dataScope.currentScope();
        assertNotNull(scope);
        assertTrue(scope.isEmpty(), "无归属区划应看不到任何数据");
    }

    @Test
    void operatorAtCity_seesAllDescendants() {
        stubTree();
        UserContext.set(new UserContext.CurrentUser(2L, "op", "operator", 3L));
        Set<Long> scope = dataScope.currentScope();
        assertTrue(scope.containsAll(Arrays.asList(3L, 10L, 11L, 100L, 110L)));
        assertEquals(5, scope.size());
    }

    @Test
    void gridmanAtVillage_seesOnlySelf() {
        stubTree();
        UserContext.set(new UserContext.CurrentUser(3L, "gm", "gridman", 110L));
        Set<Long> scope = dataScope.currentScope();
        assertEquals(Set.of(110L), scope, "村级网格员只应看到本村");
    }

    @Test
    void noUser_noRestriction() {
        // 无登录上下文(如定时任务) -> 不限制
        assertNull(dataScope.currentScope());
    }
}
