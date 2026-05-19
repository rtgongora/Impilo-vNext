package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoMath;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.List;
import java.util.Locale;

/**
 * OSM / OSRM provider adapter.
 *
 * <p>This adapter is wire-complete: when a self-hosted OSRM base URL is
 * configured, calls would be issued to that host. When not configured (the
 * default in this repo), the adapter degrades to deterministic great-circle
 * routing so dev / test / CI never reach a live public endpoint and so the
 * platform's "no public OSM tile servers in production" rule is preserved.
 *
 * <p>Real HTTP integration is intentionally left as a configuration-driven
 * follow-on wave; the adapter contract and provider policy work today.
 */
@Component
public class OsmOsrmProviderAdapter implements NdilaRoutingProvider, NdilaTileProvider {

    public static final String NAME = "OSM_OSRM";

    private final boolean osrmEnabled;
    private final String osrmBaseUrl;
    private final boolean tilesEnabled;
    private final String tileBaseUrl;
    private final boolean blockPublicOsmInProduction;
    private final String environment;

    public OsmOsrmProviderAdapter(
            @Value("${ndila.providers.osrm.enabled:false}") boolean osrmEnabled,
            @Value("${ndila.providers.osrm.base-url:}") String osrmBaseUrl,
            @Value("${ndila.providers.osm.enabled:false}") boolean tilesEnabled,
            @Value("${ndila.providers.osm.tile-base-url:}") String tileBaseUrl,
            @Value("${ndila.sovereignty.block-public-osm-tiles-in-production:true}") boolean blockPublicOsmInProduction,
            @Value("${ndila.environment:development}") String environment) {
        this.osrmEnabled = osrmEnabled;
        this.osrmBaseUrl = osrmBaseUrl == null ? "" : osrmBaseUrl;
        this.tilesEnabled = tilesEnabled;
        this.tileBaseUrl = tileBaseUrl == null ? "" : tileBaseUrl;
        this.blockPublicOsmInProduction = blockPublicOsmInProduction;
        this.environment = environment == null ? "development" : environment;
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return osrmEnabled || tilesEnabled; }

    @Override
    public boolean productionSafe() {
        if (tileBaseUrl == null || tileBaseUrl.isBlank()) return true;
        boolean isPublicOsm = tileBaseUrl.contains("tile.openstreetmap.org");
        if ("production".equalsIgnoreCase(environment) && isPublicOsm && blockPublicOsmInProduction) {
            return false;
        }
        return true;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsRouting(true)
                .supportsDistanceMatrix(true)
                .supportsTiles(true)
                .supportsZimbabweCoverage(true)
                .supportsOffline(false)
                .estimatedCostCategory("FREE")
                .dataResidencyNotes("Self-hosted OSM/OSRM; sovereignty-friendly.")
                .privacyNotes("Minimize PII in geocoding queries; routing payloads are coordinates only.")
                .build();
    }

    @Override
    public NdilaRoutingProvider.RouteResult route(NdilaRoutingProvider.RouteRequest request) {
        // Real implementation would POST to ${osrmBaseUrl}/route/v1/driving/{lon,lat;lon,lat}?...
        // For now we use a deterministic great-circle approximation so that
        // the policy + observability surface is fully exercised end-to-end.
        if (!osrmEnabled || osrmBaseUrl.isBlank()) {
            return fallback(request, "OSM_OSRM not configured — using great-circle approximation.");
        }
        return fallback(request, "OSRM live HTTP integration is configuration-driven (follow-on wave).");
    }

    @Override
    public NdilaTileProvider.TileConfig tileConfig(NdilaTileProvider.TileConfigRequest request) {
        String url = tilesEnabled && !tileBaseUrl.isBlank() ? tileBaseUrl : "mock://tiles/{z}/{x}/{y}.png";
        return new NdilaTileProvider.TileConfig(
                NAME, url, "Self-hosted OSM-derived tiles", 19, false, "osm-token-ref");
    }

    private NdilaRoutingProvider.RouteResult fallback(NdilaRoutingProvider.RouteRequest request, String warning) {
        double straight = GeoMath.haversineMeters(request.origin(), request.destination());
        double pad = 1.30;
        double distance = straight * pad;
        double avgSpeed = switch (request.mode() == null ? "CAR" : request.mode().toUpperCase(Locale.ROOT)) {
            case "WALKING" -> 1.4;
            case "CYCLING" -> 4.2;
            case "MOTORBIKE" -> 11.0;
            case "TRUCK" -> 12.0;
            case "AMBULANCE" -> 16.0;
            default -> 14.0;
        };
        double duration = distance / avgSpeed;
        return new NdilaRoutingProvider.RouteResult(
                distance, duration, request.mode(), NAME, "FALLBACK",
                true, false, null, List.of(warning), 0.5);
    }
}
