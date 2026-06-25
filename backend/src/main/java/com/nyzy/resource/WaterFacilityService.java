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
import com.nyzy.system.AuditLogService;
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
    private final AuditLogService auditLogService;

    public WaterFacilityService(WaterFacilityMapper mapper, com.nyzy.auth.DataScope dataScope,
                                AuditLogService auditLogService) {
        this.mapper = mapper;
        this.dataScope = dataScope;
        this.auditLogService = auditLogService;
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
        auditLogService.record("water", f.getId(), "CREATE", "新增水利设施 " + f.getName());
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
        if (mapper.updateById(f) == 0) {
            throw new ApiException(409, "数据已被他人修改, 请刷新后重试");
        }
        auditLogService.record("water", f.getId(), "UPDATE", "修改水利设施" + (keyChanged ? "(关键字段变更, 转待审核)" : ""));
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
        auditLogService.record("water", id, "STATUS", pass ? "审核通过" : "审核退回");
    }

    /** 批量修改: 对选中设施统一改运行状态/责任人/电话(仅非空字段) */
    @Transactional
    public int batchUpdate(java.util.List<Long> ids, WaterFacility u) {
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要修改的设施");
        boolean any = StringUtils.hasText(u.getRunStatus()) || StringUtils.hasText(u.getManager())
                || StringUtils.hasText(u.getPhone()) || u.getLastMaintainDate() != null;
        if (!any) throw new ApiException("请至少填写一个要统一修改的字段");
        // 逐行走 updateById(带 @Version) 而非一条 UpdateWrapper SQL, 以保留乐观锁校验; 版本冲突/已被删除的行跳过, 不计入返回数
        int n = 0;
        for (Long id : ids) {
            WaterFacility old = mapper.selectById(id);
            if (old == null) continue;
            WaterFacility patch = new WaterFacility();
            patch.setId(id);
            patch.setVersion(old.getVersion());
            if (StringUtils.hasText(u.getRunStatus())) patch.setRunStatus(u.getRunStatus());
            if (StringUtils.hasText(u.getManager())) patch.setManager(u.getManager());
            if (StringUtils.hasText(u.getPhone())) patch.setPhone(u.getPhone());
            if (u.getLastMaintainDate() != null) patch.setLastMaintainDate(u.getLastMaintainDate());
            n += mapper.updateById(patch);
        }
        return n;
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
        auditLogService.record("water", id, "DELETE", "软删除, 原因: " + reason);
    }

    /** 批量删除: 仅管理员, 必填原因, 软删除入回收站(与单条删除规则一致) */
    @Transactional
    public int batchDelete(java.util.List<Long> ids, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除水利设施");
        if (ids == null || ids.isEmpty()) throw new ApiException("请选择要删除的设施");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<WaterFacility> w =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<WaterFacility>().in("id", ids);
        w.set("delete_reason", reason).set("deleted_by", UserContext.username()).set("deleted_at", LocalDateTime.now());
        mapper.update(null, w);
        int n = mapper.deleteBatchIds(ids);
        for (Long id : ids) auditLogService.record("water", id, "DELETE", "批量软删除, 原因: " + reason);
        return n;
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
