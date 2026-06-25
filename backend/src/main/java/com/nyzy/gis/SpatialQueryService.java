package com.nyzy.gis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.auth.DataScope;
import com.nyzy.common.ApiException;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.gis.dto.SpatialQueryRequest;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * 空间范围查询(矩形/圆/多边形画图查询), 适用于水利/配套/确权地块/种植/质量/撂荒.
 * 当前数据量小(几十~上百条/表), 全表取候选 + 内存几何过滤, 不引入数据库空间索引.
 */
@Service
public class SpatialQueryService {

    private final LandParcelMapper parcelMapper;
    private final WaterFacilityMapper waterMapper;
    private final SupportFacilityMapper supportMapper;
    private final AbandonParcelMapper abandonMapper;
    private final PlantingRecordMapper plantingMapper;
    private final LandQualityMapper qualityMapper;
    private final DataScope dataScope;

    public SpatialQueryService(LandParcelMapper parcelMapper, WaterFacilityMapper waterMapper,
                                SupportFacilityMapper supportMapper, AbandonParcelMapper abandonMapper,
                                PlantingRecordMapper plantingMapper, LandQualityMapper qualityMapper,
                                DataScope dataScope) {
        this.parcelMapper = parcelMapper;
        this.waterMapper = waterMapper;
        this.supportMapper = supportMapper;
        this.abandonMapper = abandonMapper;
        this.plantingMapper = plantingMapper;
        this.qualityMapper = qualityMapper;
        this.dataScope = dataScope;
    }

    public List<Map<String, Object>> query(SpatialQueryRequest req) {
        if (req.getTargetType() == null || req.getShape() == null) throw new ApiException("缺少查询条件");
        BiPredicate<Double, Double> matcher = buildMatcher(req.getShape());
        switch (req.getTargetType()) {
            case "water": return queryWater(matcher);
            case "support": return querySupport(matcher);
            case "parcel": return queryParcel(matcher);
            case "abandon": return queryAbandon(matcher);
            case "planting": return queryPlanting(matcher);
            case "quality": return queryQuality(matcher);
            default: throw new ApiException("不支持的查询目标: " + req.getTargetType());
        }
    }

    private BiPredicate<Double, Double> buildMatcher(SpatialQueryRequest.Shape shape) {
        String type = shape.getType();
        if ("rect".equals(type)) {
            List<List<Double>> b = shape.getBounds();
            if (b == null || b.size() != 2) throw new ApiException("矩形范围参数错误");
            Polygon rect = GeoUtil.rectPolygon(b.get(0).get(0), b.get(0).get(1), b.get(1).get(0), b.get(1).get(1));
            return (lng, lat) -> GeoUtil.contains(rect, lng, lat);
        } else if ("circle".equals(type)) {
            List<Double> c = shape.getCenter();
            Double r = shape.getRadius();
            if (c == null || c.size() != 2 || r == null) throw new ApiException("圆形范围参数错误");
            return (lng, lat) -> GeoUtil.haversineMeters(c.get(0), c.get(1), lng, lat) <= r;
        } else if ("polygon".equals(type)) {
            List<List<Double>> pts = shape.getPoints();
            if (pts == null || pts.size() < 3) throw new ApiException("多边形范围参数错误");
            List<double[]> arr = pts.stream().map(p -> new double[]{p.get(0), p.get(1)}).collect(Collectors.toList());
            Polygon poly = GeoUtil.polygonFromPoints(arr);
            return (lng, lat) -> GeoUtil.contains(poly, lng, lat);
        }
        throw new ApiException("不支持的范围形状: " + type);
    }

