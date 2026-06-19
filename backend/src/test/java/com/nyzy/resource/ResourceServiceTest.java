package com.nyzy.resource;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.resource.entity.FacilityCategory;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ResourceServiceTest {

    private final WaterFacilityMapper waterMapper = Mockito.mock(WaterFacilityMapper.class);
    private final FacilityCategoryMapper categoryMapper = Mockito.mock(FacilityCategoryMapper.class);
    private final SupportFacilityMapper supportMapper = Mockito.mock(SupportFacilityMapper.class);

    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final WaterFacilityService waterService = new WaterFacilityService(waterMapper, dataScope);
    private final FacilityCategoryService categoryService =
            new FacilityCategoryService(categoryMapper, supportMapper);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private WaterFacility water(String type) {
        WaterFacility f = new WaterFacility();
        f.setId(1L);
        f.setName("测试机井");
        f.setType(type);
        f.setAuditStatus("PENDING");
        return f;
    }

    @Test
    void waterDelete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class, () -> waterService.delete(1L, "重复"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void waterAudit_nonPending_rejected() {
        WaterFacility f = water("机井");
        f.setAuditStatus("APPROVED");
        when(waterMapper.selectById(1L)).thenReturn(f);
        ApiException ex = assertThrows(ApiException.class, () -> waterService.audit(1L, true));
        assertTrue(ex.getMessage().contains("待审核"));
    }

    @Test
    void waterUpdate_keyFieldChange_resetsToPending() {
        WaterFacility old = water("机井");
        old.setAuditStatus("APPROVED");
        when(waterMapper.selectById(1L)).thenReturn(old);
        WaterFacility upd = water("泵站");   // 类型变更 = 关键字段
        upd.setAuditStatus("APPROVED");
        waterService.update(upd);
        assertEquals("PENDING", upd.getAuditStatus());
    }

    @Test
    void waterBatch_noIds_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> waterService.batchUpdate(null, water("机井")));
        assertTrue(ex.getMessage().contains("请选择"));
    }

    @Test
    void waterBatch_noFields_rejected() {
        WaterFacility empty = new WaterFacility();
        ApiException ex = assertThrows(ApiException.class,
                () -> waterService.batchUpdate(java.util.Arrays.asList(1L, 2L), empty));
        assertTrue(ex.getMessage().contains("至少填写一个"));
    }

    @Test
    void categoryDelete_withFacilities_blocked() {
        FacilityCategory c = new FacilityCategory();
        c.setId(1010L);
        c.setName("烘干中心");
        when(categoryMapper.selectById(1010L)).thenReturn(c);
        when(categoryMapper.selectCount(any())).thenReturn(0L);   // 无子分类
        when(supportMapper.selectCount(any())).thenReturn(3L);    // 有3个设施
        ApiException ex = assertThrows(ApiException.class, () -> categoryService.delete(1010L));
        assertTrue(ex.getMessage().contains("请先转移或删除"));
    }
}
