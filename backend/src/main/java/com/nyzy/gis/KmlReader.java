package com.nyzy.gis;

import com.nyzy.common.ApiException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小化 KML 解析器(JDK 自带 DOM, 无第三方依赖), 只解析 Placemark/Polygon/外环,
 * 不支持洞(innerBoundaryIs)/MultiGeometry 等复杂结构(确权地块一般为单一外环).
 * 业务属性(地块编码/承包方等)从 ExtendedData/Data[name=xxx]/value 读取。
 */
public final class KmlReader {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private KmlReader() {}

    /** 每个 Placemark 返回一行: {"name":.., "boundary": GeoJSON字符串, 其余为 ExtendedData 字段}. */
    public static List<Map<String, Object>> read(InputStream in) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(in);
            NodeList placemarks = doc.getElementsByTagName("Placemark");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < placemarks.getLength(); i++) {
                Element pm = (Element) placemarks.item(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", text(pm, "name"));
                readExtendedData(pm, row);
                Polygon poly = readPolygon(pm);
                if (poly == null) continue; // 跳过没有面几何的 Placemark(如纯点标注)
                row.put("boundary", GeoUtil.toGeoJson(poly));
                rows.add(row);
            }
            if (rows.isEmpty()) throw new ApiException("KML 中没有找到面(Polygon)要素");
            return rows;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("KML 解析失败: " + e.getMessage());
        }
    }

    private static Polygon readPolygon(Element placemark) {
        NodeList coordsNodes = placemark.getElementsByTagName("coordinates");
        if (coordsNodes.getLength() == 0) return null;
        String raw = coordsNodes.item(0).getTextContent();
        List<Coordinate> coords = new ArrayList<>();
        for (String tuple : raw.trim().split("\\s+")) {
            if (tuple.isEmpty()) continue;
            String[] xy = tuple.split(",");
            coords.add(new Coordinate(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
        }
        if (coords.size() < 3) return null;
        Coordinate first = coords.get(0), last = coords.get(coords.size() - 1);
        if (!first.equals2D(last)) coords.add(new Coordinate(first.x, first.y));
        LinearRing ring = FACTORY.createLinearRing(coords.toArray(new Coordinate[0]));
        return FACTORY.createPolygon(ring);
    }

    private static void readExtendedData(Element placemark, Map<String, Object> row) {
        NodeList dataNodes = placemark.getElementsByTagName("Data");
        for (int i = 0; i < dataNodes.getLength(); i++) {
            Element data = (Element) dataNodes.item(i);
            String name = data.getAttribute("name");
            if (name == null || name.isEmpty()) continue;
            row.put(name, text(data, "value"));
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        Node n = nodes.item(0);
        String v = n.getTextContent();
        return v == null ? null : v.trim();
    }
}
