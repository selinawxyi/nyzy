package com.nyzy.gis;

import com.nyzy.common.ApiException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilTest {

    private Polygon square(double x0, double y0, double size) {
        List<double[]> pts = new ArrayList<>(Arrays.asList(
                new double[]{x0, y0}, new double[]{x0 + size, y0},
                new double[]{x0 + size, y0 + size}, new double[]{x0, y0 + size}));
        return GeoUtil.polygonFromPoints(pts);
    }

    @Test
    void validateOrThrow_selfIntersecting_throws() {
        // 蝴蝶形(自相交)四边形
        List<double[]> pts = Arrays.asList(
                new double[]{0, 0}, new double[]{10, 10}, new double[]{10, 0}, new double[]{0, 10});
        Polygon bowtie = GeoUtil.polygonFromPoints(pts);
        assertThrows(ApiException.class, () -> GeoUtil.validateOrThrow(bowtie));
    }

    @Test
    void validateOrThrow_validSquare_ok() {
        assertDoesNotThrow(() -> GeoUtil.validateOrThrow(square(0, 0, 10)));
    }

    @Test
    void contains_pointInsideSquare_true() {
        Polygon s = square(0, 0, 10);
        assertTrue(GeoUtil.contains(s, 5, 5));
        assertFalse(GeoUtil.contains(s, 50, 50));
    }

    @Test
    void splitByLine_throughLine_returnsTwoPolygons() {
        Polygon s = square(0, 0, 10);
        LineString line = GeoUtil.lineFromPoints(Arrays.asList(new double[]{5, -1}, new double[]{5, 11}));
        List<Polygon> parts = GeoUtil.splitByLine(s, line);
        assertEquals(2, parts.size());
        double total = parts.get(0).getArea() + parts.get(1).getArea();
        assertEquals(s.getArea(), total, 1e-6);
    }

    @Test
    void splitByLine_notCrossing_throws() {
        Polygon s = square(0, 0, 10);
        // 线完全在地块外部
        LineString line = GeoUtil.lineFromPoints(Arrays.asList(new double[]{100, 100}, new double[]{200, 200}));
        assertThrows(ApiException.class, () -> GeoUtil.splitByLine(s, line));
    }

    @Test
    void unionToSinglePolygon_adjacentSquares_ok() {
        Polygon a = square(0, 0, 10);
        Polygon b = square(10, 0, 10); // 共边 x=10
        Polygon merged = GeoUtil.unionToSinglePolygon(Arrays.asList(a, b));
        assertEquals(200.0, merged.getArea(), 1e-6);
    }

    @Test
    void unionToSinglePolygon_disjointSquares_throws() {
        Polygon a = square(0, 0, 10);
        Polygon b = square(100, 100, 10); // 不相邻
        assertThrows(ApiException.class, () -> GeoUtil.unionToSinglePolygon(Arrays.asList(a, b)));
    }

    @Test
    void geoJsonRoundTrip() {
        Polygon s = square(0, 0, 10);
        String json = GeoUtil.toGeoJson(s);
        Polygon parsed = GeoUtil.parsePolygon(json);
        assertEquals(s.getArea(), parsed.getArea(), 1e-9);
    }
}
