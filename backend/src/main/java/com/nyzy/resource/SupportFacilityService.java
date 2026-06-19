package com.nyzy.resource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.resource.dto.SupportQuery;
import com.nyzy.resource.entity.FacilityCategory;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SupportFacilityService {

    private final SupportFacilityMapper mapper;
    private final FacilityCategoryMapper categoryMapper;
    private final com.nyzy.auth.DataScope dataScope;

    public SupportFacilityService(SupportFacilityMapper mapper, FacilityCategoryMapper categoryMapper,
                                  com.nyzy.auth.DataScope dataScope) {
        this.mapper = mapper;
        this.categoryMapper = categoryMapper;
        this.dataScope = dataScope;
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
        return f.getId();
    }

    @Transactional
    public void update(SupportFacility f) {
        get(f.getId());
        validate(f);
        f.setCategoryName(null);
        f.setDeleted(null);
        f.setDeleteReason(null);
        f.setDeletedBy(null);
        f.setDeletedAt(null);
        f.setCreatedBy(null);
        f.setCreatedAt(null);
        mapper.updateById(f);
    }

    @Transactional
    public void audit(Long id, boolean pass) {
        SupportFacility old = get(id);
        if (!"PENDING".equals(old.getAuditStatus())) throw new ApiException("仅待审核设施可执行审核");
        SupportFacility upd = new SupportFacility();
        upd.setId(id);
        upd.setAuditStatus(pass ? "APPROVED" : "REJECTED");
        mapper.updateById(upd);
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
    }

    private void validate(SupportFacility f) {
        if (!StringUtils.hasText(f.getName())) throw new ApiException("设施名称不能为空");
        if (f.getCategoryId() == null) throw new ApiException("请选择设施分类");
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
