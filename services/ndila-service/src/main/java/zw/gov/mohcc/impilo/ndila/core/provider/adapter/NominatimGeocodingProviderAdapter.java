package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nominatim- or Photon-compatible geocoding adapter (self-hosted only).
 */
@Component
public class NominatimGeocodingProviderAdapter implements NdilaGeocodingProvider {

    public static final String NAME = "NOMINATIM";

    private final RestClient restClient;
    private final boolean enabled;
    private final String baseUrl;
    private final String userAgent;
    private final String engine;
    private final String bbox;

    public NominatimGeocodingProviderAdapter(
            RestClient ndilaOsmRestClient,
            @Value("${ndila.providers.nominatim.enabled:false}") boolean enabled,
            @Value("${ndila.providers.nominatim.base-url:}") String baseUrl,
            @Value("${ndila.providers.nominatim.user-agent:impilo-ndila/0.1}") String userAgent,
            @Value("${ndila.providers.nominatim.engine:photon}") String engine,
            @Value("${ndila.providers.nominatim.bbox:25.2,-22.5,33.1,-15.6}") String bbox) {
        this.restClient = ndilaOsmRestClient;
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.userAgent = userAgent == null ? "impilo-ndila/0.1" : userAgent;
        this.engine = engine == null ? "photon" : engine.trim().toLowerCase(Locale.ROOT);
        this.bbox = bbox == null ? "" : bbox.trim();
    }

    @Override public String providerName() { return NAME; }

    @Override
    public boolean enabled() {
        if (!enabled || baseUrl.isBlank()) return false;
        return !baseUrl.contains("nominatim.openstreetmap.org");
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsGeocoding(true)
                .supportsReverseGeocoding(true)
                .supportsZimbabweCoverage(true)
                .estimatedCostCategory("FREE")
                .dataResidencyNotes("Self-hosted Nominatim/Photon; sovereignty-friendly.")
                .privacyNotes("Sends user-entered address text; PII minimization at call site is required.")
                .build();
    }

    @Override
    public List<GeocodeResult> geocode(GeocodeRequest request) {
        if (!enabled()) {
            return List.of();
        }
        String query = request.query() == null ? "" : request.query().trim();
        if (query.isBlank()) {
            return List.of();
        }
        try {
            URI uri = buildGeocodeUri(query, request.country());
            JsonNode body = restClient.get()
                    .uri(uri)
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return List.of();
            }
            return parseResults(body, request.country());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private URI buildGeocodeUri(String query, String country) {
        String root = baseUrl.replaceAll("/+$", "");
        if ("photon".equals(engine)) {
            var builder = UriComponentsBuilder.fromHttpUrl(root + "/api")
                    .queryParam("q", query)
                    .queryParam("limit", 8)
                    .queryParam("lang", "en");
            if (!bbox.isBlank()) {
                builder.queryParam("bbox", bbox);
            }
            if (country != null && !country.isBlank()) {
                builder.queryParam("osm_tag", "place:country");
            }
            return builder.build(true).toUri();
        }
        return UriComponentsBuilder.fromHttpUrl(root + "/search")
                .queryParam("q", query)
                .queryParam("format", "json")
                .queryParam("limit", 8)
                .queryParam("addressdetails", 1)
                .queryParam("countrycodes", country == null || country.isBlank() ? "zw" : country.toLowerCase(Locale.ROOT))
                .build(true)
                .toUri();
    }

    private List<GeocodeResult> parseResults(JsonNode body, String defaultCountry) {
        List<GeocodeResult> results = new ArrayList<>();
        if (body.isArray()) {
            for (JsonNode node : body) {
                GeocodeResult parsed = parseNominatimNode(node, defaultCountry);
                if (parsed != null) results.add(parsed);
            }
            return results;
        }
        if (body.has("features")) {
            for (JsonNode feature : body.path("features")) {
                GeocodeResult parsed = parsePhotonFeature(feature, defaultCountry);
                if (parsed != null) results.add(parsed);
            }
        }
        return results;
    }

    private GeocodeResult parseNominatimNode(JsonNode node, String defaultCountry) {
        if (!node.has("lat") || !node.has("lon")) return null;
        double lat = node.path("lat").asDouble();
        double lon = node.path("lon").asDouble();
        JsonNode address = node.path("address");
        return new GeocodeResult(
                node.path("display_name").asText(node.path("name").asText("")),
                new Coordinate(lat, lon),
                node.path("display_name").asText(""),
                address.path("city").asText(address.path("town").asText(address.path("village").asText(""))),
                address.path("state_district").asText(address.path("county").asText("")),
                address.path("state").asText(""),
                address.path("country_code").asText(defaultCountry == null ? "ZW" : defaultCountry).toUpperCase(Locale.ROOT),
                0.85,
                NAME,
                node.path("place_id").asText(""),
                "UNVERIFIED");
    }

    private GeocodeResult parsePhotonFeature(JsonNode feature, String defaultCountry) {
        JsonNode geometry = feature.path("geometry");
        if (!"Point".equalsIgnoreCase(geometry.path("type").asText())) return null;
        JsonNode coords = geometry.path("coordinates");
        if (!coords.isArray() || coords.size() < 2) return null;
        double lon = coords.get(0).asDouble();
        double lat = coords.get(1).asDouble();
        JsonNode props = feature.path("properties");
        String name = props.path("name").asText(props.path("street").asText(""));
        String label = props.path("label").asText(name);
        return new GeocodeResult(
                name.isBlank() ? label : name,
                new Coordinate(lat, lon),
                label,
                props.path("city").asText(props.path("locality").asText("")),
                props.path("district").asText(""),
                props.path("state").asText(""),
                props.path("countrycode").asText(defaultCountry == null ? "ZW" : defaultCountry).toUpperCase(Locale.ROOT),
                0.82,
                NAME,
                props.path("osm_id").asText(""),
                "UNVERIFIED");
    }
}
