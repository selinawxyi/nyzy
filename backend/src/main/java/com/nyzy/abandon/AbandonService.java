package com.nyzy.abandon;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nyzy.abandon.dto.AbandonDetail;
import com.nyzy.abandon.dto.AbandonQuery;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.entity.AbandonReason;
import com.nyzy.abandon.entity.AbandonTask;
import com.nyzy.abandon.mapper.AbandonParcelMapper;
import com.nyzy.abandon.mapper.AbandonReasonMapper;
import com.nyzy.abandon.mapper.AbandonTaskMapper;
import com.nyzy.auth.UserContext;
import com.nyzy.common.ApiException;
import com.nyzy.common.PageResult;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.cultivation.mapper.PlantingRecordMapper;
import com.nyzy.system.entity.AuditLog;
import com.nyzy.system.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

@Service
public class AbandonService {

    /** 撂荒地块状态机允许的流转 */
    private static final Map<String, Set<String>> TRANSITIONS = new HashMap<>();
    /** 阻止删除的活跃治理任务状态 */
    private static final Set<String> ACTIVE_TASK = new HashSet<>(Arrays.asList("ISSUED", "HANDLING", "ACCEPTING"));
    private static final Set<String> VALID_STATUS = new HashSet<>(
            Arrays.asList("PENDING", "UNGOVERNED", "GOVERNING", "GOVERNED", "REJECTED"));

    static {
        TRANSITIONS.put("PENDING", new HashSet<>(Arrays.asList("UNGOVERNED", "REJECTED")));
        TRANSITIONS.put("UNGOVERNED", new HashSet<>(Arrays.asList("GOVERNING")));
        TRANSITIONS.put("GOVERNING", new HashSet<>(Arrays.asList("GOVERNED", "UNGOVERNED")));
        TRANSITIONS.put("GOVERNED", new HashSet<>(Arrays.asList("GOVERNING")));
        TRANSITIONS.put("REJECTED", Collections.emptySet());
    }

    private final AbandonParcelMapper parcelMapper;
    private final AbandonReasonMapper reasonMapper;
    private final AbandonTaskMapper taskMapper;
    private final AuditLogMapper auditLogMapper;
    private final PlantingRecordMapper plantingMapper;
    private final com.nyzy.auth.DataScope dataScope;
    private final com.nyzy.notification.NotificationService notificationService;

    public AbandonService(AbandonParcelMapper parcelMapper, AbandonReasonMapper reasonMapper,
                          AbandonTaskMapper taskMapper, AuditLogMapper auditLogMapper,
                          PlantingRecordMapper plantingMapper, com.nyzy.auth.DataScope dataScope,
                          com.nyzy.notification.NotificationService notificationService) {
        this.parcelMapper = parcelMapper;
        this.reasonMapper = reasonMapper;
        this.taskMapper = taskMapper;
        this.auditLogMapper = auditLogMapper;
        this.plantingMapper = plantingMapper;
        this.dataScope = dataScope;
        this.notificationService = notificationService;
    }

    // ---------------- 查询 ----------------

