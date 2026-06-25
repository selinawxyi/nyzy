package com.nyzy.land;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.gis.GeoUtil;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelHistoryMapper;
import com.nyzy.land.mapper.LandParcelMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ParcelServiceTest {

    private final LandParcelMapper mapper = Mockito.mock(LandParcelMapper.class);
    private final LandParcelHistoryMapper historyMapper = Mockito.mock(LandParcelHistoryMapper.class);
    private final PlantingRecordMapper plantingMapper = Mockito.mock(PlantingRecordMapper.class);
    private final LandQualityMapper qualityMapper = Mockito.mock(LandQualityMapper.class);
    private final AbandonParcelMapper abandonMapper = Mockito.mock(AbandonParcelMapper.class);

    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final ParcelService service = new ParcelService(
            mapper, historyMapper, plantingMapper, qualityMapper, abandonMapper, new ObjectMapper(), dataScope);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private LandParcel parcel(Long id, String code) {
        LandParcel p = new LandParcel();
        p.setId(id);
        p.setParcelCode(code);
        p.setName("测试地块");
        return p;
    }

    @Test
    void create_duplicateCode_rejected() {
        when(mapper.selectCount(any())).thenReturn(1L);   // 编码已存在
        ApiException ex = assertThrows(ApiException.class, () -> service.create(parcel(null, "JY-T001")));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void update_concurrentModification_rejected() {
        LandParcel old = parcel(1L, "JY-T001");
        when(mapper.selectById(1L)).thenReturn(old);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.updateById(any())).thenReturn(0); // 模拟 version 不匹配(已被他人修改)

        LandParcel upd = parcel(1L, "JY-T001");
        upd.setName("改个名字");
        ApiException ex = assertThrows(ApiException.class, () -> service.update(upd, null));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已被他人修改"));
    }

    @Test
    void update_nameAndRegionChanged_cascadesToRelatedTables() {
        LandParcel old = parcel(1L, "JY-T001");
        old.setRegionId(100L);
        old.setRegionPath("吉林省/延边州/旧路径");
        LandParcel upd = parcel(1L, "JY-T001");
        upd.setName("地块新名字");
        upd.setRegionId(200L);
        upd.setRegionPath("吉林省/延边州/新路径");

        when(mapper.selectById(1L)).thenReturn(old, upd);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.updateById(any())).thenReturn(1);

        service.update(upd, null);

        Mockito.verify(plantingMapper).update(any(), any());
        Mockito.verify(qualityMapper).update(any(), any());
        Mockito.verify(abandonMapper).update(any(), any());
    }

    @Test
    void update_noDenormalizedFieldChanged_doesNotCascade() {
        LandParcel old = parcel(1L, "JY-T001");
        LandParcel upd = parcel(1L, "JY-T001");
        upd.setContractorName("新承包方"); // 非冗余展示字段变更, 不应触发跨表回写

        when(mapper.selectById(1L)).thenReturn(old, upd);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.updateById(any())).thenReturn(1);

        service.update(upd, null);

        Mockito.verify(plantingMapper, Mockito.never()).update(any(), any());
        Mockito.verify(qualityMapper, Mockito.never()).update(any(), any());
        Mockito.verify(abandonMapper, Mockito.never()).update(any(), any());
    }

    @Test
    void update_keyFieldChangeWithoutReason_rejected() {
        LandParcel old = parcel(1L, "JY-T001");
        old.setContractorCode("OLD");
        when(mapper.selectById(1L)).thenReturn(old);
        when(mapper.selectCount(any())).thenReturn(0L);

        LandParcel upd = parcel(1L, "JY-T001");
        upd.setContractorCode("NEW");   // 关键字段变更
        ApiException ex = assertThrows(ApiException.class, () -> service.update(upd, null));
        assertTrue(ex.getMessage().contains("变更原因"));
    }

    @Test
    void delete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, "test"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void importUpdate_codeNotFound_rejected() {
        when(mapper.selectOne(any())).thenReturn(null);
        LandParcel patch = parcel(null, "NOEXIST");
        patch.setContractorName("张三");
        ApiException ex = assertThrows(ApiException.class, () -> service.importUpdate(patch));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void importUpdate_noFields_rejected() {
        when(mapper.selectOne(any())).thenReturn(parcel(1L, "JY-T001"));
        LandParcel patch = parcel(null, "JY-T001");   // 仅编码, 无可更新字段
        ApiException ex = assertThrows(ApiException.class, () -> service.importUpdate(patch));
        assertTrue(ex.getMessage().contains("无可更新字段"));
    }

    @Test
    void delete_withRelatedData_blocked() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(mapper.selectById(1L)).thenReturn(parcel(1L, "JY-T001"));
        when(plantingMapper.selectCount(any())).thenReturn(2L);   // 有种植关联
        when(qualityMapper.selectCount(any())).thenReturn(0L);
        when(abandonMapper.selectCount(any())).thenReturn(1L);
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, "清理"));
        assertTrue(ex.getMessage().contains("不可删除"));
    }

    private String squareGeoJson(double x0, double y0, double size) {
        List<double[]> pts = new ArrayList<>(Arrays.asList(
                new double[]{x0, y0}, new double[]{x0 + size, y0},
                new double[]{x0 + size, y0 + size}, new double[]{x0, y0 + size}));
        Polygon p = GeoUtil.polygonFromPoints(pts);
        return GeoUtil.toGeoJson(p);
    }

    // ---------------- 几何编辑: 分割 ----------------

    @Test
    void split_lineDoesNotCross_throws() {
        LandParcel old = parcel(1L, "JY-T001");
        old.setBoundary(squareGeoJson(0, 0, 10));
        when(mapper.selectById(1L)).thenReturn(old);
        when(mapper.selectCount(any())).thenReturn(0L);

        String lineFarAway = "{\"type\":\"LineString\",\"coordinates\":[[100,100],[200,200]]}";
        ApiException ex = assertThrows(ApiException.class,
                () -> service.split(1L, lineFarAway, "JY-T001-2", "测绘分割"));
        assertTrue(ex.getMessage().contains("分割"));
    }

    @Test
    void split_success_createsNewParcelAndUpdatesOriginal() {
        LandParcel old = parcel(1L, "JY-T001");
        old.setBoundary(squareGeoJson(0, 0, 10));
        old.setContractorName("张三");
        when(mapper.selectById(1L)).thenReturn(old);
        when(mapper.selectCount(any())).thenReturn(0L); // 新编码不存在

        String line = "{\"type\":\"LineString\",\"coordinates\":[[5,-1],[5,11]]}";
        service.split(1L, line, "JY-T001-2", "测绘分割");

        ArgumentCaptor<LandParcel> insertCaptor = ArgumentCaptor.forClass(LandParcel.class);
        Mockito.verify(mapper).insert(insertCaptor.capture());
        assertEquals("JY-T001-2", insertCaptor.getValue().getParcelCode());
        assertEquals("张三", insertCaptor.getValue().getContractorName());

        ArgumentCaptor<LandParcel> updateCaptor = ArgumentCaptor.forClass(LandParcel.class);
        Mockito.verify(mapper).updateById(updateCaptor.capture());
        assertEquals(1L, updateCaptor.getValue().getId());
        assertNotNull(updateCaptor.getValue().getBoundary());
    }

    @Test
    void split_newCodeExists_rejected() {
        LandParcel old = parcel(1L, "JY-T001");
        old.setBoundary(squareGeoJson(0, 0, 10));
        when(mapper.selectById(1L)).thenReturn(old);
        when(mapper.selectCount(any())).thenReturn(1L); // 新编码已存在

        String line = "{\"type\":\"LineString\",\"coordinates\":[[5,-1],[5,11]]}";
        ApiException ex = assertThrows(ApiException.class, () -> service.split(1L, line, "JY-T001-2", "测绘分割"));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    // ---------------- 几何编辑: 合并 ----------------

    @Test
    void merge_lessThanTwoIds_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.merge(java.util.Collections.singletonList(1L), "NEW-CODE", "合并"));
        assertTrue(ex.getMessage().contains("至少选择2个"));
    }

    @Test
    void merge_adjacentParcels_success() {
        LandParcel a = parcel(1L, "JY-A");
        a.setBoundary(squareGeoJson(0, 0, 10));
        a.setArea(java.math.BigDecimal.TEN);
        LandParcel b = parcel(2L, "JY-B");
        b.setBoundary(squareGeoJson(10, 0, 10)); // 共边 x=10
        b.setArea(java.math.BigDecimal.ONE);
        when(mapper.selectBatchIds(any())).thenReturn(Arrays.asList(a, b));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectById(1L)).thenReturn(a);
        when(mapper.selectById(2L)).thenReturn(b);

        service.merge(Arrays.asList(1L, 2L), "JY-MERGED", "地块合并");

        ArgumentCaptor<LandParcel> insertCaptor = ArgumentCaptor.forClass(LandParcel.class);
        Mockito.verify(mapper).insert(insertCaptor.capture());
        assertEquals("JY-MERGED", insertCaptor.getValue().getParcelCode());

        ArgumentCaptor<LandParcel> updateCaptor = ArgumentCaptor.forClass(LandParcel.class);
        Mockito.verify(mapper, Mockito.times(2)).updateById(updateCaptor.capture());
        for (LandParcel u : updateCaptor.getAllValues()) {
            assertEquals("MERGED", u.getMergeStatus());
            assertEquals("JY-MERGED", u.getMergedIntoCode());
        }
    }

    @Test
    void merge_nonAdjacentParcels_rejected() {
        LandParcel a = parcel(1L, "JY-A");
        a.setBoundary(squareGeoJson(0, 0, 10));
        LandParcel b = parcel(2L, "JY-B");
        b.setBoundary(squareGeoJson(100, 100, 10)); // 不相邻
        when(mapper.selectBatchIds(any())).thenReturn(Arrays.asList(a, b));
        when(mapper.selectCount(any())).thenReturn(0L);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.merge(Arrays.asList(1L, 2L), "JY-MERGED", "地块合并"));
        assertTrue(ex.getMessage().contains("相邻"));
    }

    @Test
    void batchUpdate_noIds_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(null, new LandParcel(), "调整"));
        assertTrue(ex.getMessage().contains("请选择"));
    }

    @Test
    void batchUpdate_noFields_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(Arrays.asList(1L, 2L), new LandParcel(), "调整"));
        assertTrue(ex.getMessage().contains("至少填写一个"));
    }

    @Test
    void batchUpdate_keyFieldNoReason_rejected() {
        LandParcel patch = new LandParcel();
        patch.setLandUse("园地");
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(Arrays.asList(1L, 2L), patch, ""));
        assertTrue(ex.getMessage().contains("关键变更"));
    }

    @Test
    void batchUpdate_success_updatesEachRowWithVersionAndHistory() {
        LandParcel old1 = parcel(1L, "JY-A"); old1.setVersion(1);
        LandParcel old2 = parcel(2L, "JY-B"); old2.setVersion(2);
        when(mapper.selectById(1L)).thenReturn(old1, old1);
        when(mapper.selectById(2L)).thenReturn(old2, old2);
        when(mapper.updateById(any())).thenReturn(1);
        LandParcel patch = new LandParcel();
        patch.setLandUse("园地");
        int n = service.batchUpdate(Arrays.asList(1L, 2L), patch, "整村地类调整");
        assertEquals(2, n);
        Mockito.verify(mapper, Mockito.times(2)).updateById(any()); // 逐行 updateById, 保留乐观锁(@Version)校验
        Mockito.verify(historyMapper, Mockito.times(2)).insert(any());
    }

    @Test
    void batchUpdate_versionConflict_skippedNotCountedOrThrown() {
        LandParcel old1 = parcel(1L, "JY-A"); old1.setVersion(1);
        when(mapper.selectById(1L)).thenReturn(old1);
        when(mapper.selectById(2L)).thenReturn(null); // 已被删除
        when(mapper.updateById(any())).thenReturn(0); // 版本冲突
        LandParcel patch = new LandParcel();
        patch.setContractorName("张三");
        int n = assertDoesNotThrow(() -> service.batchUpdate(Arrays.asList(1L, 2L), patch, null));
        assertEquals(0, n);
    }
}
