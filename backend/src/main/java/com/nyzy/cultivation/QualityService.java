package com.nyzy.cultivation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.cultivation.dto.QualityQuery;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.mapper.LandQualityMapper;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.land.mapper.LandParcelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

@Service
public class QualityService {

    private final LandQualityMapper mapper;
    private final LandParcelMapper parcelMapper;
    private final com.nyzy.auth.DataScope dataScope;

    public QualityService(LandQualityMapper mapper, LandParcelMapper parcelMapper,
                          com.nyzy.auth.DataScope dataScope) {
        this.mapper = mapper;
        this.parcelMapper = parcelMapper;
        this.dataScope = dataScope;
    }

    public PageResult<LandQuality> page(QualityQuery q) {
        QueryWrapper<LandQuality> w = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like("parcel_code", kw).or().like("parcel_name", kw).or().like("contractor_name", kw));
        }
        if (q.getEvalYear() != null) w.eq("eval_year", q.getEvalYear());
        if (q.getGradeMin() != null) w.ge("grade", q.getGradeMin());
        if (q.getGradeMax() != null) w.le("grade", q.getGradeMax());
        if (StringUtils.hasText(q.getSoilType())) w.eq("soil_type", q.getSoilType());
        if (StringUtils.hasText(q.getObstacle())) w.eq("obstacle", q.getObstacle());
        dataScope.apply(w, "region_id");
        w.orderByDesc("eval_year", "grade");
        IPage<LandQuality> p = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    @Transactional
    public Long create(LandQuality e) {
        validate(e);
        fillParcelInfo(e);
        e.setCreatedBy(UserContext.username());
        e.setDeleted(0);
        mapper.insert(e);
        return e.getId();
    }

    @Transactional
    public void update(LandQuality e) {
        require(e.getId());
        validate(e);
        fillParcelInfo(e);
        e.setDeleted(null);
        e.setDeleteReason(null);
        e.setDeletedBy(null);
        e.setDeletedAt(null);
        e.setCreatedBy(null);
        e.setCreatedAt(null);
        mapper.updateById(e);
    }

    /** 批量编辑: 对选中的多条记录统一修改部分字段(仅修改非空字段) */
    @Transactional
    public int batchUpdate(java.util.List<Long> ids, LandQuality u) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要修改的记录");
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<LandQuality> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        w.in("id", ids);
        boolean any = false;
        if (u.getEvalYear() != null) { w.set("eval_year", u.getEvalYear()); any = true; }
        if (u.getGrade() != null) {
            if (u.getGrade() < 1 || u.getGrade() > 10) throw new ApiException("地力等级必须为 1-10");
            w.set("grade", u.getGrade()); any = true;
        }
        if (StringUtils.hasText(u.getSoilType())) { w.set("soil_type", u.getSoilType()); any = true; }
        if (StringUtils.hasText(u.getObstacle())) { w.set("obstacle", u.getObstacle()); any = true; }
        if (StringUtils.hasText(u.getOrg())) { w.set("org", u.getOrg()); any = true; }
        if (!any) throw new ApiException("请至少填写一个要统一修改的字段");
        return mapper.update(null, w);
    }

    /** 删除: 质量数据为历史重要组成, 仅管理员可删, 软删除 */
    @Transactional
    public void delete(Long id, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除质量评价数据");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        require(id);
        LandQuality meta = new LandQuality();
        meta.setId(id);
        meta.setDeleteReason(reason);
        meta.setDeletedBy(UserContext.username());
        meta.setDeletedAt(LocalDateTime.now());
        mapper.updateById(meta);
        mapper.deleteById(id);
    }

    // ---------------- 校验 ----------------

    private void validate(LandQuality e) {
        if (!StringUtils.hasText(e.getParcelCode())) throw new ApiException("地块编码不能为空");
        if (e.getEvalYear() == null) throw new ApiException("评价年度不能为空");
        if (e.getEvalYear() > Year.now().getValue()) throw new ApiException("评价年度不能为未来年份");
        if (e.getGrade() == null || e.getGrade() < 1 || e.getGrade() > 10) {
            throw new ApiException("地力等级必须为 1-10 之间");
        }
        nonNegative("综合得分", e.getScore());
        nonNegative("有机质含量", e.getOrganicMatter());
        nonNegative("全氮含量", e.getTotalN());
        nonNegative("有效磷含量", e.getAvailP());
        nonNegative("速效钾含量", e.getAvailK());
        if (e.getPh() != null && (e.getPh().compareTo(BigDecimal.ZERO) < 0
                || e.getPh().compareTo(new BigDecimal("14")) > 0)) {
            throw new ApiException("pH 值必须在 0-14 之间");
        }
    }

    private void nonNegative(String name, BigDecimal v) {
        if (v != null && v.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(name + "不能为负数");
        }
    }

    private void fillParcelInfo(LandQuality e) {
        if (StringUtils.hasText(e.getParcelName()) && e.getRegionId() != null) return;
        LandParcel parcel = parcelMapper.selectOne(
                new QueryWrapper<LandParcel>().eq("parcel_code", e.getParcelCode()));
        if (parcel != null) {
            if (!StringUtils.hasText(e.getParcelName())) e.setParcelName(parcel.getName());
            if (e.getRegionId() == null) e.setRegionId(parcel.getRegionId());
            if (!StringUtils.hasText(e.getRegionPath())) e.setRegionPath(parcel.getRegionPath());
            if (!StringUtils.hasText(e.getContractorName())) e.setContractorName(parcel.getContractorName());
        }
    }

    private LandQuality require(Long id) {
        LandQuality e = mapper.selectById(id);
        if (e == null) throw new ApiException(404, "质量评价记录不存在");
        return e;
    }
}
