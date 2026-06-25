package com.nyzy.gis;

import com.nyzy.common.ApiException;
import org.locationtech.jts.geom.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 最小化 ESRI Shapefile(.shp) 二进制解析器, 只支持 Polygon/PolygonZ/PolygonM(忽略Z/M值,
 * 仅取平面坐标), 不依赖 GeoTools. 文件结构见 ESRI Shapefile Technical Description。
 */
public final class ShpReader {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final int SHAPE_TYPE_NULL = 0;
    private static final int[] POLYGON_TYPES = {5, 15, 25};

    private ShpReader() {}

    public static List<Polygon> read(InputStream in) {
        try {
            byte[] all = readAll(in);
            ByteBuffer buf = ByteBuffer.wrap(all);
            buf.order(ByteOrder.BIG_ENDIAN);
            int fileCode = buf.getInt(0);
            if (fileCode != 9994) throw new ApiException("不是合法的 Shapefile(.shp) 文件");

            List<Polygon> result = new ArrayList<>();
            int pos = 100; // 文件头固定 100 字节
            while (pos < all.length) {
                buf.order(ByteOrder.BIG_ENDIAN);
                buf.position(pos);
                int recordNumber = buf.getInt();
                int contentLengthWords = buf.getInt();
                int contentBytes = contentLengthWords * 2;
                int contentStart = pos + 8;

                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.position(contentStart);
                int shapeType = buf.getInt();
                if (isPolygonType(shapeType)) {
                    result.add(readPolygon(buf));
                } else if (shapeType != SHAPE_TYPE_NULL) {
                    throw new ApiException("Shapefile 中第" + recordNumber + "条记录不是面(Polygon)类型, 暂不支持");
                }
                pos = contentStart + contentBytes;
            }
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Shapefile 解析失败: " + e.getMessage());
        }
    }

    private static boolean isPolygonType(int t) {
        for (int p : POLYGON_TYPES) if (p == t) return true;
        return false;
    }

    private static Polygon readPolygon(ByteBuffer buf) {
        buf.position(buf.position() + 32); // box: xmin,ymin,xmax,ymax (4 doubles)
        int numParts = buf.getInt();
        int numPoints = buf.getInt();
        int[] parts = new int[numParts];
        for (int i = 0; i < numParts; i++) parts[i] = buf.getInt();
        double[][] points = new double[numPoints][2];
        for (int i = 0; i < numPoints; i++) {
            points[i][0] = buf.getDouble();
            points[i][1] = buf.getDouble();
        }

        List<LinearRing> rings = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            int start = parts[i];
            int end = (i + 1 < numParts) ? parts[i + 1] : numPoints;
            List<Coordinate> coords = new ArrayList<>();
            for (int j = start; j < end; j++) coords.add(new Coordinate(points[j][0], points[j][1]));
            if (coords.size() < 4) continue; // 至少4点(含闭合)才能构成环
            Coordinate first = coords.get(0), last = coords.get(coords.size() - 1);
            if (!first.equals2D(last)) coords.add(new Coordinate(first.x, first.y));
            rings.add(FACTORY.createLinearRing(coords.toArray(new Coordinate[0])));
        }
        if (rings.isEmpty()) throw new ApiException("Shapefile 记录中没有有效的环");

        // 面积最大的环作为外环, 其余作为内环(洞); 多数确权地块只有1个环(无洞)
        int exteriorIdx = 0;
        double maxArea = Math.abs(area(rings.get(0)));
        for (int i = 1; i < rings.size(); i++) {
            double a = Math.abs(area(rings.get(i)));
            if (a > maxArea) { maxArea = a; exteriorIdx = i; }
        }
        LinearRing exterior = rings.get(exteriorIdx);
        List<LinearRing> holes = new ArrayList<>();
        for (int i = 0; i < rings.size(); i++) if (i != exteriorIdx) holes.add(rings.get(i));
        return FACTORY.createPolygon(exterior, holes.toArray(new LinearRing[0]));
    }

    /** 鞋带公式(shoelace), 用于判断环面积/方向. */
    private static double area(LinearRing ring) {
        Coordinate[] c = ring.getCoordinates();
        double sum = 0;
        for (int i = 0; i < c.length - 1; i++) sum += c[i].x * c[i + 1].y - c[i + 1].x * c[i].y;
        return sum / 2.0;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
