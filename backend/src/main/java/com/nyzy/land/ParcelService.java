package com.nyzy.land;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
        mapper.updateById(p);
        LandParcel latest = mapper.selectById(p.getId());
        recordHistory(latest, "UPDATE", fields, StringUtils.hasText(reason) ? reason : "信息更新");
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
