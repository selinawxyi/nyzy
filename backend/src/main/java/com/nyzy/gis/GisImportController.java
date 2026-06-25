package com.nyzy.gis;

import com.nyzy.common.ApiException;
import com.nyzy.common.Result;
import com.nyzy.imports.ImportResult;
import com.nyzy.land.ParcelService;
import com.nyzy.land.entity.LandParcel;
import org.locationtech.jts.geom.Polygon;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 确权地块 Shapefile / KML 批量导入. 沿用 com.nyzy.imports.ImportController 的
 * ImportResult(成功/失败/逐行错误) 约定, 前端可复用同一套结果展示组件。
 * GML 暂不支持(schema 变体太多, 没有真实样例锚定解析规则, 等遇到真实文件再做)。
 */
@RestController
@RequestMapping("/api/import")
public class GisImportController {

    private static final Map<String, String[]> ALIASES = new LinkedHashMap<>();
    static {
        ALIASES.put("parcelCode", new String[]{"地块编码", "编码", "DKBM", "BM", "parcelcode", "code"});
        ALIASES.put("name", new String[]{"地块名称", "名称", "DKMC", "MC", "name"});
        ALIASES.put("regionPath", new String[]{"坐落位置", "区划", "区划路径", "regionpath", "region"});
        ALIASES.put("contractorName", new String[]{"承包方姓名", "承包方", "姓名", "CBNAME", "contractorname"});
        ALIASES.put("contractorCode", new String[]{"承包方编码", "承包编码", "CBBM", "contractorcode"});
        ALIASES.put("area", new String[]{"确权面积", "面积", "MJ", "area"});
        ALIASES.put("landUse", new String[]{"地块用途", "用途", "YT", "landuse"});
    }

    private final ParcelService parcelService;

    public GisImportController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/parcel-shapefile/help")
    public Result<Map<String, Object>> shapefileHelp() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("desc", "上传 zip 包, 内含同名 .shp + .dbf (+.shx, 可选)。.dbf 字段名按下表别名匹配, 中文字段建议 GBK 编码。");
        m.put("fieldAliases", ALIASES);
        return Result.ok(m);
    }

    @GetMapping("/parcel-kml/help")
    public Result<Map<String, Object>> kmlHelp() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("desc", "上传 .kml 文件, 每个 Placemark 对应一个地块面(Polygon)。属性放在 ExtendedData/Data[name=字段名]/value 中, 字段名按下表别名匹配。");
        m.put("fieldAliases", ALIASES);
        return Result.ok(m);
    }

    @PostMapping("/parcel-shapefile")
    public Result<ImportResult> importShapefile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException("请上传文件");
        Map<String, byte[]> entries = unzip(file);
        byte[] shp = pickBySuffix(entries, ".shp");
        byte[] dbf = pickBySuffix(entries, ".dbf");
        if (shp == null) throw new ApiException("zip 包中未找到 .shp 文件");
        if (dbf == null) throw new ApiException("zip 包中未找到 .dbf 文件");

        List<Polygon> polygons = ShpReader.read(new ByteArrayInputStream(shp));
        List<Map<String, String>> attrs = DbfReader.read(new ByteArrayInputStream(dbf));
        int count = Math.min(polygons.size(), attrs.size());

        ImportResult result = new ImportResult();
        result.setTotal(count);
        for (int i = 0; i < count; i++) {
            try {
                createFromGeometry(polygons.get(i), toObjectMap(attrs.get(i)));
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                String msg = e instanceof ApiException ? e.getMessage() : "数据错误";
                result.getErrors().add("第" + (i + 1) + "个图形: " + msg);
            }
        }
        if (polygons.size() != attrs.size()) {
            result.getErrors().add("注意: .shp 图形数(" + polygons.size() + ")与 .dbf 记录数(" + attrs.size() + ")不一致, 仅按较小值匹配处理");
        }
        return Result.ok(result);
    }

    @PostMapping("/parcel-kml")
    public Result<ImportResult> importKml(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException("请上传文件");
        List<Map<String, Object>> rows;
        try {
            rows = KmlReader.read(file.getInputStream());
        } catch (IOException e) {
            throw new ApiException("文件读取失败: " + e.getMessage());
        }
        ImportResult result = new ImportResult();
        result.setTotal(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, Object> row = rows.get(i);
                Polygon poly = GeoUtil.parsePolygon((String) row.get("boundary"));
                createFromGeometry(poly, row);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                String msg = e instanceof ApiException ? e.getMessage() : "数据错误";
                result.getErrors().add("第" + (i + 1) + "个面: " + msg);
            }
        }
        return Result.ok(result);
    }

    // ---------------- 通用: 几何 + 属性 -> 落库 ----------------

    private void createFromGeometry(Polygon poly, Map<String, Object> attrs) {
        GeoUtil.validateOrThrow(poly);
        LandParcel p = new LandParcel();
        p.setParcelCode(field(attrs, "parcelCode"));
        p.setName(field(attrs, "name"));
        if (p.getParcelCode() == null) throw new ApiException("缺少地块编码字段(请检查别名映射, 见 /help 接口)");
        if (p.getName() == null) p.setName(p.getParcelCode());
        p.setRegionPath(field(attrs, "regionPath"));
        p.setContractorName(field(attrs, "contractorName"));
        p.setContractorCode(field(attrs, "contractorCode"));
        p.setLandUse(field(attrs, "landUse"));

        String areaStr = field(attrs, "area");
        BigDecimal area = areaStr != null ? parseDecimal(areaStr) : BigDecimal.valueOf(GeoUtil.areaMu(poly));
        p.setArea(area.setScale(2, java.math.RoundingMode.HALF_UP));

        double[] centroid = GeoUtil.centroid(poly);
        p.setCenterLng(BigDecimal.valueOf(centroid[0]));
        p.setCenterLat(BigDecimal.valueOf(centroid[1]));
        String[] bounds = GeoUtil.bounds(poly);
        p.setBoundEast(bounds[0]);
        p.setBoundSouth(bounds[1]);
        p.setBoundWest(bounds[2]);
        p.setBoundNorth(bounds[3]);
        p.setBoundary(GeoUtil.toGeoJson(poly));

        parcelService.create(p);
    }

    private String field(Map<String, Object> attrs, String key) {
        for (String alias : ALIASES.get(key)) {
            for (Map.Entry<String, Object> e : attrs.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(alias)) {
                    Object v = e.getValue();
                    String s = v == null ? null : v.toString().trim();
                    if (s != null && !s.isEmpty()) return s;
                }
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String v) {
        try { return new BigDecimal(v.trim()); } catch (Exception e) { throw new ApiException("数值格式错误: " + v); }
    }

    private Map<String, Object> toObjectMap(Map<String, String> m) {
        return new LinkedHashMap<>(m);
    }

    private Map<String, byte[]> unzip(MultipartFile file) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) != -1) bos.write(buf, 0, n);
                entries.put(entry.getName(), bos.toByteArray());
            }
        } catch (IOException e) {
            throw new ApiException("zip 文件解析失败: " + e.getMessage());
        }
        return entries;
    }

    private byte[] pickBySuffix(Map<String, byte[]> entries, String suffix) {
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().toLowerCase().endsWith(suffix)) return e.getValue();
        }
        return null;
    }
}
