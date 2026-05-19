package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.List;

@Component
public class HereMapsProviderAdapter implements NdilaGeocodingProvider, NdilaRoutingProvider {

    public static final String NAME = "HERE_MAPS";

    private final boolean enabled;
    private final String apiKey;

    public HereMapsProviderAdapter(
            @Value("${ndila.providers.here.enabled:false}") boolean enabled,
            @Value("${ndila.providers.here.api-key:}") String apiKey) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return enabled && !apiKey.isBlank(); }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsGeocoding(true)
                .supportsReverseGeocoding(true)
                .supportsRouting(true)
                .supportsDistanceMatrix(true)
                .supportsTraffic(true)
                .supportsZimbabweCoverage(true)
                .supportsCommercialSla(true)
                .estimatedCostCategory("MEDIUM")
                .dataResidencyNotes("Commercial provider; data residency per HERE policy.")
                .privacyNotes("Strictly avoid PII; coordinates only.")
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
                List.of("HERE Maps is not configured in this deployment. Falling back via policy."),
                0.0);
    }
}
