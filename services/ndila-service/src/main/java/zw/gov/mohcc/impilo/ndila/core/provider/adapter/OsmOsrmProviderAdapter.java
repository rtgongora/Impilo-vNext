package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoMath;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OSM / OSRM provider adapter — self-hosted street tiles and OSRM routing only.
 */
@Component
public class OsmOsrmProviderAdapter implements NdilaRoutingProvider, NdilaTileProvider {

    public static final String NAME = "OSM_OSRM";

    private final RestClient restClient;
    private final boolean osrmEnabled;
    private final String osrmBaseUrl;
    private final boolean tilesEnabled;
    private final String tileBaseUrl;
    private final boolean proxyThroughNdila;
    private final String ndilaHttpBasePath;
    private final boolean blockPublicOsmInProduction;
    private final String environment;

    public OsmOsrmProviderAdapter(
            RestClient ndilaOsmRestClient,
            @Value("${ndila.providers.osrm.enabled:false}") boolean osrmEnabled,
            @Value("${ndila.providers.osrm.base-url:}") String osrmBaseUrl,
            @Value("${ndila.providers.osm.enabled:false}") boolean tilesEnabled,
            @Value("${ndila.providers.osm.tile-base-url:}") String tileBaseUrl,
            @Value("${ndila.providers.osm.proxy-through-ndila:true}") boolean proxyThroughNdila,
            @Value("${ndila.providers.preview-sovereign-tiles.http-base-path:/api/v1/ndila}") String ndilaHttpBasePath,
            @Value("${ndila.sovereignty.block-public-osm-tiles-in-production:true}") boolean blockPublicOsmInProduction,
            @Value("${ndila.environment:development}") String environment) {
        this.restClient = ndilaOsmRestClient;
        this.osrmEnabled = osrmEnabled;
        this.osrmBaseUrl = osrmBaseUrl == null ? "" : osrmBaseUrl.trim();
        this.tilesEnabled = tilesEnabled;
        this.tileBaseUrl = tileBaseUrl == null ? "" : tileBaseUrl.trim();
        this.proxyThroughNdila = proxyThroughNdila;
        this.ndilaHttpBasePath = ndilaHttpBasePath == null || ndilaHttpBasePath.isBlank()
                ? "/api/v1/ndila"
                : ndilaHttpBasePath.replaceAll("/+$", "");
        this.blockPublicOsmInProduction = blockPublicOsmInProduction;
        this.environment = environment == null ? "development" : environment;
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return osrmEnabled || (tilesEnabled && !tileBaseUrl.isBlank()); }

    @Override
    public boolean productionSafe() {
        if (tileBaseUrl.isBlank()) return true;
        boolean isPublicOsm = tileBaseUrl.contains("tile.openstreetmap.org");
        return !("production".equalsIgnoreCase(environment) && isPublicOsm && blockPublicOsmInProduction);
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
    public RouteResult route(RouteRequest request) {
        if (!osrmEnabled || osrmBaseUrl.isBlank()) {
            return fallback(request, "OSM_OSRM routing not configured — using great-circle approximation.");
        }
        try {
            String profile = osrmProfile(request.mode());
            String coords = request.origin().longitude() + "," + request.origin().latitude()
                    + ";" + request.destination().longitude() + "," + request.destination().latitude();
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(osrmBaseUrl.replaceAll("/+$", ""))
                    .path("/route/v1/" + profile + "/" + coords)
                    .queryParam("overview", request.includeGeometry() ? "full" : "false")
                    .queryParam("geometries", "polyline")
                    .queryParam("steps", "false")
                    .build(true)
                    .toUri();

            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null || !"Ok".equalsIgnoreCase(body.path("code").asText())) {
                return fallback(request, "OSRM returned no route — using great-circle approximation.");
            }
            JsonNode route = body.path("routes").path(0);
            if (route.isMissingNode()) {
                return fallback(request, "OSRM returned empty routes — using great-circle approximation.");
            }
            double distance = route.path("distance").asDouble(0);
            double duration = route.path("duration").asDouble(0);
            String polyline = request.includeGeometry()
                    ? route.path("geometry").asText(null)
                    : null;
            return new RouteResult(
                    distance,
                    duration,
                    request.mode(),
                    NAME,
                    profile.toUpperCase(Locale.ROOT),
                    false,
                    false,
                    polyline,
                    List.of(),
                    0.92);
        } catch (Exception ex) {
            return fallback(request, "OSRM request failed — using great-circle approximation.");
        }
    }

    @Override
    public TileConfig tileConfig(TileConfigRequest request) {
        if (!tilesEnabled || tileBaseUrl.isBlank()) {
            return new TileConfig(
                    NAME,
                    "mock://tiles/{z}/{x}/{y}.png",
                    "Self-hosted OSM-derived tiles (not configured)",
                    19,
                    false,
                    null);
        }
        String browserTemplate = proxyThroughNdila
                ? ndilaHttpBasePath + "/tiles/{z}/{x}/{y}.png"
                : tileBaseUrl;
        return new TileConfig(
                NAME,
                browserTemplate,
                "© OpenStreetMap contributors — self-hosted via Ndila",
                19,
                false,
                null);
    }

    private static String osrmProfile(String mode) {
        return switch (mode == null ? "CAR" : mode.toUpperCase(Locale.ROOT)) {
            case "WALKING" -> "foot";
            case "CYCLING" -> "bike";
            default -> "driving";
        };
    }

    private RouteResult fallback(RouteRequest request, String warning) {
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
        List<String> warnings = new ArrayList<>();
        warnings.add(warning);
        return new RouteResult(
                distance, duration, request.mode(), NAME, "FALLBACK",
                true, false, null, warnings, 0.5);
    }
}
