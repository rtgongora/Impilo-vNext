package zw.gov.mohcc.impilo.nhume.integration.ndila;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Local simulated Ndila client used for development, tests and demos.
 *
 * <p>This implementation intentionally never reaches the network. When a
 * real Ndila deployment is available, a separate {@code HttpNdilaClient} bean
 * should override this component using {@code @ConditionalOnProperty}.
 */
@Component
public class SimulatedNdilaClient implements NdilaClient {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    @Value("${impilo.nhume.ndila.provider:simulation}")
    private String provider;

    @Override
    public String mapProvider() {
        return provider == null || provider.isBlank() ? "simulation" : provider.toLowerCase(Locale.ROOT);
    }

    @Override
    public double distanceMetres(double lat1, double lng1, double lat2, double lng2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    @Override
    public long etaSeconds(double lat1, double lng1, double lat2, double lng2, String mode) {
        double metres = distanceMetres(lat1, lng1, lat2, lng2);
        double kphSpeed = switch (mode == null ? "" : mode.toUpperCase(Locale.ROOT)) {
            case "WALKING_COURIER", "WALKING" -> 5.0;
            case "BICYCLE" -> 15.0;
            case "MOTORCYCLE", "ROBOT" -> 35.0;
            case "CAR", "VAN" -> 45.0;
            case "TRUCK" -> 35.0;
            case "AMBULANCE" -> 60.0;
            case "BOAT" -> 25.0;
            case "DRONE" -> 80.0;
            case "AUTONOMOUS_VEHICLE" -> 45.0;
            default -> 30.0;
        };
        double seconds = (metres / 1000.0) / kphSpeed * 3600.0;
        // Add 15% traffic/handling buffer
        return Math.max(60L, (long) (seconds * 1.15));
    }

    @Override
    public Optional<String> reverseGeocode(double lat, double lng) {
        return Optional.of(String.format(Locale.ROOT, "(%.4f, %.4f) — Ndila simulation", lat, lng));
    }

    @Override
    public String encodePolyline(double[][] coordinates) {
        // Lightweight CSV encoding for simulation — real Ndila would return a Google polyline.
        if (coordinates == null || coordinates.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (double[] c : coordinates) {
            if (c.length < 2) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.ROOT, "%.6f,%.6f", c[0], c[1]));
        }
        return sb.toString();
    }

    @Override
    public NdilaTileToken issueTileToken() {
        return new NdilaTileToken(
                mapProvider(),
                "simulation-tile-token",
                "ndila://simulation/style/default",
                System.currentTimeMillis() + 60 * 60 * 1000L);
    }

    /**
     * Marker @Configuration so the @ConditionalOnMissingBean check applies
     * even when the bean is auto-discovered alongside an HTTP impl.
     */
    @Configuration
    static class NdilaSimConfig {
    }
}
