package com.nyzy.audit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nyzy.abandon.AbandonService;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.common.ApiException;
import com.nyzy.resource.SupportFacilityService;
import com.nyzy.resource.WaterFacilityService;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.SupportFacilityMapper;
import com.nyzy.resource.mapper.WaterFacilityMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待审核统一中心: 聚合各模块待审核记录, 统一审批.
 * 审批动作委托给各模块已有 Service, 复用其状态机与校验.
 */
@Service
public class AuditCenterService {

    private final WaterFacilityMapper waterMapper;
    private final SupportFacilityMapper supportMapper;
    private final AbandonParcelMapper abandonMapper;
    private final WaterFacilityService waterService;
    private final SupportFacilityService supportService;
    private final AbandonService abandonService;
    private final com.nyzy.notification.NotificationService notificationService;

    public AuditCenterService(WaterFacilityMapper waterMapper, SupportFacilityMapper supportMapper,
                              AbandonParcelMapper abandonMapper, WaterFacilityService waterService,
                              SupportFacilityService supportService, AbandonService abandonService,
                              com.nyzy.notification.NotificationService notificationService) {
        this.waterMapper = waterMapper;
        this.supportMapper = supportMapper;
        this.abandonMapper = abandonMapper;
        this.waterService = waterService;
        this.supportService = supportService;
        this.abandonService = abandonService;
        this.notificationService = notificationService;
    }

    public List<AuditItem> list(String bizType) {
        List<AuditItem> all = new ArrayList<>();
        if (matches(bizType, "water")) {
            for (WaterFacility f : waterMapper.selectList(
                    new QueryWrapper<WaterFacility>().eq("audit_status", "PENDING"))) {
                all.add(item("water", "水利设施", f.getId(), f.getName(), f.getType(),
                        f.getCreatedBy(), f.getCreatedAt()));
            }
        }
        if (matches(bizType, "support")) {
            for (SupportFacility f : supportMapper.selectList(
                    new QueryWrapper<SupportFacility>().eq("audit_status", "PENDING"))) {
                all.add(item("support", "配套设施", f.getId(), f.getName(), f.getServiceAbility(),
                        f.getCreatedBy(), f.getCreatedAt()));
            }
        }
        if (matches(bizType, "abandon")) {
            for (AbandonParcel a : abandonMapper.selectList(
                    new QueryWrapper<AbandonParcel>().eq("govern_status", "PENDING"))) {
                all.add(item("abandon", "撂荒地块", a.getId(), a.getParcelName(),
                        a.getParcelCode() + " / " + a.getReasonText(), a.getReporter(), a.getCreatedAt()));
            }
        }
        all.sort(Comparator.comparing(AuditItem::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all;
    }

    /** 审批: pass=true 通过 / false 退回 */
    public void audit(String bizType, Long id, boolean pass) {
        String creator = null, name = null;
        switch (bizType == null ? "" : bizType) {
            case "water":
                WaterFacility wf = waterMapper.selectById(id);
                if (wf != null) { creator = wf.getCreatedBy(); name = wf.getName(); }
                waterService.audit(id, pass);
                break;
            case "support":
                SupportFacility sf = supportMapper.selectById(id);
                if (sf != null) { creator = sf.getCreatedBy(); name = sf.getName(); }
                supportService.audit(id, pass);
                break;
            case "abandon":
                AbandonParcel ap = abandonMapper.selectById(id);
                if (ap != null) { creator = ap.getCreatedBy(); name = ap.getParcelName(); }
                // 撂荒待审核 -> 通过转未治理 / 退回转已驳回
                abandonService.changeStatus(id, pass ? "UNGOVERNED" : "REJECTED", null);
                break;
            default:
                throw new ApiException("非法的业务类型: " + bizType);
        }
        if (creator != null) {
            notificationService.notifyUser(creator,
                    pass ? "审核已通过" : "审核被退回",
                    "您提交的「" + name + "」" + (pass ? "审核已通过" : "审核被退回，请修改后重新提交"),
                    bizType, id);
        }
    }

    private boolean matches(String filter, String type) {
        return filter == null || filter.isEmpty() || filter.equals(type);
    }

    private AuditItem item(String bizType, String bizTypeName, Long id, String title, String subtitle,
                           String submittedBy, java.time.LocalDateTime submittedAt) {
        AuditItem i = new AuditItem();
        i.setBizType(bizType);
        i.setBizTypeName(bizTypeName);
        i.setBizId(id);
        i.setTitle(title);
        i.setSubtitle(subtitle);
        i.setSubmittedBy(submittedBy);
        i.setSubmittedAt(submittedAt);
        return i;
    }
}
