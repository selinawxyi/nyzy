package com.nyzy.export;

import com.nyzy.abandon.AbandonService;
import com.nyzy.abandon.dto.AbandonQuery;
import com.nyzy.abandon.entity.AbandonParcel;
import com.nyzy.abandon.entity.AbandonTask;
import com.nyzy.common.ExcelUtil;
import com.nyzy.cultivation.PlantingService;
import com.nyzy.cultivation.QualityService;
import com.nyzy.cultivation.dto.PlantingQuery;
import com.nyzy.cultivation.dto.QualityQuery;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.land.ParcelService;
import com.nyzy.land.dto.ParcelQuery;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.resource.SupportFacilityService;
import com.nyzy.resource.WaterFacilityService;
import com.nyzy.resource.dto.SupportQuery;
import com.nyzy.resource.dto.WaterQuery;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.entity.WaterFacility;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final Map<String, String> GOV = mapOf(
            "PENDING", "待审核", "UNGOVERNED", "未治理", "GOVERNING", "治理中", "GOVERNED", "已治理", "REJECTED", "已驳回");
    private static final Map<String, String> SOURCE = mapOf(
            "REMOTE", "遥感监测", "PATROL", "网格员巡查", "REPORT", "群众举报",
            "STAT", "统计上报", "FARMER", "农户填报");
    private static final Map<String, String> AUDIT = mapOf(
            "PENDING", "待审核", "APPROVED", "已通过", "REJECTED", "已退回");
    private static final Map<String, String> PSTATUS = mapOf("VALID", "有效", "INVALID", "已无效");
    private static final Map<String, String> TASK = mapOf(
            "ISSUED", "已下发", "HANDLING", "办理中", "ACCEPTING", "待验收", "DONE", "验收通过", "RETURNED", "已退回");

    private final AbandonService abandonService;
    private final ParcelService parcelService;
    private final WaterFacilityService waterService;
    private final SupportFacilityService supportService;
    private final PlantingService plantingService;
    private final QualityService qualityService;

    public ExportController(AbandonService abandonService, ParcelService parcelService,
                            WaterFacilityService waterService, SupportFacilityService supportService,
                            PlantingService plantingService, QualityService qualityService) {
        this.abandonService = abandonService;
        this.parcelService = parcelService;
        this.waterService = waterService;
        this.supportService = supportService;
        this.plantingService = plantingService;
        this.qualityService = qualityService;
    }

    @GetMapping("/abandon")
    public void abandon(AbandonQuery q, HttpServletResponse resp) {
        q.setSize(100000);
        List<AbandonParcel> list = abandonService.page(q).getList();
        List<String> head = Arrays.asList("地块编码", "地块名称", "坐落位置", "撂荒年份", "面积(亩)",
                "撂荒原因", "上报人", "发现日期", "治理状态", "责任人");
        List<List<Object>> rows = new ArrayList<>();
        for (AbandonParcel a : list) {
            rows.add(Arrays.asList(a.getParcelCode(), a.getParcelName(), a.getRegionPath(), a.getAbandonYear(),
                    a.getArea(), a.getReasonText(), a.getReporter(), str(a.getFoundDate()),
                    GOV.getOrDefault(a.getGovernStatus(), a.getGovernStatus()), a.getManager()));
        }
        ExcelUtil.write(resp, "撂荒地块台账", head, rows);
    }

    @GetMapping("/abandon-task")
    public void abandonTask(@org.springframework.web.bind.annotation.RequestParam(required = false) String status,
                            HttpServletResponse resp) {
        List<AbandonTask> list = abandonService.tasks(status);
        List<String> head = Arrays.asList("任务编号", "任务名称", "关联地块", "责任单位", "责任人",
                "治理面积目标(亩)", "完成时限", "进度(%)", "任务状态");
        List<List<Object>> rows = new ArrayList<>();
        for (AbandonTask t : list) {
            rows.add(Arrays.asList(t.getTaskNo(), t.getName(), t.getParcelCode(), t.getRespUnit(),
                    t.getRespPerson(), t.getTargetArea(), str(t.getDeadline()), t.getProgress(),
                    TASK.getOrDefault(t.getTaskStatus(), t.getTaskStatus())));
        }
        ExcelUtil.write(resp, "撂荒治理任务台账", head, rows);
    }

    @GetMapping("/parcel")
    public void parcel(ParcelQuery q, HttpServletResponse resp) {
        q.setSize(100000);
        List<LandParcel> list = parcelService.page(q).getList();
        List<String> head = Arrays.asList("地块编码", "地块名称", "坐落位置", "承包方", "承包方编码",
                "确权面积(亩)", "地块用途", "承包起始", "承包终止");
        List<List<Object>> rows = new ArrayList<>();
        for (LandParcel p : list) {
            rows.add(Arrays.asList(p.getParcelCode(), p.getName(), p.getRegionPath(), p.getContractorName(),
                    p.getContractorCode(), p.getArea(), p.getLandUse(), str(p.getContractStart()), str(p.getContractEnd())));
        }
        ExcelUtil.write(resp, "确权地块台账", head, rows);
    }

    @GetMapping("/water")
    public void water(WaterQuery q, HttpServletResponse resp) {
        q.setSize(100000);
        List<WaterFacility> list = waterService.page(q).getList();
        List<String> head = Arrays.asList("设施名称", "类型", "所在位置", "建设年份", "覆盖面积(亩)",
                "运行状态", "管护责任人", "联系电话", "审核状态");
        List<List<Object>> rows = new ArrayList<>();
        for (WaterFacility f : list) {
            rows.add(Arrays.asList(f.getName(), f.getType(), f.getRegionPath(), f.getBuildYear(),
                    f.getCoverArea(), f.getRunStatus(), f.getManager(), f.getPhone(),
                    AUDIT.getOrDefault(f.getAuditStatus(), f.getAuditStatus())));
        }
        ExcelUtil.write(resp, "水利设施台账", head, rows);
    }

    @GetMapping("/support")
    public void support(SupportQuery q, HttpServletResponse resp) {
        q.setSize(100000);
        List<SupportFacility> list = supportService.page(q).getList();
        List<String> head = Arrays.asList("设施名称", "分类", "所在位置", "服务能力", "运营状态",
                "运营主体", "联系电话", "审核状态");
        List<List<Object>> rows = new ArrayList<>();
        for (SupportFacility f : list) {
            rows.add(Arrays.asList(f.getName(), f.getCategoryName(), f.getRegionPath(), f.getServiceAbility(),
                    f.getOperateStatus(), f.getOperateSubject(), f.getPhone(),
                    AUDIT.getOrDefault(f.getAuditStatus(), f.getAuditStatus())));
        }
        ExcelUtil.write(resp, "配套设施台账", head, rows);
    }

    @GetMapping("/planting")
    public void planting(PlantingQuery q, HttpServletResponse resp) {
        q.setSize(100000);
        List<PlantingRecord> list = plantingService.page(q).getList();
        List<String> head = Arrays.asList("地块编码", "地块名称", "种植年度", "作物", "品种",
                "面积(亩)", "产量(kg/亩)", "数据来源", "状态");
        List<List<Object>> rows = new ArrayList<>();
        for (PlantingRecord r : list) {
            rows.add(Arrays.asList(r.getParcelCode(), r.getParcelName(), r.getPlantYear(), r.getCrop(),
                    r.getVariety(), r.getArea(), r.getYieldPerMu(),
                    SOURCE.getOrDefault(r.getDataSource(), r.getDataSource()),
                    PSTATUS.getOrDefault(r.getStatus(), r.getStatus())));
        }
        ExcelUtil.write(resp, "种植记录台账", head, rows);
    }

    @GetMapping("/quality")
    public void quality(QualityQuery q, HttpServletResponse resp) {
        q.setSize(100000);
        List<LandQuality> list = qualityService.page(q).getList();
        List<String> head = Arrays.asList("地块编码", "地块名称", "承包方", "评价年度", "地力等级",
                "综合得分", "土壤类型", "pH", "障碍因素", "适宜作物");
        List<List<Object>> rows = new ArrayList<>();
        for (LandQuality e : list) {
            rows.add(Arrays.asList(e.getParcelCode(), e.getParcelName(), e.getContractorName(), e.getEvalYear(),
                    e.getGrade(), e.getScore(), e.getSoilType(), e.getPh(), e.getObstacle(), e.getSuitableCrops()));
        }
        ExcelUtil.write(resp, "耕地质量台账", head, rows);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
