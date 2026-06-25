package com.nyzy.resource;

import com.nyzy.auth.DataScope;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.gis.GeoUtil;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.system.AuditLogService;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SupportFacilityServiceTest {

    private final SupportFacilityMapper mapper = Mockito.mock(SupportFacilityMapper.class);
    private final FacilityCategoryMapper categoryMapper = Mockito.mock(FacilityCategoryMapper.class);
    private final LandParcelMapper parcelMapper = Mockito.mock(LandParcelMapper.class);
    private final DataScope dataScope = Mockito.mock(DataScope.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private final SupportFacilityService service = new SupportFacilityService(mapper, categoryMapper, parcelMapper, dataScope, auditLogService);

    private LandParcel parcel(String code, double lng, double lat, String regionPath) {
        LandParcel p = new LandParcel();
        p.setParcelCode(code);
        p.setCenterLng(BigDecimal.valueOf(lng));
        p.setCenterLat(BigDecimal.valueOf(lat));
        p.setRegionPath(regionPath);
        return p;
    }

    private String squareGeoJson(double size) {
        List<double[]> pts = new ArrayList<>(Arrays.asList(
                new double[]{0, 0}, new double[]{size, 0}, new double[]{size, size}, new double[]{0, size}));
        Polygon p = GeoUtil.polygonFromPoints(pts);
        return GeoUtil.toGeoJson(p);
    }

    @Test
    void updateServiceArea_countsCoveredParcelsAndVillages() {
        when(mapper.selectById(1L)).thenReturn(new SupportFacility());
        when(parcelMapper.selectList(any())).thenReturn(Arrays.asList(
                parcel("A", 5, 5, "吉林省/延边州/延吉市/太平镇/太平村"),
                parcel("B", 6, 6, "吉林省/延边州/延吉市/太平镇/太平村"), // 同村
                parcel("C", 7, 7, "吉林省/延边州/延吉市/太平镇/红旗村"), // 不同村
                parcel("D", 50, 50, "吉林省/延边州/延吉市/太平镇/红旗村"))); // 范围外

        service.updateServiceArea(1L, squareGeoJson(10));

        ArgumentCaptor<SupportFacility> captor = ArgumentCaptor.forClass(SupportFacility.class);
        Mockito.verify(mapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getCoverageCount());
        assertEquals(2, captor.getValue().getCoverageVillageCount());
    }

    @Test
    void updateServiceArea_invalidGeometry_throws() {
        when(mapper.selectById(1L)).thenReturn(new SupportFacility());
        String bowtie = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[10,10],[10,0],[0,10],[0,0]]]}";
        assertThrows(ApiException.class, () -> service.updateServiceArea(1L, bowtie));
    }

    private SupportFacility facility(Long id, Long categoryId, double lng, double lat, String auditStatus) {
        SupportFacility f = new SupportFacility();
        f.setId(id);
        f.setName("测试设施");
        f.setCategoryId(categoryId);
        f.setLng(BigDecimal.valueOf(lng));
        f.setLat(BigDecimal.valueOf(lat));
        f.setAuditStatus(auditStatus);
        return f;
    }

    @Test
    void update_categoryChanged_resetsToPending() {
        when(mapper.selectById(10L)).thenReturn(facility(10L, 1L, 129.6, 42.9, "APPROVED"));
        when(mapper.updateById(any())).thenReturn(1);
        SupportFacility upd = facility(10L, 2L, 129.6, 42.9, null); // 分类变了
        service.update(upd);
        assertEquals("PENDING", upd.getAuditStatus());
    }

    @Test
    void update_coordinateChanged_resetsToPending() {
        when(mapper.selectById(10L)).thenReturn(facility(10L, 1L, 129.6, 42.9, "APPROVED"));
        when(mapper.updateById(any())).thenReturn(1);
        SupportFacility upd = facility(10L, 1L, 129.7, 42.9, null); // 经度变了
        service.update(upd);
        assertEquals("PENDING", upd.getAuditStatus());
    }

    @Test
    void update_noKeyFieldChange_keepsAuditStatus() {
        when(mapper.selectById(10L)).thenReturn(facility(10L, 1L, 129.6, 42.9, "APPROVED"));
        when(mapper.updateById(any())).thenReturn(1);
        SupportFacility upd = facility(10L, 1L, 129.6, 42.9, null);
        upd.setPhone("13900000000"); // 仅改非关键字段
        service.update(upd);
        assertEquals("APPROVED", upd.getAuditStatus());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void batchUpdate_noFields_rejected() {
        SupportFacility empty = new SupportFacility();
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(Arrays.asList(1L, 2L), empty));
        assertTrue(ex.getMessage().contains("至少填写一个"));
    }

    @Test
    void batchUpdate_success() {
        SupportFacility old1 = facility(1L, 1L, 129.6, 42.9, "APPROVED"); old1.setVersion(1);
        SupportFacility old2 = facility(2L, 1L, 129.6, 42.9, "APPROVED"); old2.setVersion(3);
        when(mapper.selectById(1L)).thenReturn(old1);
        when(mapper.selectById(2L)).thenReturn(old2);
        when(mapper.updateById(any())).thenReturn(1);
        SupportFacility u = new SupportFacility();
        u.setOperateStatus("停业");
        int n = service.batchUpdate(Arrays.asList(1L, 2L), u);
        assertEquals(2, n);
        Mockito.verify(mapper, Mockito.times(2)).updateById(any()); // 逐行 updateById, 保留乐观锁(@Version)校验
    }

    @Test
    void batchUpdate_versionConflict_skippedNotCountedOrThrown() {
        SupportFacility old1 = facility(1L, 1L, 129.6, 42.9, "APPROVED"); old1.setVersion(1);
        when(mapper.selectById(1L)).thenReturn(old1);
        when(mapper.selectById(2L)).thenReturn(null); // 已被删除
        when(mapper.updateById(any())).thenReturn(0); // 版本冲突
        SupportFacility u = new SupportFacility();
        u.setOperateStatus("停业");
        int n = assertDoesNotThrow(() -> service.batchUpdate(Arrays.asList(1L, 2L), u));
        assertEquals(0, n);
    }

    @Test
    void batchDelete_nonAdmin_forbidden() {
        UserContext.set(new UserContext.CurrentUser(2L, "operator", "operator"));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchDelete(Arrays.asList(1L, 2L), "重复录入"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void batchDelete_success() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
        when(mapper.deleteBatchIds(any())).thenReturn(2);
        int n = service.batchDelete(Arrays.asList(1L, 2L), "批量清理");
        assertEquals(2, n);
    }
}
