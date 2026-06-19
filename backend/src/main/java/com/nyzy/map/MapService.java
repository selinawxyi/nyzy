package com.nyzy.map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 农业资源"一张图"点位聚合服务.
 * 坐标统一为 WGS84 经纬度 (lng/lat), 前端 Leaflet 直接渲染;
 * 后续切换天地图同样使用经纬度, 无需改动数据.
 */
@Service
public class MapService {

    private final LandParcelMapper parcelMapper;
    private final WaterFacilityMapper waterMapper;
    private final SupportFacilityMapper supportMapper;
    private final AbandonParcelMapper abandonMapper;

    public MapService(LandParcelMapper parcelMapper, WaterFacilityMapper waterMapper,
                      SupportFacilityMapper supportMapper, AbandonParcelMapper abandonMapper) {
        this.parcelMapper = parcelMapper;
        this.waterMapper = waterMapper;
        this.supportMapper = supportMapper;
        this.abandonMapper = abandonMapper;
    }

    public Map<String, Object> points() {
        List<LandParcel> parcels = parcelMapper.selectList(
                new QueryWrapper<LandParcel>().isNotNull("center_lng"));
        Map<String, BigDecimal[]> parcelCenter = parcels.stream().collect(Collectors.toMap(
                LandParcel::getParcelCode,
                p -> new BigDecimal[]{p.getCenterLng(), p.getCenterLat()},
                (a, b) -> a));
        Map<String, String> parcelBoundary = parcels.stream()
                .filter(p -> p.getBoundary() != null)
                .collect(Collectors.toMap(LandParcel::getParcelCode, LandParcel::getBoundary, (a, b) -> a));

        List<Map<String, Object>> parcelPoints = parcels.stream().map(p -> {
            Map<String, Object> m = base(p.getCenterLng(), p.getCenterLat(), p.getName());
            m.put("code", p.getParcelCode());
            m.put("contractor", p.getContractorName());
            m.put("area", p.getArea());
            m.put("landUse", p.getLandUse());
            m.put("boundary", p.getBoundary());   // GeoJSON 多边形, 前端绘面
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> waterPoints = waterMapper.selectList(
                new QueryWrapper<WaterFacility>().isNotNull("lng")).stream().map(f -> {
            Map<String, Object> m = base(f.getLng(), f.getLat(), f.getName());
            m.put("subtype", f.getType());
            m.put("runStatus", f.getRunStatus());
            m.put("manager", f.getManager());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> supportPoints = supportMapper.selectList(
                new QueryWrapper<SupportFacility>().isNotNull("lng")).stream().map(f -> {
            Map<String, Object> m = base(f.getLng(), f.getLat(), f.getName());
            m.put("operateStatus", f.getOperateStatus());
            m.put("ability", f.getServiceAbility());
            return m;
        }).collect(Collectors.toList());

        // 撂荒地块自身无坐标, 借关联确权地块的中心点定位
        List<Map<String, Object>> abandonPoints = new ArrayList<>();
        for (AbandonParcel a : abandonMapper.selectList(null)) {
            BigDecimal[] center = parcelCenter.get(a.getParcelCode());
            if (center == null || center[0] == null) continue;
            Map<String, Object> m = base(center[0], center[1], a.getParcelName());
            m.put("code", a.getParcelCode());
            m.put("governStatus", a.getGovernStatus());
            m.put("reason", a.getReasonText());
            m.put("area", a.getArea());
            m.put("boundary", parcelBoundary.get(a.getParcelCode()));  // 借关联确权地块边界绘面
            abandonPoints.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parcel", parcelPoints);
        result.put("water", waterPoints);
        result.put("support", supportPoints);
        result.put("abandon", abandonPoints);
        return result;
    }

    private Map<String, Object> base(BigDecimal lng, BigDecimal lat, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("lng", lng);
        m.put("lat", lat);
        return m;
    }
}
