package com.nyzy.resource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.common.ApiException;
import com.nyzy.resource.entity.FacilityCategory;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FacilityCategoryService {

    private final FacilityCategoryMapper mapper;
    private final SupportFacilityMapper supportMapper;

    public FacilityCategoryService(FacilityCategoryMapper mapper, SupportFacilityMapper supportMapper) {
        this.mapper = mapper;
        this.supportMapper = supportMapper;
    }

    /** 两级分类树 */
    public List<FacilityCategory> tree() {
        List<FacilityCategory> all = mapper.selectList(
                new QueryWrapper<FacilityCategory>().orderByAsc("sort", "id"));
        List<FacilityCategory> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
        for (FacilityCategory root : roots) {
            root.setChildren(all.stream()
                    .filter(c -> root.getId().equals(c.getParentId()))
                    .collect(Collectors.toList()));
        }
        return roots;
    }

    /** 二级分类扁平列表 (供下拉选择) */
    public List<FacilityCategory> leafList() {
        return mapper.selectList(new QueryWrapper<FacilityCategory>()
                .ne("parent_id", 0).orderByAsc("sort", "id"));
    }

    @Transactional
    public Long create(FacilityCategory c) {
        if (!StringUtils.hasText(c.getName())) throw new ApiException("分类名称不能为空");
        if (c.getParentId() == null) c.setParentId(0L);
        if (c.getSort() == null) c.setSort(0);
        if (c.getStatus() == null) c.setStatus(1);
        mapper.insert(c);
        return c.getId();
    }

    @Transactional
    public void update(FacilityCategory c) {
        require(c.getId());
        if (!StringUtils.hasText(c.getName())) throw new ApiException("分类名称不能为空");
        mapper.updateById(c);
    }

    /** 删除: 有子分类或有关联设施则阻止 */
    @Transactional
    public void delete(Long id) {
        require(id);
        long children = mapper.selectCount(new QueryWrapper<FacilityCategory>().eq("parent_id", id));
        if (children > 0) {
            throw new ApiException("该分类下存在子分类, 请先删除子分类");
        }
        long facilities = supportMapper.selectCount(
                new QueryWrapper<SupportFacility>().eq("category_id", id));
        if (facilities > 0) {
            throw new ApiException("该分类下已有 " + facilities + " 个设施, 请先转移或删除这些设施后再删除分类");
        }
        mapper.deleteById(id);
    }

    private FacilityCategory require(Long id) {
        FacilityCategory c = mapper.selectById(id);
        if (c == null) throw new ApiException(404, "分类不存在");
        return c;
    }
}
