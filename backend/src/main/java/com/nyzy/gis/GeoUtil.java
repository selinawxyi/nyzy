package com.nyzy.gis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyzy.common.ApiException;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.util.ArrayList;
import java.util.List;

/**
 * GIS 几何工具(基于 JTS). 系统内所有坐标均为 WGS-84 经纬度(度), 不做投影,
 * 面积/距离用等距投影近似换算(在延吉所在纬度~43°N、地块尺度下精度足够).
 * GeoJSON 坐标顺序遵循规范: [lng, lat]; JTS Coordinate(x, y) 对应 (lng, lat)。
 */
public final class GeoUtil {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double DEG_TO_M_LAT = 111320.0; // 1° 纬度 ≈ 111.32km
    private static final double EARTH_RADIUS_M = 6371000.0;

    private GeoUtil() {}

    // ---------------- 解析 ----------------

    /** 解析 GeoJSON Polygon 字符串(单个多边形, 支持外环+内环洞). */
    public static Polygon parsePolygon(String geoJson) {
        if (geoJson == null || geoJson.trim().isEmpty()) return null;
        try {
            JsonNode root = MAPPER.readTree(geoJson);
            String type = root.path("type").asText();
            JsonNode coords = root.path("coordinates");
            if (!"Polygon".equals(type) || !coords.isArray()) {
                throw new ApiException("仅支持 GeoJSON Polygon 几何");
            }
            return polygonFromRings(coords);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("几何解析失败: " + e.getMessage());
        }
    }

    private static Polygon polygonFromRings(JsonNode ringsNode) {
        LinearRing shell = ringFromArray(ringsNode.get(0));
        LinearRing[] holes = null;
        if (ringsNode.size() > 1) {
            holes = new LinearRing[ringsNode.size() - 1];
            for (int i = 1; i < ringsNode.size(); i++) holes[i - 1] = ringFromArray(ringsNode.get(i));
        }
        return FACTORY.createPolygon(shell, holes);
    }

    private static LinearRing ringFromArray(JsonNode ring) {
        List<Coordinate> coords = new ArrayList<>();
        for (JsonNode pt : ring) coords.add(new Coordinate(pt.get(0).asDouble(), pt.get(1).asDouble()));
        closeRing(coords);
        return FACTORY.createLinearRing(coords.toArray(new Coordinate[0]));
    }

    /** 由前端传来的点数组([[lng,lat],...])构造多边形(自动闭合). */
    public static Polygon polygonFromPoints(List<double[]> lngLatPoints) {
        if (lngLatPoints == null || lngLatPoints.size() < 3) throw new ApiException("多边形至少需要3个点");
        List<Coordinate> coords = new ArrayList<>();
        for (double[] p : lngLatPoints) coords.add(new Coordinate(p[0], p[1]));
        closeRing(coords);
        return FACTORY.createPolygon(coords.toArray(new Coordinate[0]));
    }

    /** 由矩形两角点([lng,lat])构造多边形. */
    public static Polygon rectPolygon(double swLng, double swLat, double neLng, double neLat) {
        Envelope env = new Envelope(swLng, neLng, swLat, neLat);
        return (Polygon) FACTORY.toGeometry(env);
    }

    /** 解析 GeoJSON LineString 字符串. */
    public static LineString parseLineString(String geoJson) {
        if (geoJson == null || geoJson.trim().isEmpty()) throw new ApiException("缺少分割线几何");
        try {
            JsonNode root = MAPPER.readTree(geoJson);
            String type = root.path("type").asText();
            JsonNode coords = root.path("coordinates");
            if (!"LineString".equals(type) || !coords.isArray()) throw new ApiException("仅支持 GeoJSON LineString 几何");
            List<double[]> pts = new ArrayList<>();
            for (JsonNode pt : coords) pts.add(new double[]{pt.get(0).asDouble(), pt.get(1).asDouble()});
            return lineFromPoints(pts);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("分割线解析失败: " + e.getMessage());
        }
    }

    public static LineString lineFromPoints(List<double[]> lngLatPoints) {
        if (lngLatPoints == null || lngLatPoints.size() < 2) throw new ApiException("分割线至少需要2个点");
        Coordinate[] coords = new Coordinate[lngLatPoints.size()];
        for (int i = 0; i < lngLatPoints.size(); i++) coords[i] = new Coordinate(lngLatPoints.get(i)[0], lngLatPoints.get(i)[1]);
        return FACTORY.createLineString(coords);
    }

