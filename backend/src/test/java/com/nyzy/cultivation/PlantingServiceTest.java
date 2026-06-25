package com.nyzy.cultivation;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.system.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlantingServiceTest {

    private final PlantingRecordMapper mapper = Mockito.mock(PlantingRecordMapper.class);
    private final LandParcelMapper parcelMapper = Mockito.mock(LandParcelMapper.class);
    private final com.nyzy.auth.DataScope dataScope = Mockito.mock(com.nyzy.auth.DataScope.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private final PlantingService service = new PlantingService(mapper, parcelMapper, dataScope, auditLogService);

    @BeforeEach
    void setUp() {
        UserContext.set(new UserContext.CurrentUser(1L, "admin", "admin"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private PlantingRecord rec(String code, int year, String crop) {
        PlantingRecord r = new PlantingRecord();
        r.setParcelCode(code);
        r.setPlantYear(year);
        r.setCrop(crop);
        return r;
    }

    @Test
    void create_futureYear_rejected() {
        PlantingRecord r = rec("JY-T001", Year.now().getValue() + 1, "水稻");
        ApiException ex = assertThrows(ApiException.class, () -> service.create(r));
        assertTrue(ex.getMessage().contains("未来年份"));
    }

    @Test
    void create_areaExceedsParcel_rejected() {
        PlantingRecord r = rec("JY-T001", 2024, "水稻");
        r.setArea(new BigDecimal("50"));
        LandParcel parcel = new LandParcel();
        parcel.setParcelCode("JY-T001");
        parcel.setArea(new BigDecimal("24.6"));
        when(parcelMapper.selectOne(any())).thenReturn(parcel);
        ApiException ex = assertThrows(ApiException.class, () -> service.create(r));
        assertTrue(ex.getMessage().contains("超过确权面积"));
    }

    @Test
    void update_harvestedYieldChange_rejected() {
        PlantingRecord old = rec("JY-T001", 2023, "水稻");
        old.setId(1L);
        old.setActualHarvestDate(LocalDate.of(2023, 9, 28));
        old.setYieldPerMu(new BigDecimal("520"));
        when(mapper.selectById(1L)).thenReturn(old);

        PlantingRecord upd = rec("JY-T001", 2023, "水稻");
        upd.setId(1L);
        upd.setYieldPerMu(new BigDecimal("600"));   // 改产量
        ApiException ex = assertThrows(ApiException.class, () -> service.update(upd));
        assertTrue(ex.getMessage().contains("产量"));
    }

    @Test
    void delete_withoutReason_rejected() {
        ApiException ex = assertThrows(ApiException.class, () -> service.delete(1L, ""));
        assertTrue(ex.getMessage().contains("删除原因"));
    }

    @Test
    void batchUpdate_noFields_rejected() {
        PlantingRecord empty = new PlantingRecord();
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchUpdate(java.util.Arrays.asList(1L, 2L), empty));
        assertTrue(ex.getMessage().contains("至少填写一个"));
    }

    @Test
    void batchUpdate_success() {
        PlantingRecord old1 = rec("JY-T001", 2024, "水稻"); old1.setId(1L); old1.setVersion(1);
        PlantingRecord old2 = rec("JY-T002", 2024, "水稻"); old2.setId(2L); old2.setVersion(4);
        when(mapper.selectById(1L)).thenReturn(old1);
        when(mapper.selectById(2L)).thenReturn(old2);
        when(mapper.updateById(any())).thenReturn(1);
        PlantingRecord u = new PlantingRecord();
        u.setReporter("张三");
        int n = service.batchUpdate(java.util.Arrays.asList(1L, 2L), u);
        assertEquals(2, n);
        Mockito.verify(mapper, Mockito.times(2)).updateById(any()); // 逐行 updateById, 保留乐观锁(@Version)校验
    }

    @Test
    void batchUpdate_versionConflict_skippedNotCountedOrThrown() {
        PlantingRecord old1 = rec("JY-T001", 2024, "水稻"); old1.setId(1L); old1.setVersion(1);
        when(mapper.selectById(1L)).thenReturn(old1);
        when(mapper.selectById(2L)).thenReturn(null); // 已被删除
        when(mapper.updateById(any())).thenReturn(0); // 版本冲突
        PlantingRecord u = new PlantingRecord();
        u.setReporter("张三");
        int n = assertDoesNotThrow(() -> service.batchUpdate(java.util.Arrays.asList(1L, 2L), u));
        assertEquals(0, n);
    }

    @Test
    void batchDelete_noReason_rejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.batchDelete(java.util.Arrays.asList(1L, 2L), ""));
        assertTrue(ex.getMessage().contains("删除原因"));
    }

    @Test
    void batchDelete_success() {
        when(mapper.deleteBatchIds(any())).thenReturn(2);
        int n = service.batchDelete(java.util.Arrays.asList(1L, 2L), "批量清理");
        assertEquals(2, n);
        Mockito.verify(mapper).update(Mockito.isNull(), any());
    }
}
