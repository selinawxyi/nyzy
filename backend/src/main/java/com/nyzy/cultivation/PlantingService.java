package com.nyzy.cultivation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.cultivation.dto.PlantingQuery;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import com.nyzy.system.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Objects;

@Service
public class PlantingService {

    private final PlantingRecordMapper mapper;
    private final LandParcelMapper parcelMapper;
    private final com.nyzy.auth.DataScope dataScope;
    private final AuditLogService auditLogService;

    public PlantingService(PlantingRecordMapper mapper, LandParcelMapper parcelMapper,
                           com.nyzy.auth.DataScope dataScope, AuditLogService auditLogService) {
        this.mapper = mapper;
        this.parcelMapper = parcelMapper;
        this.dataScope = dataScope;
        this.auditLogService = auditLogService;
    }

    public PageResult<PlantingRecord> page(PlantingQuery q) {
        QueryWrapper<PlantingRecord> w = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like("parcel_code", kw).or().like("parcel_name", kw).or().like("reporter", kw));
        }
        if (q.getPlantYear() != null) w.eq("plant_year", q.getPlantYear());
        if (StringUtils.hasText(q.getCrop())) w.eq("crop", q.getCrop());
        if (StringUtils.hasText(q.getDataSource())) w.eq("data_source", q.getDataSource());
        if (StringUtils.hasText(q.getStatus())) w.eq("status", q.getStatus());
        dataScope.apply(w, "region_id");
        w.orderByDesc("plant_year", "id");
        IPage<PlantingRecord> p = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    /** 种植历史: 某地块按年度倒序的全部记录 (用于轮作视图/产量趋势) */
    public List<PlantingRecord> history(String parcelCode) {
        return mapper.selectList(new QueryWrapper<PlantingRecord>()
                .eq("parcel_code", parcelCode)
                .orderByAsc("plant_year", "id"));
    }

    @Transactional
    public Long create(PlantingRecord r) {
        validate(r);
        fillParcelInfo(r);
        validateArea(r);
        if (!StringUtils.hasText(r.getStatus())) r.setStatus("VALID");
        if (r.getReportDate() == null) r.setReportDate(LocalDate.now());
        if (!StringUtils.hasText(r.getReporter())) r.setReporter(UserContext.username());
        r.setCreatedBy(UserContext.username());
        r.setDeleted(0);
        mapper.insert(r);
        auditLogService.record("planting", r.getId(), "CREATE",
                "新增种植记录 " + r.getParcelCode() + " " + r.getPlantYear() + "年" + r.getCrop());
        return r.getId();
    }

    @Transactional
    public void update(PlantingRecord r) {
        PlantingRecord old = require(r.getId());
        validate(r);
        // 关键约束: 已收获记录的产量原则上不可修改
        boolean harvested = old.getActualHarvestDate() != null;
        if (harvested && r.getYieldPerMu() != null
                && !numEq(old.getYieldPerMu(), r.getYieldPerMu())) {
            throw new ApiException("该记录已收获, 产量数据不允许修改");
        }
        fillParcelInfo(r);
        validateArea(r);
        // 软删除/审计字段不允许普通更新修改
        r.setDeleted(null);
        r.setDeleteReason(null);
        r.setDeletedBy(null);
        r.setDeletedAt(null);
        r.setCreatedBy(null);
        r.setCreatedAt(null);
        if (mapper.updateById(r) == 0) {
            throw new ApiException(409, "数据已被他人修改, 请刷新后重试");
        }
        auditLogService.record("planting", r.getId(), "UPDATE", "修改种植记录");
    }

    /** 标记为无效 (文档推荐优先于删除, 不影响历史展示但排除统计) */
    @Transactional
    public void markInvalid(Long id) {
        require(id);
        PlantingRecord upd = new PlantingRecord();
        upd.setId(id);
        upd.setStatus("INVALID");
        mapper.updateById(upd);
        auditLogService.record("planting", id, "STATUS", "标记为无效");
    }

    /** 软删除: 必填原因 */
    @Transactional
    public void delete(Long id, String reason) {
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        require(id);
        PlantingRecord meta = new PlantingRecord();
        meta.setId(id);
        meta.setDeleteReason(reason);
        meta.setDeletedBy(UserContext.username());
        meta.setDeletedAt(LocalDateTime.now());
        mapper.updateById(meta);
        mapper.deleteById(id);
        auditLogService.record("planting", id, "DELETE", "软删除, 原因: " + reason);
    }

    /** 批量修改: 对选中记录统一改数据来源/填报人/备注(仅非空字段) */
    @Transactional
    public int batchUpdate(java.util.List<Long> ids, PlantingRecord u) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要修改的记录");
        boolean any = StringUtils.hasText(u.getDataSource()) || StringUtils.hasText(u.getReporter())
                || StringUtils.hasText(u.getRemark());
        if (!any) throw new ApiException("请至少填写一个要统一修改的字段");
        // 逐行走 updateById(带 @Version) 而非一条 UpdateWrapper SQL, 以保留乐观锁校验; 版本冲突/已被删除的行跳过, 不计入返回数
        int n = 0;
        for (Long id : ids) {
            PlantingRecord old = mapper.selectById(id);
            if (old == null) continue;
            PlantingRecord patch = new PlantingRecord();
            patch.setId(id);
            patch.setVersion(old.getVersion());
            if (StringUtils.hasText(u.getDataSource())) patch.setDataSource(u.getDataSource());
            if (StringUtils.hasText(u.getReporter())) patch.setReporter(u.getReporter());
            if (StringUtils.hasText(u.getRemark())) patch.setRemark(u.getRemark());
            n += mapper.updateById(patch);
        }
        return n;
    }

    /** 批量软删除: 必填原因(与单条删除规则一致, 无需管理员) */
    @Transactional
    public int batchDelete(java.util.List<Long> ids, String reason) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要删除的记录");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PlantingRecord> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PlantingRecord>().in("id", ids);
        w.set("delete_reason", reason).set("deleted_by", UserContext.username()).set("deleted_at", LocalDateTime.now());
        mapper.update(null, w);
        int n = mapper.deleteBatchIds(ids);
        for (Long id : ids) auditLogService.record("planting", id, "DELETE", "批量软删除, 原因: " + reason);
        return n;
    }

    // ---------------- 校验 ----------------

    private void validate(PlantingRecord r) {
        if (!StringUtils.hasText(r.getParcelCode())) throw new ApiException("地块编码不能为空");
        if (!StringUtils.hasText(r.getCrop())) throw new ApiException("作物不能为空");
        if (r.getPlantYear() == null) throw new ApiException("种植年度不能为空");
        if (r.getPlantYear() > Year.now().getValue()) throw new ApiException("种植年度不能为未来年份");
    }

    /** 种植面积不得超过确权地块面积 */
    private void validateArea(PlantingRecord r) {
        if (r.getArea() == null) return;
        LandParcel parcel = findParcel(r.getParcelCode());
        if (parcel != null && parcel.getArea() != null
                && r.getArea().compareTo(parcel.getArea()) > 0) {
            throw new ApiException("种植面积(" + r.getArea() + "亩)超过确权面积("
                    + parcel.getArea() + "亩)");
        }
    }

    private void fillParcelInfo(PlantingRecord r) {
        if (StringUtils.hasText(r.getParcelName()) && r.getRegionId() != null) return;
        LandParcel parcel = findParcel(r.getParcelCode());
        if (parcel != null) {
            if (!StringUtils.hasText(r.getParcelName())) r.setParcelName(parcel.getName());
            if (r.getRegionId() == null) r.setRegionId(parcel.getRegionId());
            if (!StringUtils.hasText(r.getRegionPath())) r.setRegionPath(parcel.getRegionPath());
        }
    }

    private LandParcel findParcel(String code) {
        return parcelMapper.selectOne(new QueryWrapper<LandParcel>().eq("parcel_code", code));
    }

    private PlantingRecord require(Long id) {
        PlantingRecord r = mapper.selectById(id);
        if (r == null) throw new ApiException(404, "种植记录不存在");
        return r;
    }

    private boolean numEq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return Objects.equals(a, b);
        return a.compareTo(b) == 0;
    }
}