    private boolean test(BiPredicate<Double, Double> matcher, BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) return false;
        return matcher.test(lng.doubleValue(), lat.doubleValue());
    }

    private List<Map<String, Object>> queryWater(BiPredicate<Double, Double> matcher) {
        QueryWrapper<WaterFacility> w = new QueryWrapper<WaterFacility>().isNotNull("lng");
        dataScope.apply(w, "region_id");
        return waterMapper.selectList(w).stream()
                .filter(f -> test(matcher, f.getLng(), f.getLat()))
                .map(f -> {
                    Map<String, Object> m = base(f.getId(), f.getName(), f.getLng(), f.getLat());
                    m.put("type", f.getType());
                    m.put("runStatus", f.getRunStatus());
                    m.put("manager", f.getManager());
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> querySupport(BiPredicate<Double, Double> matcher) {
        QueryWrapper<SupportFacility> w = new QueryWrapper<SupportFacility>().isNotNull("lng");
        dataScope.apply(w, "region_id");
        return supportMapper.selectList(w).stream()
                .filter(f -> test(matcher, f.getLng(), f.getLat()))
                .map(f -> {
                    Map<String, Object> m = base(f.getId(), f.getName(), f.getLng(), f.getLat());
                    m.put("operateStatus", f.getOperateStatus());
                    m.put("serviceAbility", f.getServiceAbility());
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryParcel(BiPredicate<Double, Double> matcher) {
        QueryWrapper<LandParcel> w = new QueryWrapper<LandParcel>().isNotNull("center_lng");
        dataScope.apply(w, "region_id");
        return parcelMapper.selectList(w).stream()
                .filter(p -> test(matcher, p.getCenterLng(), p.getCenterLat()))
                .map(p -> {
                    Map<String, Object> m = base(p.getId(), p.getName(), p.getCenterLng(), p.getCenterLat());
                    m.put("code", p.getParcelCode());
                    m.put("contractor", p.getContractorName());
                    m.put("area", p.getArea());
                    m.put("landUse", p.getLandUse());
                    m.put("boundary", p.getBoundary());
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> queryAbandon(BiPredicate<Double, Double> matcher) {
        Map<String, LandParcel> parcelByCode = parcelCenterMap();
        QueryWrapper<AbandonParcel> w = new QueryWrapper<>();
        dataScope.apply(w, "region_id");
        List<Map<String, Object>> result = new ArrayList<>();
        for (AbandonParcel a : abandonMapper.selectList(w)) {
            LandParcel p = parcelByCode.get(a.getParcelCode());
            if (p == null || !test(matcher, p.getCenterLng(), p.getCenterLat())) continue;
            Map<String, Object> m = base(a.getId(), a.getParcelName(), p.getCenterLng(), p.getCenterLat());
            m.put("code", a.getParcelCode());
            m.put("governStatus", a.getGovernStatus());
            m.put("area", a.getArea());
            m.put("boundary", p.getBoundary());
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> queryPlanting(BiPredicate<Double, Double> matcher) {
        Map<String, LandParcel> parcelByCode = parcelCenterMap();
        QueryWrapper<PlantingRecord> w = new QueryWrapper<PlantingRecord>().eq("status", "VALID");
        dataScope.apply(w, "region_id");
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlantingRecord rec : plantingMapper.selectList(w)) {
            LandParcel p = parcelByCode.get(rec.getParcelCode());
            if (p == null || !test(matcher, p.getCenterLng(), p.getCenterLat())) continue;
            Map<String, Object> m = base(rec.getId(), rec.getParcelName(), p.getCenterLng(), p.getCenterLat());
            m.put("code", rec.getParcelCode());
            m.put("crop", rec.getCrop());
            m.put("plantYear", rec.getPlantYear());
            m.put("area", rec.getArea());
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> queryQuality(BiPredicate<Double, Double> matcher) {
        Map<String, LandParcel> parcelByCode = parcelCenterMap();
        QueryWrapper<LandQuality> w = new QueryWrapper<>();
        dataScope.apply(w, "region_id");
        List<Map<String, Object>> result = new ArrayList<>();
        for (LandQuality q : qualityMapper.selectList(w)) {
            LandParcel p = parcelByCode.get(q.getParcelCode());
            if (p == null || !test(matcher, p.getCenterLng(), p.getCenterLat())) continue;
            Map<String, Object> m = base(q.getId(), q.getParcelName(), p.getCenterLng(), p.getCenterLat());
            m.put("code", q.getParcelCode());
            m.put("grade", q.getGrade());
            m.put("soilType", q.getSoilType());
            m.put("evalYear", q.getEvalYear());
            result.add(m);
        }
        return result;
    }

    private Map<String, LandParcel> parcelCenterMap() {
        return parcelMapper.selectList(new QueryWrapper<LandParcel>().isNotNull("center_lng")).stream()
                .collect(Collectors.toMap(LandParcel::getParcelCode, p -> p, (a, b) -> a));
    }

    private Map<String, Object> base(Long id, String name, BigDecimal lng, BigDecimal lat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("lng", lng);
        m.put("lat", lat);
        return m;
    }
}
