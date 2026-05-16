package zw.gov.mohcc.impilo.ndila.core.geo;

import java.util.List;

/**
 * Internal geospatial math helpers that allow Ndila to run all core spatial
 * operations (point-in-polygon, point-in-circle, haversine distance,
 * bounding-box, centroid) in pure Java without requiring PostGIS.
 *
 * When PostGIS is available Ndila prefers ST_DWithin / ST_Contains for
 * performance, but Java math is the fallback-safe default and is what every
 * test relies on.
 */
public final class GeoMath {

    /** Mean Earth radius (meters). */
    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoMath() {}

    /** Haversine distance between two coordinates in meters. */
    public static double haversineMeters(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double sinDLat = Math.sin(dLat / 2.0);
        double sinDLon = Math.sin(dLon / 2.0);
        double h = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** True if {@code point} is within {@code radiusMeters} of {@code center}. */
    public static boolean isWithinRadius(Coordinate center, double radiusMeters, Coordinate point) {
        return haversineMeters(center, point) <= radiusMeters;
    }

    /**
     * Ray-casting point-in-polygon for a single closed ring of [lng,lat] pairs
     * in GeoJSON order. The ring may or may not be explicitly closed.
     */
    public static boolean isPointInRing(Coordinate point, List<Coordinate> ring) {
        if (ring == null || ring.size() < 3) return false;
        boolean inside = false;
        int n = ring.size();
        double x = point.longitude();
        double y = point.latitude();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring.get(i).longitude(), yi = ring.get(i).latitude();
            double xj = ring.get(j).longitude(), yj = ring.get(j).latitude();
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    /** Bounding box for a collection of coordinates. */
    public static BoundingBox bboxOf(List<Coordinate> coords) {
        if (coords == null || coords.isEmpty()) {
            throw new IllegalArgumentException("empty coordinate list");
        }
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY, maxLng = Double.NEGATIVE_INFINITY;
        for (Coordinate c : coords) {
            if (c.latitude() < minLat) minLat = c.latitude();
            if (c.latitude() > maxLat) maxLat = c.latitude();
            if (c.longitude() < minLng) minLng = c.longitude();
            if (c.longitude() > maxLng) maxLng = c.longitude();
        }
        return new BoundingBox(minLat, minLng, maxLat, maxLng);
    }

    /** Centroid (simple average) of a set of coordinates. */
    public static Coordinate centroidOf(List<Coordinate> coords) {
        if (coords == null || coords.isEmpty()) {
            throw new IllegalArgumentException("empty coordinate list");
        }
        double lat = 0.0, lng = 0.0;
        for (Coordinate c : coords) {
            lat += c.latitude();
            lng += c.longitude();
        }
        return new Coordinate(lat / coords.size(), lng / coords.size());
    }

    public record BoundingBox(double minLat, double minLng, double maxLat, double maxLng) {
        public boolean contains(Coordinate c) {
            return c.latitude() >= minLat && c.latitude() <= maxLat
                    && c.longitude() >= minLng && c.longitude() <= maxLng;
        }
    }
}
