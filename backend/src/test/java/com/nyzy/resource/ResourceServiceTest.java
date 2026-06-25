package com.nyzy.resource;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.resource.entity.FacilityCategory;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import com.nyzy.system.AuditLogService;
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
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private final WaterFacilityService waterService = new WaterFacilityService(waterMapper, dataScope, auditLogService);
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
        when(waterMapper.updateById(any())).thenReturn(1);
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
    void waterBatch_success_updatesEachRowWithVersion() {
        WaterFacility old1 = water("机井"); old1.setId(1L); old1.setVersion(1);
        WaterFacility old2 = water("泵站"); old2.setId(2L); old2.setVersion(2);
        when(waterMapper.selectById(1L)).thenReturn(old1);
        when(waterMapper.selectById(2L)).thenReturn(old2);
        when(waterMapper.updateById(any())).thenReturn(1);
        WaterFacility u = new WaterFacility();
        u.setManager("李四");
        int n = waterService.batchUpdate(java.util.Arrays.asList(1L, 2L), u);
        assertEquals(2, n);
        Mockito.verify(waterMapper, Mockito.times(2)).updateById(any()); // 逐行 updateById, 保留乐观锁(@Version)校验
    }

    @Test
    void waterBatchDelete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class,
                () -> waterService.batchDelete(java.util.Arrays.asList(1L, 2L), "重复录入"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void waterBatchDelete_noReason_rejected() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        ApiException ex = assertThrows(ApiException.class,
                () -> waterService.batchDelete(java.util.Arrays.asList(1L, 2L), " "));
        assertTrue(ex.getMessage().contains("删除原因"));
    }

    @Test
    void waterBatchDelete_success() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(waterMapper.deleteBatchIds(any())).thenReturn(2);
        int n = waterService.batchDelete(java.util.Arrays.asList(1L, 2L), "批量清理废弃设施");
        assertEquals(2, n);
        Mockito.verify(waterMapper).update(Mockito.isNull(), any());
        Mockito.verify(waterMapper).deleteBatchIds(java.util.Arrays.asList(1L, 2L));
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

    private FacilityCategory category(Long id, Long parentId, String name) {
        FacilityCategory c = new FacilityCategory();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        return c;
    }

    @Test
    void categoryDeleteWithTransfer_noTarget_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> categoryService.deleteWithTransfer(1010L, null));
        assertTrue(ex.getMessage().contains("请选择转移目标分类"));
    }

    @Test
    void categoryDeleteWithTransfer_targetIsRoot_rejected() {
        when(categoryMapper.selectById(1010L)).thenReturn(category(1010L, 1L, "烘干中心"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(supportMapper.selectCount(any())).thenReturn(3L);
        when(categoryMapper.selectById(1020L)).thenReturn(category(1020L, 0L, "加工类")); // 一级分类
        ApiException ex = assertThrows(ApiException.class,
                () -> categoryService.deleteWithTransfer(1010L, 1020L));
        assertTrue(ex.getMessage().contains("二级分类"));
    }

    @Test
    void categoryDeleteWithTransfer_success() {
        when(categoryMapper.selectById(1010L)).thenReturn(category(1010L, 1L, "烘干中心"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(supportMapper.selectCount(any())).thenReturn(3L);
        when(categoryMapper.selectById(1011L)).thenReturn(category(1011L, 1L, "仓储中心"));
        categoryService.deleteWithTransfer(1010L, 1011L);
        Mockito.verify(supportMapper).update(Mockito.isNull(), any());
        Mockito.verify(categoryMapper).deleteById(1010L);
    }
}
