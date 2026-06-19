package com.nyzy.analysis;

import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
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
    private final AnalysisService service = new AnalysisService(
            plantingMapper, Mockito.mock(AbandonParcelMapper.class), Mockito.mock(LandQualityMapper.class));

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
