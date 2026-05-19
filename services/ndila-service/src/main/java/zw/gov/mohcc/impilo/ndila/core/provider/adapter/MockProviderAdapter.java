package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoMath;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaIsochroneProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic Mock provider — Ndila's production-quality default for
 * development, tests and CI/CD. The Mock provider never makes network calls,
 * never reaches a live external map service, and produces deterministic
 * fixtures so the platform can always serve geocoding, routing, distance
 * matrix, isochrone and tile config requests with safe fallback values.
 *
 * <p>The Mock provider is always {@link #enabled() enabled} and intentionally
 * supports every operation type. Provider policy can still prefer other
 * providers when configured.
 */
@Component
public class MockProviderAdapter implements
        NdilaGeocodingProvider,
        NdilaRoutingProvider,
        NdilaTileProvider,
        NdilaIsochroneProvider {

    public static final String NAME = "MOCK";

    private static final double MOCK_HARARE_LAT = -17.8292;
    private static final double MOCK_HARARE_LNG = 31.0522;

    @Override
    public String providerName() { return NAME; }

    @Override
    public boolean enabled() { return true; }

    @Override
    public boolean productionSafe() { return true; }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsGeocoding(true)
                .supportsReverseGeocoding(true)
                .supportsRouting(true)
                .supportsDistanceMatrix(true)
                .supportsIsochrones(true)
                .supportsTraffic(false)
                .supportsOffline(true)
                .supportsTiles(true)
                .supportsVectorTiles(false)
                .supportsBoundaries(true)
                .supportsZimbabweCoverage(true)
                .supportsCommercialSla(false)
                .supportsSensitiveWorkflows(true)
                .estimatedCostCategory("FREE")
                .dataResidencyNotes("In-process, never sent off-host.")
                .privacyNotes("Deterministic mock — safe for sensitive workflows.")
                .build();
    }

    // ── Geocoding ────────────────────────────────────────────────────────────

    @Override
    public List<NdilaGeocodingProvider.GeocodeResult> geocode(NdilaGeocodingProvider.GeocodeRequest request) {
        String query = Optional.ofNullable(request.query()).orElse("").trim();
        if (query.isEmpty()) return List.of();
        long h = stableHash(query);
        double latJitter = ((h % 200) - 100) / 10_000.0;
        double lngJitter = (((h / 200) % 200) - 100) / 10_000.0;
        return List.of(new NdilaGeocodingProvider.GeocodeResult(
                query,
                new Coordinate(MOCK_HARARE_LAT + latJitter, MOCK_HARARE_LNG + lngJitter),
                Optional.ofNullable(request.country()).orElse("ZWE"),
                request.district() != null ? request.district() : "Harare",
                request.district() != null ? request.district() : "Harare",
                request.province() != null ? request.province() : "Harare",
                Optional.ofNullable(request.country()).orElse("ZWE"),
                0.82,
                NAME,
                "mock:place:" + Long.toHexString(h),
                "SYSTEM_VALIDATED"));
    }

    @Override
    public Optional<NdilaGeocodingProvider.GeocodeResult> reverseGeocode(Coordinate coordinate, String country) {
        return Optional.of(new NdilaGeocodingProvider.GeocodeResult(
                String.format(Locale.ROOT, "Approx. (%.5f, %.5f)", coordinate.latitude(), coordinate.longitude()),
                coordinate,
                "Approximate location",
                "Locality (mock)",
                "Harare",
                "Harare",
                Optional.ofNullable(country).orElse("ZWE"),
                0.75,
                NAME,
                "mock:rev:" + Long.toHexString(stableHash(coordinate.toString())),
                "SYSTEM_VALIDATED"));
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    @Override
    public NdilaRoutingProvider.RouteResult route(NdilaRoutingProvider.RouteRequest request) {
        double straightMeters = GeoMath.haversineMeters(request.origin(), request.destination());
        double pad = Math.max(1.15, modeRoutingPad(request.mode()));
        double distanceMeters = straightMeters * pad;
        double avgSpeed = modeAverageMps(request.mode());
        double durationSeconds = avgSpeed > 0 ? distanceMeters / avgSpeed : 0;
        List<String> warnings = modeWarnings(request.mode());
        String polyline = request.includeGeometry()
                ? String.format(Locale.ROOT, "mock-poly:%.4f,%.4f->%.4f,%.4f",
                request.origin().latitude(), request.origin().longitude(),
                request.destination().latitude(), request.destination().longitude())
                : null;
        return new NdilaRoutingProvider.RouteResult(
                distanceMeters,
                durationSeconds,
                request.mode(),
                NAME,
                "DEFAULT",
                false,
                false,
                polyline,
                warnings,
                0.6);
    }

    // ── Tiles ────────────────────────────────────────────────────────────────

    @Override
    public NdilaTileProvider.TileConfig tileConfig(NdilaTileProvider.TileConfigRequest request) {
        return new NdilaTileProvider.TileConfig(
                NAME,
                "mock://tiles/{z}/{x}/{y}.png",
                "Ndila Mock Tiles",
                19,
                true,
                "mock-token-ref");
    }

    // ── Isochrone ────────────────────────────────────────────────────────────

    @Override
    public NdilaIsochroneProvider.IsochroneResult isochrone(NdilaIsochroneProvider.IsochroneRequest request) {
        double speed = modeAverageMps(request.mode());
        double radius = Math.max(speed * request.minutes() * 60.0 * 0.65, 250.0);
        Coordinate o = request.origin();
        // simple square approximation as a GeoJSON polygon
        double dLat = radius / 111_000.0;
        double dLng = radius / (111_000.0 * Math.cos(Math.toRadians(o.latitude())) + 1e-9);
        String geometryJson = String.format(Locale.ROOT,
                "{\"type\":\"Polygon\",\"coordinates\":[[[%f,%f],[%f,%f],[%f,%f],[%f,%f],[%f,%f]]]}",
                o.longitude() - dLng, o.latitude() - dLat,
                o.longitude() + dLng, o.latitude() - dLat,
                o.longitude() + dLng, o.latitude() + dLat,
                o.longitude() - dLng, o.latitude() + dLat,
                o.longitude() - dLng, o.latitude() - dLat);
        return new NdilaIsochroneProvider.IsochroneResult(
                NAME, request.mode(), request.minutes(),
                geometryJson, List.of("Mock isochrone — approximate."), 0.55, false);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static long stableHash(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return Math.abs(h);
    }

    private static double modeAverageMps(String mode) {
        return switch (mode == null ? "CAR" : mode.toUpperCase(Locale.ROOT)) {
            case "WALKING" -> 1.4;
            case "CYCLING" -> 4.2;
            case "MOTORBIKE" -> 11.0;
            case "CAR" -> 14.0;
            case "TRUCK" -> 12.0;
            case "AMBULANCE" -> 16.0;
            case "PUBLIC_TRANSPORT" -> 8.0;
            case "BOAT" -> 5.0;
            case "DRONE" -> 18.0;
            case "ROBOT" -> 1.0;
            case "AUTONOMOUS_VEHICLE" -> 14.0;
            default -> 12.0;
        };
    }

    private static double modeRoutingPad(String mode) {
        return switch (mode == null ? "CAR" : mode.toUpperCase(Locale.ROOT)) {
            case "DRONE", "ROBOT", "AUTONOMOUS_VEHICLE" -> 1.05;
            case "WALKING", "CYCLING" -> 1.20;
            default -> 1.30;
        };
    }

    private static List<String> modeWarnings(String mode) {
        String m = mode == null ? "CAR" : mode.toUpperCase(Locale.ROOT);
        return switch (m) {
            case "DRONE" -> List.of(
                    "Drone routing in this build is a great-circle approximation.",
                    "Air-corridor / no-fly-zone awareness requires a configured provider.");
            case "ROBOT" -> List.of(
                    "Robot routing in this build is a great-circle approximation.",
                    "Indoor/outdoor zone restrictions require a configured robotics provider.");
            case "AUTONOMOUS_VEHICLE" -> List.of(
                    "Autonomous vehicle routing in this build is a placeholder.");
            default -> List.of("Travel time is an estimate and may not reflect current road conditions.");
        };
    }
}
