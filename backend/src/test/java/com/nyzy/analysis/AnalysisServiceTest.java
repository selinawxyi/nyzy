package com.nyzy.analysis;

import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AnalysisServiceTest {

    private final PlantingRecordMapper plantingMapper = Mockito.mock(PlantingRecordMapper.class);
    private final AbandonParcelMapper abandonMapper = Mockito.mock(AbandonParcelMapper.class);
    private final LandParcelMapper parcelMapper = Mockito.mock(LandParcelMapper.class);
    private final AnalysisService service = new AnalysisService(
            plantingMapper, abandonMapper, Mockito.mock(LandQualityMapper.class), parcelMapper);

    private LandParcel parcel(String code, String landUse) {
        LandParcel p = new LandParcel();
        p.setParcelCode(code);
        p.setLandUse(landUse);
        return p;
    }

    private PlantingRecord rec(String code, int year, String crop, String region, double area) {
        PlantingRecord r = new PlantingRecord();
        r.setParcelCode(code);
        r.setPlantYear(year);
        r.setCrop(crop);
        r.setRegionPath(region);
        r.setArea(BigDecimal.valueOf(area));
        return r;
    }

    @Test
    void evaluate_dominantCropOver70_warnsMonoculture() {
        Map<String, Double> m = new HashMap<>();
        m.put("水稻", 80.0);
        m.put("玉米", 20.0);
        String r = service.evaluateStructure(m);
        assertTrue(r.contains("单一"));
        assertTrue(r.contains("病虫害"));
    }

    @Test
    void evaluate_lowBean_suggestsSoybean() {
        Map<String, Double> m = new HashMap<>();
        m.put("水稻", 50.0);
        m.put("玉米", 45.0);
        m.put("大豆", 5.0);    // 5% < 10%
        String r = service.evaluateStructure(m);
        assertTrue(r.contains("大豆"));
    }

    @Test
    void evaluate_balanced_ok() {
        Map<String, Double> m = new HashMap<>();
        m.put("水稻", 40.0);
        m.put("玉米", 35.0);
        m.put("大豆", 25.0);   // 25% bean, no dominant>70
        String r = service.evaluateStructure(m);
        assertTrue(r.contains("均衡"));
    }

    @Test
    void evaluate_empty_returnsNoData() {
        assertEquals("暂无种植数据", service.evaluateStructure(new HashMap<>()));
    }

    @Test
    void sankey_tracksCropTransitionPerParcel() {
        String region = "吉林省/延边州/延吉市/太平镇/太平村/一组";
        // 同一地块 P1: 2021 水稻 -> 2024 大豆
        List<PlantingRecord> data = Arrays.asList(
                rec("P1", 2021, "水稻", region, 20),
                rec("P1", 2024, "大豆", region, 20));
        when(plantingMapper.selectList(any())).thenReturn(data);
        Map<String, Object> r = service.sankey(2021, 2024);
        List<?> links = (List<?>) r.get("links");
        assertEquals(1, links.size());
        Map<?, ?> link = (Map<?, ?>) links.get(0);
        assertEquals("2021·水稻", link.get("source"));
        assertEquals("2024·大豆", link.get("target"));
    }

    @Test
    void landUse_classifiesCultivatedAbandonedIdleAndNonFarm() {
        // P1 在耕, P2 撂荒, P3 用途已转为非农业用地, P4 既不在耕也未登记撂荒 -> 季节性闲置
        when(plantingMapper.selectList(any())).thenReturn(Arrays.asList(rec("P1", 2024, "水稻", "省/州/市/镇/村", 20)));
        AbandonParcel ab = new AbandonParcel();
        ab.setParcelCode("P2");
        ab.setGovernStatus("UNGOVERNED");
        when(abandonMapper.selectList(any())).thenReturn(Arrays.asList(ab));
        when(parcelMapper.selectList(any())).thenReturn(Arrays.asList(
                parcel("P1", "基本农田"), parcel("P2", "一般耕地"),
                parcel("P3", "建设用地"), parcel("P4", "基本农田")));

        List<Map<String, Object>> result = service.landUse(2024);
        Map<String, Object> byType = new HashMap<>();
        for (Map<String, Object> m : result) byType.put((String) m.get("type"), m.get("count"));

        assertEquals(1L, byType.get("在耕"));
        assertEquals(1L, byType.get("撂荒"));
        assertEquals(1L, byType.get("季节性闲置"));
        assertEquals(1L, byType.get("非农化"));
    }

    @Test
    void regionCompare_groupsByVillageAndCrop() {
        when(plantingMapper.selectList(any())).thenReturn(Arrays.asList(
                rec("P1", 2024, "水稻", "省/州/市/镇/太平村/一组", 20),
                rec("P2", 2024, "玉米", "省/州/市/乡/安全村/一组", 30)));
        Map<String, Object> r = service.regionCompare(2024);
        List<?> regions = (List<?>) r.get("regions");
        assertTrue(regions.contains("太平村"));
        assertTrue(regions.contains("安全村"));
    }
}
