package com.nyzy.cultivation;

import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
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
    private final PlantingService service = new PlantingService(mapper, parcelMapper, dataScope);

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
}
