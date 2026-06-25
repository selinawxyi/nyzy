package com.nyzy.resource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.common.RegionPathUtil;
import com.nyzy.gis.GeoUtil;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.resource.dto.SupportQuery;
import com.nyzy.resource.entity.FacilityCategory;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.system.AuditLogService;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SupportFacilityService {

    private final SupportFacilityMapper mapper;
    private final FacilityCategoryMapper categoryMapper;
    private final LandParcelMapper parcelMapper;
    private final com.nyzy.auth.DataScope dataScope;
    private final AuditLogService auditLogService;

    public SupportFacilityService(SupportFacilityMapper mapper, FacilityCategoryMapper categoryMapper,
                                  LandParcelMapper parcelMapper, com.nyzy.auth.DataScope dataScope,
                                  AuditLogService auditLogService) {
        this.mapper = mapper;
        this.categoryMapper = categoryMapper;
        this.parcelMapper = parcelMapper;
        this.dataScope = dataScope;
        this.auditLogService = auditLogService;
    }

    public PageResult<SupportFacility> page(SupportQuery q) {
        QueryWrapper<SupportFacility> w = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) w.like("name", q.getKeyword().trim());
        if (q.getCategoryId() != null) w.eq("category_id", q.getCategoryId());
        if (StringUtils.hasText(q.getOperateStatus())) w.eq("operate_status", q.getOperateStatus());
        if (StringUtils.hasText(q.getOperateSubject())) w.eq("operate_subject", q.getOperateSubject());
        dataScope.apply(w, "region_id");
        w.orderByDesc("id");
        IPage<SupportFacility> p = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        fillCategoryName(p.getRecords());
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    public SupportFacility get(Long id) {
        SupportFacility f = mapper.selectById(id);
        if (f == null) throw new ApiException(404, "配套设施不存在");
        return f;
    }

    @Transactional
    public Long create(SupportFacility f) {
        validate(f);
        f.setAuditStatus("PENDING");
        f.setCreatedBy(UserContext.username());
        f.setDeleted(0);
        mapper.insert(f);
        auditLogService.record("support", f.getId(), "CREATE", "新增配套设施 " + f.getName());
        return f.getId();
    }

    /** 修改: 关键字段(设施分类/经纬度)变更需重新审核, 同 B1.4 水利设施规则 */
    @Transactional
    public void update(SupportFacility f) {
        SupportFacility old = get(f.getId());
        validate(f);
        boolean keyChanged = !java.util.Objects.equals(old.getCategoryId(), f.getCategoryId())
                || numChanged(old.getLng(), f.getLng())
                || numChanged(old.getLat(), f.getLat());
        f.setAuditStatus(keyChanged ? "PENDING" : old.getAuditStatus());
        f.setCategoryName(null);
        f.setDeleted(null);
        f.setDeleteReason(null);
        f.setDeletedBy(null);
        f.setDeletedAt(null);
        f.setCreatedBy(null);
        f.setCreatedAt(null);
        if (mapper.updateById(f) == 0) {
            throw new ApiException(409, "数据已被他人修改, 请刷新后重试");
        }
        auditLogService.record("support", f.getId(), "UPDATE", "修改配套设施" + (keyChanged ? "(关键字段变更, 转待审核)" : ""));
    }

    @Transactional
    public void audit(Long id, boolean pass) {
        SupportFacility old = get(id);
        if (!"PENDING".equals(old.getAuditStatus())) throw new ApiException("仅待审核设施可执行审核");
        SupportFacility upd = new SupportFacility();
        upd.setId(id);
        upd.setAuditStatus(pass ? "APPROVED" : "REJECTED");
        mapper.updateById(upd);
        auditLogService.record("support", id, "STATUS", pass ? "审核通过" : "审核退回");
    }

    @Transactional
    public void delete(Long id, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除配套设施");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        get(id);
        SupportFacility meta = new SupportFacility();
        meta.setId(id);
        meta.setDeleteReason(reason);
        meta.setDeletedBy(UserContext.username());
        meta.setDeletedAt(LocalDateTime.now());
        mapper.updateById(meta);
        mapper.deleteById(id);
        auditLogService.record("support", id, "DELETE", "软删除, 原因: " + reason);
    }

    /** 批量修改: 对选中设施统一改运营状态/联系电话/营业时间(仅非空字段), 与 B1.4 水利设施批量修改对齐 */
    @Transactional
    public int batchUpdate(java.util.List<Long> ids, SupportFacility u) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要修改的设施");
        boolean any = StringUtils.hasText(u.getOperateStatus()) || StringUtils.hasText(u.getPhone())
                || StringUtils.hasText(u.getBusinessHours());
        if (!any) throw new ApiException("请至少填写一个要统一修改的字段");
        // 逐行走 updateById(带 @Version) 而非一条 UpdateWrapper SQL, 以保留乐观锁校验; 版本冲突/已被删除的行跳过, 不计入返回数
        int n = 0;
        for (Long id : ids) {
            SupportFacility old = mapper.selectById(id);
            if (old == null) continue;
            SupportFacility patch = new SupportFacility();
            patch.setId(id);
            patch.setVersion(old.getVersion());
            if (StringUtils.hasText(u.getOperateStatus())) patch.setOperateStatus(u.getOperateStatus());
            if (StringUtils.hasText(u.getPhone())) patch.setPhone(u.getPhone());
            if (StringUtils.hasText(u.getBusinessHours())) patch.setBusinessHours(u.getBusinessHours());
            n += mapper.updateById(patch);
        }
        return n;
    }

    /** 批量删除: 仅管理员, 必填原因, 软删除入回收站(与单条删除规则一致) */
    @Transactional
    public int batchDelete(java.util.List<Long> ids, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除配套设施");
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要删除的设施");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SupportFacility> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SupportFacility>().in("id", ids);
        w.set("delete_reason", reason).set("deleted_by", UserContext.username()).set("deleted_at", LocalDateTime.now());
        mapper.update(null, w);
        int n = mapper.deleteBatchIds(ids);
        for (Long id : ids) auditLogService.record("support", id, "DELETE", "批量软删除, 原因: " + reason);
        return n;
    }

    /**
     * 绘制/更新服务范围面, 并自动统计覆盖地块数与覆盖村庄数(代理指标:
     * 系统内没有真实的村级行政边界数据, 用落在服务范围内的确权地块中心点
     * 近似统计覆盖情况, 而非真实的行政区划面相交计算).
     */
    @Transactional
    public SupportFacility updateServiceArea(Long id, String serviceAreaGeoJson) {
        get(id);
        Polygon area = GeoUtil.parsePolygon(serviceAreaGeoJson);
        GeoUtil.validateOrThrow(area);

        List<LandParcel> parcels = parcelMapper.selectList(new QueryWrapper<LandParcel>().isNotNull("center_lng"));
        int coverageCount = 0;
        Set<String> villages = new HashSet<>();
        for (LandParcel p : parcels) {
            if (p.getCenterLng() == null || p.getCenterLat() == null) continue;
            if (!GeoUtil.contains(area, p.getCenterLng().doubleValue(), p.getCenterLat().doubleValue())) continue;
            coverageCount++;
            villages.add(RegionPathUtil.village(p.getRegionPath()));
        }

        SupportFacility upd = new SupportFacility();
        upd.setId(id);
        upd.setServiceArea(serviceAreaGeoJson);
        upd.setCoverageCount(coverageCount);
        upd.setCoverageVillageCount(villages.size());
        mapper.updateById(upd);
        return get(id);
    }

    private void validate(SupportFacility f) {
        if (!StringUtils.hasText(f.getName())) throw new ApiException("设施名称不能为空");
        if (f.getCategoryId() == null) throw new ApiException("请选择设施分类");
    }

    private boolean numChanged(java.math.BigDecimal a, java.math.BigDecimal b) {
        if (a == null || b == null) return !java.util.Objects.equals(a, b);
        return a.compareTo(b) != 0;
    }

    private void fillCategoryName(List<SupportFacility> list) {
        if (list.isEmpty()) return;
        Map<Long, String> names = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(FacilityCategory::getId, FacilityCategory::getName, (a, b) -> a));
        for (SupportFacility f : list) {
            if (f.getCategoryId() != null) f.setCategoryName(names.get(f.getCategoryId()));
        }
    }
}
