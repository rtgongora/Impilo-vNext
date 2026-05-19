package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaProviderAdapter;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

/**
 * Sovereign / Government GIS adapter for Zimbabwe-specific boundary, parcel
 * and administrative datasets. Disabled by default; intended to be wired to
 * a configured government / national GIS endpoint.
 */
@Component
public class ZimbabweGisProviderAdapter implements NdilaProviderAdapter {

    public static final String NAME = "ZW_GIS";

    private final boolean enabled;
    private final String baseUrl;

    public ZimbabweGisProviderAdapter(
            @Value("${ndila.providers.zw-gis.enabled:false}") boolean enabled,
            @Value("${ndila.providers.zw-gis.base-url:}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return enabled && !baseUrl.isBlank(); }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsBoundaries(true)
                .supportsZimbabweCoverage(true)
                .supportsSensitiveWorkflows(true)
                .estimatedCostCategory("FREE")
                .dataResidencyNotes("Sovereign / Government GIS; data stays within national boundary.")
                .privacyNotes("Preferred for sensitive workflows; preserves data sovereignty.")
                .build();
    }
}
