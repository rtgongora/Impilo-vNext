package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.List;

/**
 * Commercial Mapbox adapter — optional, credentialed, audited. Disabled by
 * default. Provider policy never selects Mapbox unless it is explicitly
 * enabled in configuration and authorised by Tshepo for the use case.
 */
@Component
public class MapboxProviderAdapter implements
        NdilaGeocodingProvider, NdilaRoutingProvider, NdilaTileProvider {

    public static final String NAME = "MAPBOX";

    private final boolean enabled;
    private final String accessToken;

    public MapboxProviderAdapter(
            @Value("${ndila.providers.mapbox.enabled:false}") boolean enabled,
            @Value("${ndila.providers.mapbox.access-token:}") String accessToken) {
        this.enabled = enabled;
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return enabled && !accessToken.isBlank(); }
    @Override public boolean productionSafe() { return true; }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsGeocoding(true)
                .supportsReverseGeocoding(true)
                .supportsRouting(true)
                .supportsDistanceMatrix(true)
                .supportsIsochrones(true)
                .supportsTraffic(true)
                .supportsTiles(true)
                .supportsVectorTiles(true)
                .supportsZimbabweCoverage(true)
                .supportsCommercialSla(true)
                .estimatedCostCategory("MEDIUM")
                .dataResidencyNotes("Commercial provider; data residency per Mapbox policy.")
                .privacyNotes("Avoid sending PII; coordinates only; sensitive workflows must minimize payload.")
                .build();
    }

    @Override
    public List<NdilaGeocodingProvider.GeocodeResult> geocode(NdilaGeocodingProvider.GeocodeRequest r) {
        return List.of();
    }

    @Override
    public NdilaRoutingProvider.RouteResult route(NdilaRoutingProvider.RouteRequest r) {
        return new NdilaRoutingProvider.RouteResult(
                0, 0, r.mode(), NAME, "PROVIDER_NOT_CONFIGURED",
                true, false, null,
                List.of("Mapbox is not configured in this deployment. Falling back via policy."),
                0.0);
    }

    @Override
    public NdilaTileProvider.TileConfig tileConfig(NdilaTileProvider.TileConfigRequest r) {
        return new NdilaTileProvider.TileConfig(
                NAME,
                "https://api.mapbox.com/styles/v1/mapbox/streets-v11/tiles/{z}/{x}/{y}",
                "© Mapbox © OpenStreetMap",
                22, false, "mapbox-token-ref");
    }
}
