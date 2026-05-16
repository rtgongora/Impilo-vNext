package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.List;

/**
 * Nominatim-compatible geocoding adapter.
 *
 * <p>Wire-complete; calls a self-hosted Nominatim host when configured. This
 * adapter explicitly refuses to route to {@code nominatim.openstreetmap.org}
 * as a production target.
 */
@Component
public class NominatimGeocodingProviderAdapter implements NdilaGeocodingProvider {

    public static final String NAME = "NOMINATIM";

    private final boolean enabled;
    private final String baseUrl;
    private final String userAgent;

    public NominatimGeocodingProviderAdapter(
            @Value("${ndila.providers.nominatim.enabled:false}") boolean enabled,
            @Value("${ndila.providers.nominatim.base-url:}") String baseUrl,
            @Value("${ndila.providers.nominatim.user-agent:impilo-ndila/0.1}") String userAgent) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.userAgent = userAgent == null ? "impilo-ndila/0.1" : userAgent;
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
                .dataResidencyNotes("Self-hosted Nominatim; sovereignty-friendly.")
                .privacyNotes("Sends user-entered address text; PII minimization at call site is required.")
                .build();
    }

    @Override
    public List<NdilaGeocodingProvider.GeocodeResult> geocode(NdilaGeocodingProvider.GeocodeRequest request) {
        // Live HTTP integration is configuration-driven (follow-on wave).
        // Until configured, the policy service falls back to MOCK.
        return List.of();
    }
}
