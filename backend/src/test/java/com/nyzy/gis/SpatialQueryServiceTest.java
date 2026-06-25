package com.nyzy.gis;

import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.auth.DataScope;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.gis.dto.SpatialQueryRequest;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SpatialQueryServiceTest {

    private final LandParcelMapper parcelMapper = Mockito.mock(LandParcelMapper.class);
    private final WaterFacilityMapper waterMapper = Mockito.mock(WaterFacilityMapper.class);
    private final SupportFacilityMapper supportMapper = Mockito.mock(SupportFacilityMapper.class);
    private final AbandonParcelMapper abandonMapper = Mockito.mock(AbandonParcelMapper.class);
    private final PlantingRecordMapper plantingMapper = Mockito.mock(PlantingRecordMapper.class);
    private final LandQualityMapper qualityMapper = Mockito.mock(LandQualityMapper.class);
    private final DataScope dataScope = Mockito.mock(DataScope.class);

    private final SpatialQueryService service = new SpatialQueryService(
            parcelMapper, waterMapper, supportMapper, abandonMapper, plantingMapper, qualityMapper, dataScope);

    private WaterFacility water(long id, String name, double lng, double lat) {
        WaterFacility f = new WaterFacility();
        f.setId(id);
        f.setName(name);
        f.setLng(BigDecimal.valueOf(lng));
        f.setLat(BigDecimal.valueOf(lat));
        return f;
    }

    @Test
    void rectQuery_filtersPointsInsideBounds() {
        when(waterMapper.selectList(any())).thenReturn(Arrays.asList(
                water(1L, "in", 5, 5), water(2L, "out", 50, 50)));

        SpatialQueryRequest req = new SpatialQueryRequest();
        req.setTargetType("water");
        SpatialQueryRequest.Shape shape = new SpatialQueryRequest.Shape();
        shape.setType("rect");
        shape.setBounds(Arrays.asList(Arrays.asList(0.0, 0.0), Arrays.asList(10.0, 10.0)));
        req.setShape(shape);

        List<Map<String, Object>> result = service.query(req);
        assertEquals(1, result.size());
        assertEquals("in", result.get(0).get("name"));
    }

    @Test
    void circleQuery_filtersByDistance() {
        // 0.01度纬度间距约1.1km
        when(waterMapper.selectList(any())).thenReturn(Arrays.asList(
                water(1L, "near", 129.60, 42.94), water(2L, "far", 130.60, 43.94)));

        SpatialQueryRequest req = new SpatialQueryRequest();
        req.setTargetType("water");
        SpatialQueryRequest.Shape shape = new SpatialQueryRequest.Shape();
        shape.setType("circle");
        shape.setCenter(Arrays.asList(129.60, 42.94));
        shape.setRadius(5000.0); // 5km
        req.setShape(shape);

        List<Map<String, Object>> result = service.query(req);
        assertEquals(1, result.size());
        assertEquals("near", result.get(0).get("name"));
    }

    @Test
    void polygonQuery_parcelUsesCenterPoint() {
        LandParcel p1 = new LandParcel();
        p1.setId(1L); p1.setParcelCode("P1"); p1.setName("地块1");
        p1.setCenterLng(BigDecimal.valueOf(5)); p1.setCenterLat(BigDecimal.valueOf(5));
        LandParcel p2 = new LandParcel();
        p2.setId(2L); p2.setParcelCode("P2"); p2.setName("地块2");
        p2.setCenterLng(BigDecimal.valueOf(50)); p2.setCenterLat(BigDecimal.valueOf(50));
        when(parcelMapper.selectList(any())).thenReturn(Arrays.asList(p1, p2));

        SpatialQueryRequest req = new SpatialQueryRequest();
        req.setTargetType("parcel");
        SpatialQueryRequest.Shape shape = new SpatialQueryRequest.Shape();
        shape.setType("polygon");
        shape.setPoints(Arrays.asList(Arrays.asList(0.0, 0.0), Arrays.asList(10.0, 0.0),
                Arrays.asList(10.0, 10.0), Arrays.asList(0.0, 10.0)));
        req.setShape(shape);

        List<Map<String, Object>> result = service.query(req);
        assertEquals(1, result.size());
        assertEquals("P1", result.get(0).get("code"));
    }

    @Test
    void unsupportedTargetType_throws() {
        SpatialQueryRequest req = new SpatialQueryRequest();
        req.setTargetType("unknown");
        SpatialQueryRequest.Shape shape = new SpatialQueryRequest.Shape();
        shape.setType("rect");
        shape.setBounds(Arrays.asList(Arrays.asList(0.0, 0.0), Arrays.asList(10.0, 10.0)));
        req.setShape(shape);
        assertThrows(ApiException.class, () -> service.query(req));
    }
}