    public PageResult<AbandonParcel> page(AbandonQuery q) {
        QueryWrapper<AbandonParcel> w = new QueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like("parcel_code", kw).or().like("parcel_name", kw).or().like("reporter", kw));
        }
        if (StringUtils.hasText(q.getGovernStatus())) w.eq("govern_status", q.getGovernStatus());
        if (q.getAbandonYear() != null) w.eq("abandon_year", q.getAbandonYear());
        if (StringUtils.hasText(q.getSource())) w.eq("source", q.getSource());
        if (StringUtils.hasText(q.getReasonType())) w.eq("reason_type", q.getReasonType());
        dataScope.apply(w, "region_id");
        w.orderByDesc("found_date", "id");

        IPage<AbandonParcel> p = parcelMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    public AbandonDetail detail(Long id) {
        AbandonParcel parcel = parcelMapper.selectById(id);
        if (parcel == null) throw new ApiException(404, "撂荒地块不存在");
        AbandonDetail d = new AbandonDetail();
        d.setParcel(parcel);
        d.setReasons(reasonMapper.selectList(
                new QueryWrapper<AbandonReason>().eq("abandon_id", id).orderByDesc("id")));
        d.setTasks(taskMapper.selectList(
                new QueryWrapper<AbandonTask>().eq("abandon_id", id).orderByDesc("id")));
        return d;
    }

    // ---------------- 增改删 ----------------

    @Transactional
    public Long create(AbandonParcel p) {
        validateYear(p.getAbandonYear());
        if (!StringUtils.hasText(p.getParcelCode())) throw new ApiException("地块编码不能为空");
        if (!StringUtils.hasText(p.getGovernStatus())) p.setGovernStatus("UNGOVERNED");
        if (!VALID_STATUS.contains(p.getGovernStatus())) throw new ApiException("非法治理状态");
        if (p.getFoundDate() == null) p.setFoundDate(LocalDate.now());
        p.setCreatedBy(UserContext.username());
        p.setDeleted(0);
        parcelMapper.insert(p);
        audit("abandon", p.getId(), "CREATE", "新增撂荒地块 " + p.getParcelCode());
        if ("PENDING".equals(p.getGovernStatus())) {
            notificationService.notifyAdmins("新撂荒地块待审核",
                    p.getParcelName() + "(" + p.getParcelCode() + ") 由 " + p.getReporter() + " 上报，请核查",
                    "abandon", p.getId());
        }
        return p.getId();
    }

    @Transactional
    public void update(AbandonParcel p) {
        AbandonParcel old = require(p.getId());
        validateYear(p.getAbandonYear());
        // 治理状态变更走状态机校验
        if (StringUtils.hasText(p.getGovernStatus())
                && !Objects.equals(old.getGovernStatus(), p.getGovernStatus())) {
            checkTransition(old.getGovernStatus(), p.getGovernStatus());
        }
        // 软删除相关字段不允许通过普通更新修改
        p.setDeleted(null);
        p.setDeleteReason(null);
        p.setDeletedAt(null);
        p.setDeletedBy(null);
        p.setCreatedBy(null);
        p.setCreatedAt(null);
        parcelMapper.updateById(p);
        audit("abandon", p.getId(), "UPDATE", "修改撂荒地块");
    }

    /** 治理状态变更 (状态机) */
    @Transactional
    public void changeStatus(Long id, String target, String remark) {
        AbandonParcel old = require(id);
        checkTransition(old.getGovernStatus(), target);
        AbandonParcel upd = new AbandonParcel();
        upd.setId(id);
        upd.setGovernStatus(target);
        if (StringUtils.hasText(remark)) upd.setRemark(remark);
        parcelMapper.updateById(upd);
        audit("abandon", id, "STATUS", old.getGovernStatus() + " -> " + target);
    }

    /** 软删除: 仅管理员, 必填原因, 有活跃治理任务则阻止 */
    @Transactional
    public void delete(Long id, String reason) {
        if (!UserContext.isAdmin()) throw new ApiException(403, "仅管理员可删除撂荒地块");
        if (!StringUtils.hasText(reason)) throw new ApiException("请填写删除原因");
        AbandonParcel old = require(id);
        long activeTasks = taskMapper.selectCount(new QueryWrapper<AbandonTask>()
                .eq("abandon_id", id).in("task_status", ACTIVE_TASK));
        if (activeTasks > 0) {
            throw new ApiException("该地块存在进行中的治理任务, 请先终止相关任务再删除");
        }
        // 先写入删除元信息 (@TableLogic 字段会被普通 update 过滤, 故分两步)
        AbandonParcel meta = new AbandonParcel();
        meta.setId(id);
        meta.setDeleteReason(reason);
        meta.setDeletedBy(UserContext.username());
        meta.setDeletedAt(LocalDateTime.now());
        parcelMapper.updateById(meta);
        // 再触发逻辑删除, 由 MyBatis-Plus 置 is_deleted=1
        parcelMapper.deleteById(id);
        audit("abandon", id, "DELETE", "软删除, 原因: " + reason);
    }

    // ---------------- 原因填报 ----------------

    @Transactional
    public Long addReason(AbandonReason r) {
        require(r.getAbandonId());
        if (!StringUtils.hasText(r.getReporter())) r.setReporter(UserContext.username());
        reasonMapper.insert(r);
        // 回写主表原因展示
        if (StringUtils.hasText(r.getReasonTypes())) {
            AbandonParcel upd = new AbandonParcel();
            upd.setId(r.getAbandonId());
            upd.setReasonType(r.getReasonTypes().split(",")[0]);
            parcelMapper.updateById(upd);
        }
        audit("abandon", r.getAbandonId(), "REASON", "填报撂荒原因");
        return r.getId();
    }

    // ---------------- 治理任务 ----------------

    @Transactional
    public Long createTask(AbandonTask t) {
        AbandonParcel parcel = require(t.getAbandonId());
        if (t.getTaskNo() == null) {
            t.setTaskNo("T" + LocalDate.now().toString().replace("-", "")
                    + String.format("%04d", new Random().nextInt(10000)));
        }
        t.setParcelCode(parcel.getParcelCode());
        if (!StringUtils.hasText(t.getTaskStatus())) t.setTaskStatus("ISSUED");
        if (t.getProgress() == null) t.setProgress(0);
        t.setCreatedBy(UserContext.username());
        taskMapper.insert(t);
        // 下发任务后地块进入治理中
        if ("UNGOVERNED".equals(parcel.getGovernStatus())) {
            changeStatus(parcel.getId(), "GOVERNING", null);
        }
        audit("abandon_task", t.getId(), "CREATE", "下发治理任务 " + t.getTaskNo());
        if (parcel.getCreatedBy() != null) {
            notificationService.notifyUser(parcel.getCreatedBy(), "撂荒地块已下发治理任务",
                    parcel.getParcelName() + " 治理任务「" + t.getName() + "」已下发，责任人：" + t.getRespPerson(),
                    "abandon", parcel.getId());
        }
        return t.getId();
    }

    /** 任务列表 (治理台账, 可按状态过滤) */
    public List<AbandonTask> tasks(String status) {
        QueryWrapper<AbandonTask> w = new QueryWrapper<AbandonTask>().orderByDesc("id");
        if (StringUtils.hasText(status)) w.eq("task_status", status);
        return taskMapper.selectList(w);
    }

    /** 进度填报: <100 为办理中, =100 进入待验收 */
    @Transactional
    public void updateTaskProgress(Long taskId, Integer progress) {
        AbandonTask t = requireTask(taskId);
        if (progress == null || progress < 0 || progress > 100) throw new ApiException("进度需在 0-100 之间");
        if ("DONE".equals(t.getTaskStatus())) throw new ApiException("任务已验收完成, 不可再填报进度");
        AbandonTask upd = new AbandonTask();
        upd.setId(taskId);
        upd.setProgress(progress);
        upd.setTaskStatus(progress >= 100 ? "ACCEPTING" : "HANDLING");
        taskMapper.updateById(upd);
        audit("abandon_task", taskId, "PROGRESS", "进度更新至 " + progress + "%");
    }

    /**
     * 验收反馈: 通过则任务完成、地块转"已治理"、可回写治理后种植记录;
     * 不通过则退回办理.
     */
    @Transactional
    public void acceptTask(Long taskId, boolean pass, String crop, BigDecimal area, Integer year) {
        AbandonTask t = requireTask(taskId);
        AbandonTask upd = new AbandonTask();
        upd.setId(taskId);
        if (!pass) {
            upd.setTaskStatus("RETURNED");
            taskMapper.updateById(upd);
            audit("abandon_task", taskId, "ACCEPT", "验收不通过, 退回办理");
            if (t.getCreatedBy() != null) {
                notificationService.notifyUser(t.getCreatedBy(), "治理任务验收未通过",
                        "任务「" + t.getName() + "」验收未通过，已退回办理", "abandon_task", taskId);
            }
            return;
        }
        upd.setTaskStatus("DONE");
        upd.setProgress(100);
        taskMapper.updateById(upd);

        AbandonParcel parcel = parcelMapper.selectById(t.getAbandonId());
        if (parcel != null && !"GOVERNED".equals(parcel.getGovernStatus())) {
            // 经状态机: 治理中 -> 已治理
            if ("GOVERNING".equals(parcel.getGovernStatus())) {
                changeStatus(parcel.getId(), "GOVERNED", "治理任务验收通过");
            } else {
                AbandonParcel pu = new AbandonParcel();
                pu.setId(parcel.getId());
                pu.setGovernStatus("GOVERNED");
                parcelMapper.updateById(pu);
            }
        }
        // 回写治理后种植记录
        if (StringUtils.hasText(crop) && parcel != null) {
            PlantingRecord r = new PlantingRecord();
            r.setParcelCode(parcel.getParcelCode());
            r.setParcelName(parcel.getParcelName());
            r.setRegionId(parcel.getRegionId());
            r.setRegionPath(parcel.getRegionPath());
            r.setPlantYear(year != null ? year : Year.now().getValue());
            r.setSeason("SPRING");
            r.setCrop(crop);
            r.setArea(area);
            r.setDataSource("PATROL");
            r.setReporter(UserContext.username());
            r.setReportDate(LocalDate.now());
            r.setStatus("VALID");
            r.setDeleted(0);
            plantingMapper.insert(r);
        }
        audit("abandon_task", taskId, "ACCEPT", "验收通过, 地块转已治理" + (crop != null ? ", 回写种植:" + crop : ""));
        if (t.getCreatedBy() != null) {
            notificationService.notifyUser(t.getCreatedBy(), "治理任务验收通过",
                    "任务「" + t.getName() + "」验收通过，地块已转为「已治理」", "abandon_task", taskId);
        }
    }

    /** 责任单位制定/修改治理方案 */
    @Transactional
    public void updateTaskPlan(Long taskId, String plan) {
        requireTask(taskId);
        if (!StringUtils.hasText(plan)) throw new ApiException("治理方案不能为空");
        AbandonTask upd = new AbandonTask();
        upd.setId(taskId);
        upd.setPlan(plan);
        if (taskMapper.selectById(taskId).getProgress() == 0) upd.setTaskStatus("HANDLING");
        taskMapper.updateById(upd);
        audit("abandon_task", taskId, "PLAN", "制定治理方案");
    }

    /** 办理过程问题反馈: 记录并通知管理员协调 */
    @Transactional
    public void taskFeedback(Long taskId, String content) {
        AbandonTask t = requireTask(taskId);
        if (!StringUtils.hasText(content)) throw new ApiException("反馈内容不能为空");
        audit("abandon_task", taskId, "FEEDBACK", "问题反馈: " + content);
        notificationService.notifyAdmins("治理任务问题反馈",
                "任务「" + t.getName() + "」反馈: " + content, "abandon_task", taskId);
    }

    private AbandonTask requireTask(Long id) {
        AbandonTask t = taskMapper.selectById(id);
        if (t == null) throw new ApiException(404, "治理任务不存在");
        return t;
    }

    // ---------------- 内部工具 ----------------

    private AbandonParcel require(Long id) {
        AbandonParcel p = parcelMapper.selectById(id);
        if (p == null) throw new ApiException(404, "撂荒地块不存在");
        return p;
    }

    private void validateYear(Integer year) {
        if (year != null && year > Year.now().getValue()) {
            throw new ApiException("撂荒年份不能为未来年份");
        }
    }

    private void checkTransition(String from, String to) {
        if (!VALID_STATUS.contains(to)) throw new ApiException("非法目标状态: " + to);
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new ApiException("不允许的状态流转: " + from + " -> " + to);
        }
    }

    private void audit(String bizType, Long bizId, String action, String detail) {
        AuditLog log = new AuditLog();
        log.setBizType(bizType);
        log.setBizId(String.valueOf(bizId));
        log.setAction(action);
        log.setOperator(UserContext.username());
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }
}
