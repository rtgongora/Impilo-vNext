package zw.gov.mohcc.impilo.nhume.integration.ndila;

import java.util.Optional;

/**
 * Ndila — Impilo shared maps & geospatial intelligence service.
 *
 * <p>Nhume must consume map, routing, geofencing, location intelligence,
 * ETA and telemetry capabilities through Ndila rather than hard-coding a single
 * map provider. The Ndila abstraction allows Mapbox, Google Maps, OpenStreetMap,
 * Azure Maps or future providers to be swapped without touching Nhume code.
 *
 * <p>The default {@code SimulatedNdilaClient} provides realistic offline
 * behaviour for development, tests and demos. Configure
 * {@code impilo.nhume.ndila.base-url} to point at a real Ndila deployment.
 */
public interface NdilaClient {

    /** Map provider implementation hint surfaced to UI clients. */
    String mapProvider();

    /**
     * Compute a great-circle distance between two coordinates (metres).
     * Real Ndila would return a route-aware distance.
     */
    double distanceMetres(double lat1, double lng1, double lat2, double lng2);

    /**
     * Compute a coarse ETA in seconds between two coordinates assuming the
     * given travel mode. Real Ndila replaces this with provider routing.
     */
    long etaSeconds(double lat1, double lng1, double lat2, double lng2, String mode);

    /** Returns true if the supplied point falls within a circular zone. */
    default boolean withinCircle(double lat, double lng,
                                 double centreLat, double centreLng, int radiusMetres) {
        return distanceMetres(lat, lng, centreLat, centreLng) <= radiusMetres;
    }

    /** Best-effort reverse geocode (display-friendly label). */
    Optional<String> reverseGeocode(double lat, double lng);

    /** Encode a coordinate pair → polyline (Google polyline / similar). */
    String encodePolyline(double[][] coordinates);

    /** Returns a Ndila tile token / configuration blob safe to expose to clients. */
    NdilaTileToken issueTileToken();

    record NdilaTileToken(String provider, String token, String styleUrl, long expiresAtEpochMs) {}
}
