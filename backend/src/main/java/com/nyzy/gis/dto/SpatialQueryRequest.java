package com.nyzy.gis.dto;

import lombok.Data;

import java.util.List;

@Data
public class SpatialQueryRequest {
    /** water / support / parcel / planting / quality / abandon */
    private String targetType;
    private Shape shape;

    @Data
    public static class Shape {
        /** rect / circle / polygon */
        private String type;
        /** rect: [[lng,lat]西南角, [lng,lat]东北角] */
        private List<List<Double>> bounds;
        /** circle: [lng,lat] */
        private List<Double> center;
        /** circle: 半径(米) */
        private Double radius;
        /** polygon: [[lng,lat], ...] */
        private List<List<Double>> points;
    }
}
