package com.nyzy.land;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.land.entity.LandAnnotation;
import com.nyzy.land.mapper.LandAnnotationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AnnotationService {

    private final LandAnnotationMapper mapper;

    public AnnotationService(LandAnnotationMapper mapper) {
        this.mapper = mapper;
    }

    /** 列出地块标注: 仅展示「所有人可见」或「本人创建」的标注 */
    public List<LandAnnotation> list(Long parcelId) {
        Long uid = UserContext.get() == null ? null : UserContext.get().id;
        return mapper.selectList(new QueryWrapper<LandAnnotation>()
                .eq("parcel_id", parcelId)
                .and(w -> w.eq("visible_scope", "ALL").or().eq("owner_id", uid))
                .orderByDesc("id"));
    }

    @Transactional
    public Long add(LandAnnotation a) {
        if (a.getParcelId() == null) throw new ApiException("缺少地块ID");
        if (!StringUtils.hasText(a.getType())) a.setType("TEXT");
        if (!StringUtils.hasText(a.getVisibleScope())) a.setVisibleScope("SELF");
        a.setOwnerId(UserContext.get() == null ? null : UserContext.get().id);
        a.setOwnerName(UserContext.username());
        mapper.insert(a);
        return a.getId();
    }

    /** 删除标注: 仅本人或管理员 */
    @Transactional
    public void delete(Long id) {
        LandAnnotation a = mapper.selectById(id);
        if (a == null) throw new ApiException(404, "标注不存在");
        Long uid = UserContext.get() == null ? null : UserContext.get().id;
        if (!UserContext.isAdmin() && !java.util.Objects.equals(a.getOwnerId(), uid)) {
            throw new ApiException(403, "只能删除自己创建的标注");
        }
        mapper.deleteById(id);
    }
}
