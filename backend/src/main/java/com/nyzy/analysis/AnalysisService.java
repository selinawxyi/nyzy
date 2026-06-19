package com.nyzy.analysis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 种植动态分析 (E1 数据分析).
 * 仅统计有效(status=VALID)且未删除的种植记录.
 */
@Service
public class AnalysisService {

    private final PlantingRecordMapper plantingMapper;
    private final AbandonParcelMapper abandonMapper;
    private final LandQualityMapper qualityMapper;

    public AnalysisService(PlantingRecordMapper plantingMapper, AbandonParcelMapper abandonMapper,
                           LandQualityMapper qualityMapper) {
        this.plantingMapper = plantingMapper;
        this.abandonMapper = abandonMapper;
        this.qualityMapper = qualityMapper;
    }

    private List<PlantingRecord> validRecords(Integer year) {
        QueryWrapper<PlantingRecord> w = new QueryWrapper<PlantingRecord>().eq("status", "VALID");
        if (year != null) w.eq("plant_year", year);
        return plantingMapper.selectList(w);
    }

    /** 可选年度列表 (倒序) */
    public List<Integer> years() {
        return validRecords(null).stream()
                .map(PlantingRecord::getPlantYear)
                .distinct().sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /** E1.3 种植结构分析: 指定年度各作物面积占比 + 合理性评价 */
    public Map<String, Object> overview(Integer year) {
        List<PlantingRecord> list = validRecords(year);
        Map<String, Double> areaByCrop = sumAreaByCrop(list);
        double total = areaByCrop.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Long> countByCrop = list.stream()
                .collect(Collectors.groupingBy(PlantingRecord::getCrop, Collectors.counting()));

        List<Map<String, Object>> structure = areaByCrop.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("crop", e.getKey());
                    m.put("area", round(e.getValue()));
                    m.put("ratio", total == 0 ? 0 : round(e.getValue() / total * 100));
                    m.put("count", countByCrop.getOrDefault(e.getKey(), 0L));
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("totalArea", round(total));
        result.put("recordCount", list.size());
        result.put("cropCount", areaByCrop.size());
        result.put("structure", structure);
        result.put("evaluation", evaluateStructure(areaByCrop));
        return result;
    }

    /** E1.2 年度变化分析: 各作物逐年面积 (折线), 含同比变化标识 */
    public Map<String, Object> yearly() {
        List<PlantingRecord> list = validRecords(null);
        List<Integer> sortedYears = list.stream().map(PlantingRecord::getPlantYear)
                .distinct().sorted().collect(Collectors.toList());
        Set<String> crops = list.stream().map(PlantingRecord::getCrop)
                .collect(Collectors.toCollection(TreeSet::new));

        // crop -> year -> area
        Map<String, Map<Integer, Double>> grid = new HashMap<>();
        for (PlantingRecord r : list) {
            grid.computeIfAbsent(r.getCrop(), k -> new HashMap<>())
                    .merge(r.getPlantYear(), toDouble(r.getArea()), Double::sum);
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (String crop : crops) {
            Map<Integer, Double> byYear = grid.getOrDefault(crop, Collections.emptyMap());
            List<Double> data = sortedYears.stream()
                    .map(y -> round(byYear.getOrDefault(y, 0.0))).collect(Collectors.toList());
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("crop", crop);
            s.put("data", data);
            series.add(s);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("years", sortedYears);
        result.put("series", series);
        result.put("changes", computeChanges(grid, sortedYears));
        return result;
    }

    /** E1.1 行政区划分析: 按村组聚合面积与主导作物 */
    public List<Map<String, Object>> byRegion(Integer year) {
        List<PlantingRecord> list = validRecords(year);
        Map<String, List<PlantingRecord>> byVillage = list.stream()
                .collect(Collectors.groupingBy(r -> village(r.getRegionPath())));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<PlantingRecord>> e : byVillage.entrySet()) {
            Map<String, Double> areaByCrop = sumAreaByCrop(e.getValue());
            double total = areaByCrop.values().stream().mapToDouble(Double::doubleValue).sum();
            String mainCrop = areaByCrop.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("region", e.getKey());
            m.put("totalArea", round(total));
            m.put("mainCrop", mainCrop);
            m.put("cropCount", areaByCrop.size());
            result.add(m);
        }
        result.sort((a, b) -> Double.compare((double) b.get("totalArea"), (double) a.get("totalArea")));
        return result;
    }

    /** E1.5 耕地利用类型: 在耕 / 撂荒 (季节性闲置/非农化暂按0, 需更多数据源) */
    public List<Map<String, Object>> landUse(Integer year) {
        long planted = validRecords(year).stream()
                .map(PlantingRecord::getParcelCode).distinct().count();
        long abandoned = abandonMapper.selectCount(new QueryWrapper<AbandonParcel>()
                .ne("govern_status", "GOVERNED")
                .ne("govern_status", "REJECTED"));
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(landUseItem("在耕", planted));
        result.add(landUseItem("撂荒", abandoned));
        result.add(landUseItem("季节性闲置", 0));
        result.add(landUseItem("非农化", 0));
        return result;
    }

    /**
     * E1.2 种植结构流转桑基图: 基期年→报告期年, 按地块追踪作物流转.
     */
    public Map<String, Object> sankey(Integer fromYear, Integer toYear) {
        if (fromYear == null || toYear == null) {
            List<Integer> ys = years();           // 倒序
            if (ys.size() >= 2) { toYear = ys.get(0); fromYear = ys.get(ys.size() - 1); }
            else if (ys.size() == 1) { fromYear = toYear = ys.get(0); }
        }
        List<PlantingRecord> all = validRecords(null);
        Map<String, String> fromCrop = new HashMap<>();
        Map<String, String> toCrop = new HashMap<>();
        Map<String, Double> toArea = new HashMap<>();
        for (PlantingRecord r : all) {
            if (Objects.equals(r.getPlantYear(), fromYear)) fromCrop.put(r.getParcelCode(), r.getCrop());
            if (Objects.equals(r.getPlantYear(), toYear)) {
                toCrop.put(r.getParcelCode(), r.getCrop());
                toArea.merge(r.getParcelCode(), toDouble(r.getArea()), Double::sum);
            }
        }
        Map<String, Double> linkMap = new LinkedHashMap<>();
        Set<String> nodeNames = new LinkedHashSet<>();
        for (String code : toCrop.keySet()) {
            if (!fromCrop.containsKey(code)) continue;
            String src = fromYear + "·" + fromCrop.get(code);
            String tgt = toYear + "·" + toCrop.get(code);
            nodeNames.add(src);
            nodeNames.add(tgt);
            linkMap.merge(src + "||" + tgt, toArea.getOrDefault(code, 0.0), Double::sum);
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String n : nodeNames) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", n);
            nodes.add(m);
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Double> e : linkMap.entrySet()) {
            String[] st = e.getKey().split("\\|\\|");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", st[0]);
            m.put("target", st[1]);
            m.put("value", round(e.getValue()));
            links.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromYear", fromYear);
        result.put("toYear", toYear);
        result.put("nodes", nodes);
        result.put("links", links);
        return result;
    }

    /** 多区域横向对比: 各村组各作物面积(堆叠对比) */
    public Map<String, Object> regionCompare(Integer year) {
        List<PlantingRecord> list = validRecords(year);
        Map<String, List<PlantingRecord>> byVillage = list.stream()
                .collect(Collectors.groupingBy(r -> village(r.getRegionPath())));
        Set<String> crops = list.stream().map(PlantingRecord::getCrop)
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> regions = new ArrayList<>(byVillage.keySet());

        List<Map<String, Object>> series = new ArrayList<>();
        for (String crop : crops) {
            List<Double> data = new ArrayList<>();
            for (String region : regions) {
                double area = byVillage.get(region).stream()
                        .filter(r -> crop.equals(r.getCrop()))
                        .mapToDouble(r -> toDouble(r.getArea())).sum();
                data.add(round(area));
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("crop", crop);
            s.put("data", data);
            series.add(s);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("regions", regions);
        result.put("series", series);
        return result;
    }

    /**
     * E1.4 优势产区识别: 针对某作物, 按村组综合评分(平均产量/产量稳定性/种植规模/耕地质量)
     * 计算优势度(0-100)并排名, 给出种植适宜性评级.
     */
    public Map<String, Object> advantageZones(String crop) {
        List<PlantingRecord> list = validRecords(null).stream()
                .filter(r -> crop == null || crop.equals(r.getCrop()))
                .collect(Collectors.toList());
        Map<String, List<PlantingRecord>> byVillage = list.stream()
                .collect(Collectors.groupingBy(r -> village(r.getRegionPath())));

        // 各村平均地力等级 (1最好), 用于质量得分
        Map<String, Double> gradeByVillage = avgGradeByVillage();

        List<Map<String, Object>> raw = new ArrayList<>();
        double maxYield = 0, maxArea = 0;
        for (Map.Entry<String, List<PlantingRecord>> e : byVillage.entrySet()) {
            List<Double> yields = new ArrayList<>();
            double area = 0;
            for (PlantingRecord r : e.getValue()) {
                if (r.getYieldPerMu() != null) yields.add(r.getYieldPerMu().doubleValue());
                area += toDouble(r.getArea());
            }
            double avgYield = yields.isEmpty() ? 0 : yields.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double stability = stability(yields);
            Double grade = gradeByVillage.get(e.getKey());
            Map<String, Object> m = new HashMap<>();
            m.put("region", e.getKey());
            m.put("avgYield", round(avgYield));
            m.put("totalArea", round(area));
            m.put("stability", round(stability * 100));
            m.put("avgGrade", grade == null ? null : round(grade));
            m.put("_yield", avgYield);
            m.put("_area", area);
            m.put("_stability", stability);
            m.put("_grade", grade);
            raw.add(m);
            maxYield = Math.max(maxYield, avgYield);
            maxArea = Math.max(maxArea, area);
        }

        for (Map<String, Object> m : raw) {
            double yieldScore = maxYield == 0 ? 0 : (double) m.get("_yield") / maxYield * 100;
            double areaScore = maxArea == 0 ? 0 : (double) m.get("_area") / maxArea * 100;
            double stabScore = (double) m.get("_stability") * 100;
            Double grade = (Double) m.get("_grade");
            double qualityScore = grade == null ? 60 : (11 - grade) / 10.0 * 100; // 1等→100
            double score = yieldScore * 0.4 + areaScore * 0.2 + stabScore * 0.2 + qualityScore * 0.2;
            m.put("score", round(score));
            m.put("suitability", score >= 75 ? "高适宜" : score >= 50 ? "中适宜" : "低适宜");
            m.keySet().removeIf(k -> k.startsWith("_"));
        }
        raw.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("crop", crop);
        result.put("zones", raw);
        // 推荐: 取高适宜的前若干
        List<String> top = raw.stream()
                .filter(m -> "高适宜".equals(m.get("suitability")))
                .map(m -> (String) m.get("region")).collect(Collectors.toList());
        if (top.isEmpty() && !raw.isEmpty()) top.add((String) raw.get(0).get("region"));
        result.put("recommendation", top.isEmpty() ? "暂无足够数据"
                : String.format("%s优势产区: %s，建议规划为优质%s产业带", crop, String.join("、", top), crop));
        return result;
    }

    private Map<String, Double> avgGradeByVillage() {
        List<LandQuality> qs = qualityMapper.selectList(null);
        Map<String, List<Integer>> byV = new HashMap<>();
        for (LandQuality q : qs) {
            if (q.getGrade() == null) continue;
            byV.computeIfAbsent(village(q.getRegionPath()), k -> new ArrayList<>()).add(q.getGrade());
        }
        Map<String, Double> avg = new HashMap<>();
        byV.forEach((k, v) -> avg.put(k, v.stream().mapToInt(Integer::intValue).average().orElse(0)));
        return avg;
    }

    private double stability(List<Double> values) {
        if (values.size() < 2) return 1.0;  // 数据不足按稳定处理
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (mean == 0) return 0;
        double var = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double cv = Math.sqrt(var) / mean;
        return Math.max(0, 1 - cv);
    }

    // ---------------- 纯函数 (可单元测试) ----------------

    /** 种植结构合理性评价 */
    public String evaluateStructure(Map<String, Double> areaByCrop) {
        double total = areaByCrop.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return "暂无种植数据";
        String dominant = areaByCrop.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        double dominantRatio = dominant == null ? 0 : areaByCrop.get(dominant) / total * 100;
        double beanRatio = areaByCrop.getOrDefault("大豆", 0.0) / total * 100;

        List<String> tips = new ArrayList<>();
        if (dominantRatio > 70) {
            tips.add(String.format("「%s」占比达 %.1f%%，结构略显单一，存在病虫害爆发风险", dominant, dominantRatio));
        }
        if (beanRatio < 10) {
            tips.add("豆科作物占比偏低，耕地用养结合不足，建议适当增加大豆种植");
        }
        if (tips.isEmpty()) tips.add("种植结构较为均衡，用养结合良好");
        return String.join("；", tips);
    }

    // ---------------- 内部工具 ----------------

    private Map<String, Double> sumAreaByCrop(List<PlantingRecord> list) {
        Map<String, Double> map = new HashMap<>();
        for (PlantingRecord r : list) {
            map.merge(r.getCrop(), toDouble(r.getArea()), Double::sum);
        }
        return map;
    }

    private List<Map<String, Object>> computeChanges(Map<String, Map<Integer, Double>> grid, List<Integer> years) {
        if (years.size() < 2) return Collections.emptyList();
        int base = years.get(0), latest = years.get(years.size() - 1);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Double>> e : grid.entrySet()) {
            double from = e.getValue().getOrDefault(base, 0.0);
            double to = e.getValue().getOrDefault(latest, 0.0);
            if (from == 0 && to == 0) continue;
            double pct = from == 0 ? 100 : (to - from) / from * 100;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("crop", e.getKey());
            m.put("from", round(from));
            m.put("to", round(to));
            m.put("changePct", round(pct));
            m.put("trend", pct > 20 ? "UP" : pct < -20 ? "DOWN" : "FLAT");
            changes.add(m);
        }
        return changes;
    }

    private Map<String, Object> landUseItem(String type, long count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("count", count);
        return m;
    }

    private String village(String regionPath) {
        if (regionPath == null) return "未知";
        String[] parts = regionPath.split("/");
        // 省/州/市/乡镇/村/组 -> 取「村」(index 4), 不足则取末段
        if (parts.length >= 5) return parts[4];
        return parts[parts.length - 1];
    }

    private double toDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
