package com.nyzy.imports;

import com.alibaba.excel.EasyExcel;
import com.nyzy.common.ApiException;
import com.nyzy.common.ExcelUtil;
import com.nyzy.common.Result;
import com.nyzy.cultivation.PlantingService;
import com.nyzy.cultivation.QualityService;
import com.nyzy.cultivation.entity.LandQuality;
import com.nyzy.cultivation.entity.PlantingRecord;
import com.nyzy.land.ParcelService;
import com.nyzy.land.entity.LandParcel;
import com.nyzy.resource.SupportFacilityService;
import com.nyzy.resource.WaterFacilityService;
import com.nyzy.resource.entity.FacilityCategory;
import com.nyzy.resource.entity.SupportFacility;
import com.nyzy.resource.entity.WaterFacility;
import com.nyzy.resource.mapper.FacilityCategoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Excel 批量导入 (种植/质量/水利设施) + 模板下载 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final Map<String, String> SEASON = mapOf("春", "SPRING", "夏", "SUMMER", "秋", "AUTUMN");
    private static final Map<String, String> SOURCE = mapOf("遥感", "REMOTE", "统计", "STAT", "农户", "FARMER", "巡查", "PATROL");

    private final PlantingService plantingService;
    private final QualityService qualityService;
    private final WaterFacilityService waterService;
    private final ParcelService parcelService;
    private final SupportFacilityService supportService;
    private final FacilityCategoryMapper categoryMapper;

    public ImportController(PlantingService plantingService, QualityService qualityService,
                            WaterFacilityService waterService, ParcelService parcelService,
                            SupportFacilityService supportService, FacilityCategoryMapper categoryMapper) {
        this.plantingService = plantingService;
        this.qualityService = qualityService;
        this.waterService = waterService;
        this.parcelService = parcelService;
        this.supportService = supportService;
        this.categoryMapper = categoryMapper;
    }

    // ---------------- 确权地块 ----------------

    @GetMapping("/parcel/template")
    public void parcelTemplate(HttpServletResponse resp) {
        List<String> head = Arrays.asList("地块编码*", "地块名称*", "坐落位置", "承包方姓名", "承包方编码",
                "确权面积(亩)", "地块用途", "承包起始(yyyy-MM-dd)", "承包终止(yyyy-MM-dd)");
        List<List<Object>> sample = new ArrayList<>();
        sample.add(Arrays.asList("JYX-001", "示例村一组1号地", "吉林省/延边州/延吉市/太平镇/太平村",
                "张三", "BX001", 20, "基本农田", "2015-01-01", "2044-12-31"));
        ExcelUtil.write(resp, "确权地块导入模板", head, sample);
    }

    @PostMapping("/parcel")
    public Result<ImportResult> importParcel(@RequestParam("file") MultipartFile file) {
        return Result.ok(process(file, (row, r) -> {
            LandParcel p = new LandParcel();
            p.setParcelCode(str(row, 0));
            p.setName(str(row, 1));
            p.setRegionPath(str(row, 2));
            p.setContractorName(str(row, 3));
            p.setContractorCode(str(row, 4));
            p.setArea(dec(row, 5));
            p.setLandUse(str(row, 6));
            p.setContractStart(date(row, 7));
            p.setContractEnd(date(row, 8));
            parcelService.create(p);
        }));
    }

    // ---------------- 确权地块批量更新 ----------------

    @GetMapping("/parcel-update/template")
    public void parcelUpdateTemplate(HttpServletResponse resp) {
        List<String> head = Arrays.asList("地块编码*", "承包方姓名", "承包方编码", "确权面积(亩)", "地块用途");
        List<List<Object>> sample = new ArrayList<>();
        sample.add(Arrays.asList("JYB-T002", "李志远", "BT20180018", 18.2, "基本农田"));
        ExcelUtil.write(resp, "确权地块批量更新模板", head, sample);
    }

    @PostMapping("/parcel-update")
    public Result<ImportResult> importParcelUpdate(@RequestParam("file") MultipartFile file) {
        return Result.ok(process(file, (row, r) -> {
            LandParcel p = new LandParcel();
            p.setParcelCode(str(row, 0));
            if (p.getParcelCode() == null) throw new ApiException("地块编码不能为空");
            p.setContractorName(str(row, 1));
            p.setContractorCode(str(row, 2));
            p.setArea(dec(row, 3));
            p.setLandUse(str(row, 4));
            parcelService.importUpdate(p);
        }));
    }

    // ---------------- 配套设施 ----------------

    @GetMapping("/support/template")
    public void supportTemplate(HttpServletResponse resp) {
        List<String> head = Arrays.asList("设施名称*", "分类名称*", "所在位置", "经度", "纬度",
                "服务范围", "服务能力", "运营状态", "运营主体", "联系电话");
        List<List<Object>> sample = new ArrayList<>();
        sample.add(Arrays.asList("示例烘干中心", "烘干中心", "吉林省/延边州/延吉市/太平镇",
                129.6, 42.94, "太平镇", "日处理100吨", "正常", "合作社", "13800000000"));
        ExcelUtil.write(resp, "配套设施导入模板", head, sample);
    }

    @PostMapping("/support")
    public Result<ImportResult> importSupport(@RequestParam("file") MultipartFile file) {
        return Result.ok(process(file, (row, r) -> {
            String catName = str(row, 1);
            if (catName == null) throw new ApiException("分类名称不能为空");
            FacilityCategory cat = categoryMapper.selectOne(
                    new QueryWrapper<FacilityCategory>().eq("name", catName).ne("parent_id", 0).last("limit 1"));
            if (cat == null) throw new ApiException("分类不存在: " + catName);
            SupportFacility f = new SupportFacility();
            f.setName(str(row, 0));
            f.setCategoryId(cat.getId());
            f.setRegionPath(str(row, 2));
            f.setLng(dec(row, 3));
            f.setLat(dec(row, 4));
            f.setServiceRange(str(row, 5));
            f.setServiceAbility(str(row, 6));
            f.setOperateStatus(str(row, 7) == null ? "正常" : str(row, 7));
            f.setOperateSubject(str(row, 8));
            f.setPhone(str(row, 9));
            supportService.create(f);
        }));
    }

    // ---------------- 种植数据 ----------------

    @GetMapping("/planting/template")
    public void plantingTemplate(HttpServletResponse resp) {
        List<String> head = Arrays.asList("地块编码*", "种植年度*", "季节(春/夏/秋)", "作物*", "品种",
                "面积(亩)", "产量(kg/亩)", "数据来源(遥感/统计/农户/巡查)");
        List<List<Object>> sample = new ArrayList<>();
        sample.add(Arrays.asList("JYB-T001", 2024, "春", "水稻", "吉粳88", 20, 540, "农户"));
        ExcelUtil.write(resp, "种植数据导入模板", head, sample);
    }

    @PostMapping("/planting")
    public Result<ImportResult> importPlanting(@RequestParam("file") MultipartFile file) {
        return Result.ok(process(file, (row, r) -> {
            PlantingRecord rec = new PlantingRecord();
            rec.setParcelCode(str(row, 0));
            rec.setPlantYear(intv(row, 1));
            rec.setSeason(SEASON.getOrDefault(str(row, 2), "SPRING"));
            rec.setCrop(str(row, 3));
            rec.setVariety(str(row, 4));
            rec.setArea(dec(row, 5));
            rec.setYieldPerMu(dec(row, 6));
            rec.setDataSource(SOURCE.getOrDefault(str(row, 7), "STAT"));
            plantingService.create(rec);
        }));
    }

    // ---------------- 耕地质量 ----------------

    @GetMapping("/quality/template")
    public void qualityTemplate(HttpServletResponse resp) {
        List<String> head = Arrays.asList("地块编码*", "评价年度*", "地力等级(1-10)*", "综合得分", "土壤类型",
                "有机质(g/kg)", "全氮(g/kg)", "有效磷(mg/kg)", "速效钾(mg/kg)", "pH", "障碍因素", "适宜作物");
        List<List<Object>> sample = new ArrayList<>();
        sample.add(Arrays.asList("JYB-H001", 2024, 2, 88.5, "黑土", 38.2, 2.15, 28.6, 135, 6.8, "", "水稻、玉米"));
        ExcelUtil.write(resp, "耕地质量导入模板", head, sample);
    }

    @PostMapping("/quality")
    public Result<ImportResult> importQuality(@RequestParam("file") MultipartFile file) {
        return Result.ok(process(file, (row, r) -> {
            LandQuality q = new LandQuality();
            q.setParcelCode(str(row, 0));
            q.setEvalYear(intv(row, 1));
            q.setGrade(intv(row, 2));
            q.setScore(dec(row, 3));
            q.setSoilType(str(row, 4));
            q.setOrganicMatter(dec(row, 5));
            q.setTotalN(dec(row, 6));
            q.setAvailP(dec(row, 7));
            q.setAvailK(dec(row, 8));
            q.setPh(dec(row, 9));
            q.setObstacle(str(row, 10));
            q.setSuitableCrops(str(row, 11));
            qualityService.create(q);
        }));
    }

    // ---------------- 水利设施 ----------------

    @GetMapping("/water/template")
    public void waterTemplate(HttpServletResponse resp) {
        List<String> head = Arrays.asList("设施名称*", "类型*", "所在位置", "经度", "纬度",
                "建设年份", "覆盖面积(亩)", "运行状态", "管护责任人", "联系电话");
        List<List<Object>> sample = new ArrayList<>();
        sample.add(Arrays.asList("示例机井", "机井", "吉林省/延边州/延吉市/太平镇/太平村",
                129.6, 42.94, 2020, 150, "正常", "张三", "13800000000"));
        ExcelUtil.write(resp, "水利设施导入模板", head, sample);
    }

    @PostMapping("/water")
    public Result<ImportResult> importWater(@RequestParam("file") MultipartFile file) {
        return Result.ok(process(file, (row, r) -> {
            WaterFacility f = new WaterFacility();
            f.setName(str(row, 0));
            f.setType(str(row, 1));
            f.setRegionPath(str(row, 2));
            f.setLng(dec(row, 3));
            f.setLat(dec(row, 4));
            f.setBuildYear(intv(row, 5));
            f.setCoverArea(dec(row, 6));
            f.setRunStatus(str(row, 7) == null ? "正常" : str(row, 7));
            f.setManager(str(row, 8));
            f.setPhone(str(row, 9));
            waterService.create(f);
        }));
    }

    // ---------------- 通用处理 ----------------

    @FunctionalInterface
    interface RowHandler {
        void handle(Map<Integer, String> row, ImportResult result);
    }

    private ImportResult process(MultipartFile file, RowHandler handler) {
        if (file == null || file.isEmpty()) throw new ApiException("请上传文件");
        List<Map<Integer, String>> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).sheet().headRowNumber(1).doReadSync();
        } catch (IOException e) {
            throw new ApiException("文件解析失败: " + e.getMessage());
        }
        ImportResult result = new ImportResult();
        result.setTotal(rows.size());
        int idx = 1;
        for (Map<Integer, String> row : rows) {
            idx++;
            if (isBlankRow(row)) {
                result.setTotal(result.getTotal() - 1);
                continue;
            }
            try {
                handler.handle(row, result);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                String msg = e instanceof ApiException ? e.getMessage() : "数据错误";
                result.getErrors().add("第" + idx + "行: " + msg);
            }
        }
        return result;
    }

    private boolean isBlankRow(Map<Integer, String> row) {
        for (String v : row.values()) {
            if (v != null && !v.trim().isEmpty()) return false;
        }
        return true;
    }

    private static String str(Map<Integer, String> row, int i) {
        String v = row.get(i);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private static Integer intv(Map<Integer, String> row, int i) {
        String v = str(row, i);
        if (v == null) return null;
        try { return (int) Double.parseDouble(v); } catch (Exception e) { throw new ApiException("数值格式错误: " + v); }
    }

    private static BigDecimal dec(Map<Integer, String> row, int i) {
        String v = str(row, i);
        if (v == null) return null;
        try { return new BigDecimal(v); } catch (Exception e) { throw new ApiException("数值格式错误: " + v); }
    }

    private static LocalDate date(Map<Integer, String> row, int i) {
        String v = str(row, i);
        if (v == null) return null;
        try { return LocalDate.parse(v.replace('/', '-')); }
        catch (Exception e) { throw new ApiException("日期格式应为 yyyy-MM-dd: " + v); }
    }

    private static Map<String, String> mapOf(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
