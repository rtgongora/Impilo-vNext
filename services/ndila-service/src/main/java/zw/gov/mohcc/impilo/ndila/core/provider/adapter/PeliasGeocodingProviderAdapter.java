package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.List;

@Component
public class PeliasGeocodingProviderAdapter implements NdilaGeocodingProvider {

    public static final String NAME = "PELIAS";

    private final boolean enabled;
    private final String baseUrl;

    public PeliasGeocodingProviderAdapter(
            @Value("${ndila.providers.pelias.enabled:false}") boolean enabled,
            @Value("${ndila.providers.pelias.base-url:}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return enabled && !baseUrl.isBlank(); }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsGeocoding(true)
                .supportsReverseGeocoding(true)
                .supportsZimbabweCoverage(true)
                .estimatedCostCategory("FREE")
                .dataResidencyNotes("Self-hosted Pelias; sovereignty-friendly.")
                .privacyNotes("Sends user-entered address text; PII minimization at call site is required.")
                .build();
    }

    @Override
    public List<NdilaGeocodingProvider.GeocodeResult> geocode(NdilaGeocodingProvider.GeocodeRequest request) {
        return List.of();
    }
}
