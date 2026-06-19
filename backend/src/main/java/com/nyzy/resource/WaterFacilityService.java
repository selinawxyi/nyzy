package com.nyzy.resource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.resource.dto.WaterQuery;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class WaterFacilityService {

    private final WaterFacilityMapper mapper;
    private final com.nyzy.auth.DataScope dataScope;

    public WaterFacilityService(WaterFacilityMapper mapper, com.nyzy.auth.DataScope dataScope) {
        this.mapper = mapper;
        this.dataScope = dataScope;
    }

    public PageResult<WaterFacility> page(WaterQuery q) {
        QueryWrapper<WaterFacility> w = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like("name", kw).or().like("manager", kw));
        }
        if (StringUtils.hasText(q.getType())) w.eq("type", q.getType());
        if (StringUtils.hasText(q.getRunStatus())) w.eq("run_status", q.getRunStatus());
        if (StringUtils.hasText(q.getAuditStatus())) w.eq("audit_status", q.getAuditStatus());
        dataScope.apply(w, "region_id");
        w.orderByDesc("id");
        IPage<WaterFacility> p = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    public WaterFacility get(Long id) {
        WaterFacility f = mapper.selectById(id);
        if (f == null) throw new ApiException(404, "水利设施不存在");
        return f;
    }

    /** 标注(新增): 进入待审核状态 */
    @Transactional
    public Long create(WaterFacility f) {
        validate(f);
        f.setAuditStatus("PENDING");
        f.setCreatedBy(UserContext.username());
        f.setDeleted(0);
        mapper.insert(f);
        return f.getId();
    }

    /** 修改: 关键字段(类型/经纬度)变更需重新审核 */
    @Transactional
    public void update(WaterFacility f) {
        WaterFacility old = get(f.getId());
        validate(f);
        boolean keyChanged = !Objects.equals(old.getType(), f.getType())
                || numChanged(old.getLng(), f.getLng())
                || numChanged(old.getLat(), f.getLat());
        f.setAuditStatus(keyChanged ? "PENDING" : old.getAuditStatus());
        f.setDeleted(null);
        f.setDeleteReason(null);
        f.setDeletedBy(null);
        f.setDeletedAt(null);
        f.setCreatedBy(null);
        f.setCreatedAt(null);
        mapper.updateById(f);
    }

    /** 审核: 通过 / 退回 */
    @Transactional
    public void audit(Long id, boolean pass) {
        WaterFacility old = get(id);
        if (!"PENDING".equals(old.getAuditStatus())) {
            throw new ApiException("仅待审核设施可执行审核");
        }
        WaterFacility upd = new WaterFacility();
        upd.setId(id);
        upd.setAuditStatus(pass ? "APPROVED" : "REJECTED");
        mapper.updateById(upd);
    }

    /** 批量修改: 对选中设施统一改运行状态/责任人/电话(仅非空字段) */
    @Transactional
    public int batchUpdate(java.util.List<Long> ids, WaterFacility u) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要修改的设施");
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<WaterFacility> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        w.in("id", ids);
        boolean any = false;
        if (StringUtils.hasText(u.getRunStatus())) { w.set("run_status", u.getRunStatus()); any = true; }
        if (StringUtils.hasText(u.getManager())) { w.set("manager", u.getManager()); any = true; }
        if (StringUtils.hasText(u.getPhone())) { w.set("phone", u.getPhone()); any = true; }
        if (u.getLastMaintainDate() != null) { w.set("last_maintain_date", u.getLastMaintainDate()); any = true; }
        if (!any) throw new ApiException("请至少填写一个要统一修改的字段");
        return mapper.update(null, w);
    }

    /** 删除: 仅管理员, 必填原因, 软删除入回收站 */
    @Transactional
    public void delete(Long id, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除水利设施");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        get(id);
        WaterFacility meta = new WaterFacility();
        meta.setId(id);
        meta.setDeleteReason(reason);
        meta.setDeletedBy(UserContext.username());
        meta.setDeletedAt(LocalDateTime.now());
        mapper.updateById(meta);
        mapper.deleteById(id);
    }

    private void validate(WaterFacility f) {
        if (!StringUtils.hasText(f.getName())) throw new ApiException("设施名称不能为空");
        if (!StringUtils.hasText(f.getType())) throw new ApiException("设施类型不能为空");
        if (f.getCoverArea() != null && f.getCoverArea().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException("覆盖面积不能为负数");
        }
    }

    private boolean numChanged(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return !Objects.equals(a, b);
        return a.compareTo(b) != 0;
    }
}
