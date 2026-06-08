package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

/**
 * Sovereign preview tile provider — serves raster tiles from Ndila itself (no
 * public OSM endpoints). Used in development, staging, and preview sandboxes.
 */
@Component
public class PreviewSovereignTilesProviderAdapter implements NdilaTileProvider {

    public static final String NAME = "PREVIEW_SOVEREIGN";

    private final boolean enabled;
    private final String httpBasePath;

    public PreviewSovereignTilesProviderAdapter(
            @Value("${ndila.providers.preview-sovereign-tiles.enabled:true}") boolean enabled,
            @Value("${ndila.providers.preview-sovereign-tiles.http-base-path:/api/v1/ndila}") String httpBasePath) {
        this.enabled = enabled;
        this.httpBasePath = httpBasePath == null || httpBasePath.isBlank()
                ? "/api/v1/ndila"
                : httpBasePath.replaceAll("/+$", "");
    }

    @Override public String providerName() { return NAME; }
    @Override public boolean enabled() { return enabled; }
    @Override public boolean productionSafe() { return true; }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities.Builder()
                .supportsTiles(true)
                .supportsOffline(true)
                .supportsZimbabweCoverage(true)
                .supportsSensitiveWorkflows(true)
                .estimatedCostCategory("FREE")
                .dataResidencyNotes("Tiles generated in-process by Ndila — no third-party raster calls.")
                .privacyNotes("Preview sovereign raster — safe for restricted workflows in non-production.")
                .build();
    }

    @Override
    public TileConfig tileConfig(TileConfigRequest request) {
        String template = httpBasePath + "/tiles/{z}/{x}/{y}.png";
        return new TileConfig(
                NAME,
                template,
                "Impilo Ndila — sovereign preview tiles",
                17,
                true,
                null);
    }
}
