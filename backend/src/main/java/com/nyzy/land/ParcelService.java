package com.nyzy.land;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.land.dto.ParcelQuery;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.entity.LandParcelHistory;
import com.nyzy.land.mapper.LandParcelHistoryMapper;
import com.nyzy.land.mapper.LandParcelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ParcelService {

    private final LandParcelMapper mapper;
    private final LandParcelHistoryMapper historyMapper;
    private final PlantingRecordMapper plantingMapper;
    private final LandQualityMapper qualityMapper;
    private final AbandonParcelMapper abandonMapper;
    private final ObjectMapper objectMapper;
    private final com.nyzy.auth.DataScope dataScope;

    public ParcelService(LandParcelMapper mapper, LandParcelHistoryMapper historyMapper,
                         PlantingRecordMapper plantingMapper, LandQualityMapper qualityMapper,
                         AbandonParcelMapper abandonMapper, ObjectMapper objectMapper,
                         com.nyzy.auth.DataScope dataScope) {
        this.mapper = mapper;
        this.historyMapper = historyMapper;
        this.plantingMapper = plantingMapper;
        this.qualityMapper = qualityMapper;
        this.abandonMapper = abandonMapper;
        this.objectMapper = objectMapper;
        this.dataScope = dataScope;
    }

    public PageResult<LandParcel> page(ParcelQuery q) {
        QueryWrapper<LandParcel> w = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like("parcel_code", kw).or().like("name", kw).or().like("contractor_name", kw));
        }
        if (StringUtils.hasText(q.getLandUse())) w.eq("land_use", q.getLandUse());
        if (q.getAreaMin() != null) w.ge("area", q.getAreaMin());
        if (q.getAreaMax() != null) w.le("area", q.getAreaMax());
        dataScope.apply(w, "region_id");
        w.orderByAsc("parcel_code");
        IPage<LandParcel> p = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    public LandParcel get(Long id) {
        LandParcel p = mapper.selectById(id);
        if (p == null) throw new ApiException(404, "地块不存在");
        return p;
    }

    @Transactional
    public Long create(LandParcel p) {
        if (!StringUtils.hasText(p.getParcelCode())) throw new ApiException("地块编码不能为空");
        if (!StringUtils.hasText(p.getName())) throw new ApiException("地块名称不能为空");
        if (exists(p.getParcelCode(), null)) throw new ApiException("地块编码已存在: " + p.getParcelCode());
        p.setDeleted(0);
        if (StringUtils.hasText(p.getBoundary())) {
            org.locationtech.jts.geom.Polygon poly = com.nyzy.gis.GeoUtil.parsePolygon(p.getBoundary());
            com.nyzy.gis.GeoUtil.validateOrThrow(poly);
            fillGeometryFields(p, poly);
        }
        mapper.insert(p);
        recordHistory(p, "CREATE", "初始建档", "新增确权地块");
        return p.getId();
    }

    /**
     * 单条编辑 (A1.2). 关键字段(地块编码/承包方编码)变更需填写变更原因.
     */
    @Transactional
    public void update(LandParcel p, String reason) {
        LandParcel old = get(p.getId());
        if (StringUtils.hasText(p.getParcelCode()) && !Objects.equals(old.getParcelCode(), p.getParcelCode())) {
            if (exists(p.getParcelCode(), p.getId())) throw new ApiException("地块编码已存在: " + p.getParcelCode());
        }
        boolean keyChanged = changed(old.getParcelCode(), p.getParcelCode())
                || changed(old.getContractorCode(), p.getContractorCode());
        if (keyChanged && !StringUtils.hasText(reason)) {
            throw new ApiException("修改地块编码或承包方编码属于关键变更, 请填写变更原因");
        }
        String fields = diffFields(old, p);
        // 软删除/审计字段不允许普通更新
        p.setDeleted(null);
        p.setDeleteReason(null);
        p.setDeletedBy(null);
        p.setDeletedAt(null);
        p.setCreatedAt(null);
        if (mapper.updateById(p) == 0) {
            throw new ApiException(409, "数据已被他人修改, 请刷新后重试");
        }
        LandParcel latest = mapper.selectById(p.getId());
        recordHistory(latest, "UPDATE", fields, StringUtils.hasText(reason) ? reason : "信息更新");
        cascadeDenormalized(old, latest);
    }

    /**
     * 地块编码/名称/区划属于种植记录/质量评价/撂荒记录里冗余存储的展示字段(按 parcel_code 关联, 非外键),
     * 这里改了源头后同步回写, 避免那几张表继续显示改名/改区划前的旧信息.
     */
    private void cascadeDenormalized(LandParcel old, LandParcel n) {
        boolean codeChanged = changed(old.getParcelCode(), n.getParcelCode());
        boolean nameChanged = changed(old.getName(), n.getName());
        boolean regionChanged = changed(old.getRegionId(), n.getRegionId()) || changed(old.getRegionPath(), n.getRegionPath());
        if (!codeChanged && !nameChanged && !regionChanged) return;
        String oldCode = old.getParcelCode();

        UpdateWrapper<PlantingRecord> pw = new UpdateWrapper<PlantingRecord>().eq("parcel_code", oldCode);
        if (codeChanged) pw.set("parcel_code", n.getParcelCode());
        if (nameChanged) pw.set("parcel_name", n.getName());
        if (regionChanged) pw.set("region_id", n.getRegionId()).set("region_path", n.getRegionPath());
        plantingMapper.update(null, pw);

        UpdateWrapper<LandQuality> qw = new UpdateWrapper<LandQuality>().eq("parcel_code", oldCode);
        if (codeChanged) qw.set("parcel_code", n.getParcelCode());
        if (nameChanged) qw.set("parcel_name", n.getName());
        if (regionChanged) qw.set("region_id", n.getRegionId()).set("region_path", n.getRegionPath());
        qualityMapper.update(null, qw);

        UpdateWrapper<AbandonParcel> aw = new UpdateWrapper<AbandonParcel>().eq("parcel_code", oldCode);
        if (codeChanged) aw.set("parcel_code", n.getParcelCode());
        if (nameChanged) aw.set("parcel_name", n.getName());
        if (regionChanged) aw.set("region_id", n.getRegionId()).set("region_path", n.getRegionPath());
        abandonMapper.update(null, aw);
    }

    /**
     * 删除确权地块: 数据底座, 仅管理员; 若被种植/质量/撂荒记录关联则阻止.
     */
    @Transactional
    public void delete(Long id, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除确权地块");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        LandParcel old = get(id);
        long planting = plantingMapper.selectCount(new QueryWrapper<PlantingRecord>().eq("parcel_code", old.getParcelCode()));
        long quality = qualityMapper.selectCount(new QueryWrapper<LandQuality>().eq("parcel_code", old.getParcelCode()));
        long abandon = abandonMapper.selectCount(new QueryWrapper<AbandonParcel>().eq("parcel_code", old.getParcelCode()));
        if (planting + quality + abandon > 0) {
            throw new ApiException(String.format(
                    "该地块存在关联业务数据(种植%d/质量%d/撂荒%d), 不可删除", planting, quality, abandon));
        }
        recordHistory(old, "DELETE", "删除地块", reason);
        LandParcel meta = new LandParcel();
        meta.setId(id);
        meta.setDeleteReason(reason);
        meta.setDeletedBy(UserContext.username());
        meta.setDeletedAt(LocalDateTime.now());
        mapper.updateById(meta);
        mapper.deleteById(id);
    }

    /** 批量更新: 按地块编码匹配, 更新非空属性字段(不改编码), 记录历史 */
    @Transactional
    public void importUpdate(LandParcel patch) {
        LandParcel old = mapper.selectOne(new QueryWrapper<LandParcel>().eq("parcel_code", patch.getParcelCode()));
        if (old == null) throw new ApiException("地块编码不存在: " + patch.getParcelCode());
        LandParcel upd = new LandParcel();
        upd.setId(old.getId());
        boolean any = false;
        if (StringUtils.hasText(patch.getContractorName())) { upd.setContractorName(patch.getContractorName()); any = true; }
        if (StringUtils.hasText(patch.getContractorCode())) { upd.setContractorCode(patch.getContractorCode()); any = true; }
        if (patch.getArea() != null) { upd.setArea(patch.getArea()); any = true; }
        if (StringUtils.hasText(patch.getLandUse())) { upd.setLandUse(patch.getLandUse()); any = true; }
        if (!any) throw new ApiException("无可更新字段");
        mapper.updateById(upd);
        LandParcel latest = mapper.selectById(old.getId());
        recordHistory(latest, "UPDATE", diffFields(old, patch), "批量导入更新");
    }

    /**
     * 空间框选批量改属性 (A1.2): 对查询命中的多个地块统一修改承包方/用途字段(仅非空字段).
     * 承包方编码/地块用途属于关键字段, 与单条编辑(update)同样的规则需要填写变更原因.
     * 逐行走 updateById(带 @Version) 以保留乐观锁校验, 并逐行记录历史(与该模块版本追溯设计一致).
     */
    @Transactional
    public int batchUpdate(List<Long> ids, LandParcel patch, String reason) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要修改的地块");
        boolean any = StringUtils.hasText(patch.getContractorName()) || StringUtils.hasText(patch.getContractorCode())
                || StringUtils.hasText(patch.getLandUse());
        if (!any) throw new ApiException("请至少填写一个要统一修改的字段");
        boolean keyChanged = StringUtils.hasText(patch.getContractorCode()) || StringUtils.hasText(patch.getLandUse());
        if (keyChanged && !StringUtils.hasText(reason)) {
            throw new ApiException("批量修改承包方编码或地块用途属于关键变更, 请填写变更原因");
        }
        int n = 0;
        for (Long id : ids) {
            LandParcel old = mapper.selectById(id);
            if (old == null) continue;
            LandParcel upd = new LandParcel();
            upd.setId(id);
            upd.setVersion(old.getVersion());
            if (StringUtils.hasText(patch.getContractorName())) upd.setContractorName(patch.getContractorName());
            if (StringUtils.hasText(patch.getContractorCode())) upd.setContractorCode(patch.getContractorCode());
            if (StringUtils.hasText(patch.getLandUse())) upd.setLandUse(patch.getLandUse());
            if (mapper.updateById(upd) == 0) continue;
            LandParcel latest = mapper.selectById(id);
            recordHistory(latest, "UPDATE", diffFields(old, patch), StringUtils.hasText(reason) ? reason : "空间框选批量修改");
            n++;
        }
        return n;
    }

    // ---------------- 几何编辑 (A1.4) ----------------

    /** 边界节点编辑/重绘: 重算面积/质心/四至, 记历史. */
    @Transactional
    public void updateGeometry(Long id, String boundaryGeoJson, String reason) {
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写编辑原因");
        get(id);
        org.locationtech.jts.geom.Polygon poly = com.nyzy.gis.GeoUtil.parsePolygon(boundaryGeoJson);
        com.nyzy.gis.GeoUtil.validateOrThrow(poly);
        applyGeometry(id, poly);
        LandParcel latest = mapper.selectById(id);
        recordHistory(latest, "UPDATE", "地块边界编辑", reason);
    }

    /**
     * 用一条贯穿线把地块分割成两块: 面积较大的一块保留原编码并更新几何,
     * 较小的一块以 newCode 新建一条地块, 两者都继承原承包方/用途/区划信息并记历史.
     */
    @Transactional
    public Long split(Long id, String lineGeoJson, String newCode, String reason) {
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写分割原因");
        if (!StringUtils.hasText(newCode)) throw new ApiException("请填写分割后新地块编码");
        if (exists(newCode, null)) throw new ApiException("地块编码已存在: " + newCode);
        LandParcel old = get(id);
        if (!StringUtils.hasText(old.getBoundary())) throw new ApiException("该地块没有边界几何, 无法分割");

        org.locationtech.jts.geom.Polygon original = com.nyzy.gis.GeoUtil.parsePolygon(old.getBoundary());
        org.locationtech.jts.geom.LineString line = com.nyzy.gis.GeoUtil.parseLineString(lineGeoJson);
        List<org.locationtech.jts.geom.Polygon> parts = com.nyzy.gis.GeoUtil.splitByLine(original, line);
        parts.sort((a, b) -> Double.compare(b.getArea(), a.getArea())); // 大块在前, 保留原编码
        org.locationtech.jts.geom.Polygon keep = parts.get(0);
        org.locationtech.jts.geom.Polygon spun = parts.get(1);

        applyGeometry(id, keep);
        LandParcel kept = mapper.selectById(id);
        recordHistory(kept, "UPDATE", "地块分割", reason + "(分出新地块 " + newCode + ")");

        LandParcel created = new LandParcel();
        created.setParcelCode(newCode);
        created.setName(old.getName() + "(分割)");
        created.setRegionId(old.getRegionId());
        created.setRegionPath(old.getRegionPath());
        created.setContractorName(old.getContractorName());
        created.setContractorCode(old.getContractorCode());
        created.setLandUse(old.getLandUse());
        created.setContractStart(old.getContractStart());
        created.setContractEnd(old.getContractEnd());
        created.setDeleted(0);
        created.setMergeStatus("NORMAL");
        fillGeometryFields(created, spun);
        mapper.insert(created);
        recordHistory(created, "CREATE", "地块分割产生", reason + "(由 " + old.getParcelCode() + " 分割产生)");
        return created.getId();
    }

    /**
     * 合并多个(相邻/相交)地块为一个新地块; 原地块不删除, 标记 merge_status=MERGED
     * 并记录合并去向, 历史/种植/质量记录仍可按原编码追溯。
     */
    @Transactional
    public Long merge(List<Long> ids, String newCode, String reason) {
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写合并原因");
        if (!StringUtils.hasText(newCode)) throw new ApiException("请填写合并后新地块编码");
        if (ids == null || ids.size() < 2) throw new ApiException("请至少选择2个地块进行合并");
        if (exists(newCode, null)) throw new ApiException("地块编码已存在: " + newCode);

        List<LandParcel> parcels = mapper.selectBatchIds(ids);
        if (parcels.size() != ids.size()) throw new ApiException("部分地块不存在");
        for (LandParcel p : parcels) {
            if (!StringUtils.hasText(p.getBoundary())) throw new ApiException("地块 " + p.getParcelCode() + " 没有边界几何, 无法合并");
            if ("MERGED".equals(p.getMergeStatus())) throw new ApiException("地块 " + p.getParcelCode() + " 已合并入其他地块, 不能再次合并");
        }
        List<org.locationtech.jts.geom.Polygon> polys = new ArrayList<>();
        for (LandParcel p : parcels) polys.add(com.nyzy.gis.GeoUtil.parsePolygon(p.getBoundary()));
        org.locationtech.jts.geom.Polygon merged = com.nyzy.gis.GeoUtil.unionToSinglePolygon(polys);

        LandParcel base = parcels.stream().max(Comparator.comparing(p -> p.getArea() == null ? java.math.BigDecimal.ZERO : p.getArea())).get();
        LandParcel created = new LandParcel();
        created.setParcelCode(newCode);
        created.setName(base.getName() + "(合并)");
        created.setRegionId(base.getRegionId());
        created.setRegionPath(base.getRegionPath());
        created.setContractorName(base.getContractorName());
        created.setContractorCode(base.getContractorCode());
        created.setLandUse(base.getLandUse());
        created.setContractStart(base.getContractStart());
        created.setContractEnd(base.getContractEnd());
        created.setDeleted(0);
        created.setMergeStatus("NORMAL");
        fillGeometryFields(created, merged);
        mapper.insert(created);
        String sourceCodes = String.join("、", parcels.stream().map(LandParcel::getParcelCode).toArray(String[]::new));
        recordHistory(created, "CREATE", "地块合并产生", reason + "(由 " + sourceCodes + " 合并产生)");

        for (LandParcel p : parcels) {
            LandParcel upd = new LandParcel();
            upd.setId(p.getId());
            upd.setMergeStatus("MERGED");
            upd.setMergedIntoCode(newCode);
            mapper.updateById(upd);
            LandParcel latest = mapper.selectById(p.getId());
            recordHistory(latest, "UPDATE", "地块合并", reason + "(合并入新地块 " + newCode + ")");
        }
        return created.getId();
    }

    private void applyGeometry(Long id, org.locationtech.jts.geom.Polygon poly) {
        LandParcel upd = new LandParcel();
        upd.setId(id);
        fillGeometryFields(upd, poly);
        mapper.updateById(upd);
    }

    private void fillGeometryFields(LandParcel p, org.locationtech.jts.geom.Polygon poly) {
        p.setArea(java.math.BigDecimal.valueOf(com.nyzy.gis.GeoUtil.areaMu(poly)).setScale(2, java.math.RoundingMode.HALF_UP));
        double[] centroid = com.nyzy.gis.GeoUtil.centroid(poly);
        p.setCenterLng(java.math.BigDecimal.valueOf(centroid[0]));
        p.setCenterLat(java.math.BigDecimal.valueOf(centroid[1]));
        String[] bounds = com.nyzy.gis.GeoUtil.bounds(poly);
        p.setBoundEast(bounds[0]);
        p.setBoundSouth(bounds[1]);
        p.setBoundWest(bounds[2]);
        p.setBoundNorth(bounds[3]);
        p.setBoundary(com.nyzy.gis.GeoUtil.toGeoJson(poly));
    }

    // ---------------- 版本历史 (A1.5) ----------------

    public List<LandParcelHistory> history(Long parcelId) {
        return historyMapper.selectList(new QueryWrapper<LandParcelHistory>()
                .eq("parcel_id", parcelId).orderByDesc("version"));
    }

    /** 版本对比: 返回两个版本快照, 前端高亮差异 */
    public Map<String, Object> compare(Long v1Id, Long v2Id) {
        LandParcelHistory a = historyMapper.selectById(v1Id);
        LandParcelHistory b = historyMapper.selectById(v2Id);
        if (a == null || b == null) throw new ApiException(404, "历史版本不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("v1", a);
        result.put("v2", b);
        return result;
    }

    // ---------------- 内部工具 ----------------

    private boolean exists(String code, Long excludeId) {
        QueryWrapper<LandParcel> w = new QueryWrapper<LandParcel>().eq("parcel_code", code);
        if (excludeId != null) w.ne("id", excludeId);
        return mapper.selectCount(w) > 0;
    }

    private boolean changed(Object a, Object b) {
        return b != null && !Objects.equals(a, b);
    }

    private String diffFields(LandParcel old, LandParcel n) {
        List<String> changes = new ArrayList<>();
        if (changed(old.getContractorName(), n.getContractorName())) changes.add("承包方姓名");
        if (changed(old.getContractorCode(), n.getContractorCode())) changes.add("承包方编码");
        if (changed(old.getArea(), n.getArea())) changes.add("确权面积");
        if (changed(old.getLandUse(), n.getLandUse())) changes.add("地块用途");
        if (changed(old.getName(), n.getName())) changes.add("地块名称");
        if (changed(old.getParcelCode(), n.getParcelCode())) changes.add("地块编码");
        return changes.isEmpty() ? "其他信息" : String.join("、", changes);
    }

    private int nextVersion(Long parcelId) {
        Integer max = historyMapper.selectList(new QueryWrapper<LandParcelHistory>()
                        .eq("parcel_id", parcelId).orderByDesc("version").last("limit 1"))
                .stream().map(LandParcelHistory::getVersion).findFirst().orElse(0);
        return max + 1;
    }

    private void recordHistory(LandParcel p, String type, String fields, String reason) {
        LandParcelHistory h = new LandParcelHistory();
        h.setParcelId(p.getId());
        h.setParcelCode(p.getParcelCode());
        h.setVersion(nextVersion(p.getId()));
        h.setChangeType(type);
        h.setChangeFields(fields);
        h.setOperator(UserContext.username());
        h.setReason(reason);
        try {
            h.setSnapshot(objectMapper.writeValueAsString(p));
        } catch (Exception ignored) {
        }
        historyMapper.insert(h);
    }
}