    public static Point point(double lng, double lat) {
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    private static void closeRing(List<Coordinate> coords) {
        Coordinate first = coords.get(0);
        Coordinate last = coords.get(coords.size() - 1);
        if (!first.equals2D(last)) coords.add(new Coordinate(first.x, first.y));
    }

    // ---------------- 校验 ----------------

    /** 校验几何合法性(自相交/环方向等), 不合法抛出业务异常说明原因. */
    public static void validateOrThrow(Geometry g) {
        IsValidOp op = new IsValidOp(g);
        if (!op.isValid()) {
            throw new ApiException("几何不合法(可能存在自相交): " + op.getValidationError().getMessage());
        }
    }

    // ---------------- 量算(等距投影近似) ----------------

    /** 多边形面积, 单位: 亩. */
    public static double areaMu(Polygon p) {
        double lat = p.getCentroid().getY();
        double mPerDegLng = DEG_TO_M_LAT * Math.cos(Math.toRadians(lat));
        double areaM2 = p.getArea() * DEG_TO_M_LAT * mPerDegLng;
        return areaM2 / 666.67;
    }

    /** 质心 [lng, lat]. */
    public static double[] centroid(Polygon p) {
        Point c = p.getCentroid();
        return new double[]{c.getX(), c.getY()};
    }

    /** 外包矩形 {east, south, west, north} (经纬度文本). */
    public static String[] bounds(Polygon p) {
        Envelope env = p.getEnvelopeInternal();
        return new String[]{
                String.format("E%.6f", env.getMaxX()),
                String.format("N%.6f", env.getMinY()),
                String.format("E%.6f", env.getMinX()),
                String.format("N%.6f", env.getMaxY())
        };
    }

    /** 两点间球面距离(米), 入参均为 [lng,lat]. */
    public static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    // ---------------- 序列化 ----------------

    /** 输出 GeoJSON Polygon 字符串(仅外环, 不含洞). */
    public static String toGeoJson(Polygon p) {
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[[");
        Coordinate[] coords = p.getExteriorRing().getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('[').append(coords[i].x).append(',').append(coords[i].y).append(']');
        }
        sb.append("]]}");
        return sb.toString();
    }

    // ---------------- 拓扑操作 ----------------

    /** 多个多边形合并为一个几何(要求相邻/相交, 否则结果为 MultiPolygon 会被拒绝). */
    public static Polygon unionToSinglePolygon(List<Polygon> polys) {
        Geometry result = FACTORY.createGeometryCollection(polys.toArray(new Geometry[0])).union();
        if (!(result instanceof Polygon)) {
            throw new ApiException("所选地块互不相邻(未共边/相交), 无法合并为单个地块");
        }
        return (Polygon) result;
    }

    /**
     * 用一条贯穿线把多边形分割成两块. 思路: 多边形边界与分割线 noding 后 union,
     * 再用 Polygonizer 重建面, 取落在原多边形内部的面. 要求分割线完整贯穿多边形,
     * 否则不会恰好得到 2 个结果面.
     */
    public static List<Polygon> splitByLine(Polygon polygon, LineString line) {
        Geometry noded = polygon.getBoundary().union(line);
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(noded);
        List<Polygon> candidates = new ArrayList<>();
        for (Object o : polygonizer.getPolygons()) {
            Polygon cand = (Polygon) o;
            Point centroid = cand.getCentroid();
            if (polygon.contains(centroid)) candidates.add(cand);
        }
        if (candidates.size() != 2) {
            throw new ApiException("分割线未能将地块完整分割成两块(需贯穿地块边界), 请重新绘制");
        }
        return candidates;
    }

    public static boolean contains(Polygon polygon, double lng, double lat) {
        return polygon.contains(point(lng, lat));
    }

    public static boolean intersectsWithTolerance(Polygon a, Polygon b, double toleranceMeters) {
        double lat = a.getCentroid().getY();
        double toleranceDeg = toleranceMeters / DEG_TO_M_LAT;
        Geometry buffered = a.buffer(toleranceDeg);
        return buffered.intersects(b);
    }
}
