package zw.gov.mohcc.impilo.ndila.core.geo;

/**
 * Lightweight latitude/longitude coordinate used across the Ndila adapter and
 * service layer. Held in double precision for in-process calculations. Persisted
 * data uses BigDecimal at storage boundaries.
 */
public record Coordinate(double latitude, double longitude) {

    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    /** Approximate great-circle (haversine) distance in meters. */
    public double distanceMetersTo(Coordinate other) {
        return GeoMath.haversineMeters(this, other);
    }
}
