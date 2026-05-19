package zw.gov.mohcc.impilo.ndila.core.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal GeoJSON reader Ndila uses to evaluate point-in-geofence and
 * point-in-catchment without requiring PostGIS or a third-party JTS / GeoTools
 * dependency.
 *
 * Supports the subset Ndila accepts in geofence/catchment geometry payloads:
 *  - Point
 *  - LineString
 *  - Polygon
 *  - MultiPolygon
 *  - "Circle" extension: {"type":"Circle","center":[lng,lat],"radius_m":200}
 */
public final class GeoJsonReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeoJsonReader() {}

    public static ParsedGeometry parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String type = root.path("type").asText("");
            return switch (type) {
                case "Polygon" -> ParsedGeometry.polygon(readPolygon(root.path("coordinates")));
                case "MultiPolygon" -> ParsedGeometry.multiPolygon(readMultiPolygon(root.path("coordinates")));
                case "Circle" -> {
                    JsonNode center = root.path("center");
                    double lng = center.path(0).asDouble();
                    double lat = center.path(1).asDouble();
                    double radius = root.path("radius_m").asDouble();
                    yield ParsedGeometry.circle(new Coordinate(lat, lng), radius);
                }
                case "Point" -> {
                    JsonNode coords = root.path("coordinates");
                    yield ParsedGeometry.point(new Coordinate(coords.path(1).asDouble(), coords.path(0).asDouble()));
                }
                default -> ParsedGeometry.unsupported(type);
            };
        } catch (Exception e) {
            return ParsedGeometry.unsupported("invalid:" + e.getClass().getSimpleName());
        }
    }

    private static List<Coordinate> readPolygon(JsonNode coordinates) {
        // GeoJSON Polygon = [outerRing, [holes...]]; Ndila considers only the outer ring.
        if (coordinates == null || !coordinates.isArray() || coordinates.size() == 0) {
            return List.of();
        }
        return readRing(coordinates.get(0));
    }

    private static List<List<Coordinate>> readMultiPolygon(JsonNode coordinates) {
        List<List<Coordinate>> polys = new ArrayList<>();
        if (coordinates != null && coordinates.isArray()) {
            for (JsonNode poly : coordinates) {
                if (poly.size() > 0) {
                    polys.add(readRing(poly.get(0)));
                }
            }
        }
        return polys;
    }

    private static List<Coordinate> readRing(JsonNode ring) {
        List<Coordinate> out = new ArrayList<>();
        if (ring != null && ring.isArray()) {
            for (JsonNode p : ring) {
                if (p.isArray() && p.size() >= 2) {
                    out.add(new Coordinate(p.get(1).asDouble(), p.get(0).asDouble()));
                }
            }
        }
        return out;
    }

    public sealed interface ParsedGeometry {
        boolean contains(Coordinate point);

        record Polygon(List<Coordinate> ring) implements ParsedGeometry {
            public boolean contains(Coordinate point) { return GeoMath.isPointInRing(point, ring); }
        }
        record MultiPolygon(List<List<Coordinate>> rings) implements ParsedGeometry {
            public boolean contains(Coordinate point) {
                for (List<Coordinate> r : rings) {
                    if (GeoMath.isPointInRing(point, r)) return true;
                }
                return false;
            }
        }
        record Circle(Coordinate center, double radiusMeters) implements ParsedGeometry {
            public boolean contains(Coordinate point) {
                return GeoMath.isWithinRadius(center, radiusMeters, point);
            }
        }
        record PointGeometry(Coordinate point) implements ParsedGeometry {
            public boolean contains(Coordinate p) { return false; }
        }
        record Unsupported(String typeName) implements ParsedGeometry {
            public boolean contains(Coordinate point) { return false; }
        }

        static ParsedGeometry polygon(List<Coordinate> ring) { return new Polygon(ring); }
        static ParsedGeometry multiPolygon(List<List<Coordinate>> rings) { return new MultiPolygon(rings); }
        static ParsedGeometry circle(Coordinate c, double r) { return new Circle(c, r); }
        static ParsedGeometry point(Coordinate p) { return new PointGeometry(p); }
        static ParsedGeometry unsupported(String type) { return new Unsupported(type); }
    }
}
