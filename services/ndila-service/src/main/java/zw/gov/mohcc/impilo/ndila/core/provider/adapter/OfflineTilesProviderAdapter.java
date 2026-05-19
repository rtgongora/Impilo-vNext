package zw.gov.mohcc.impilo.ndila.core.provider.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

/**
 * Offline-cached tiles adapter (MinIO-backed). Used by mobile clients and
 * low-connectivity field operations.
 */
@Component
public class OfflineTilesProviderAdapter implements NdilaTileProvider {

    public static final String NAME = "OFFLINE_TILES";

    private final boolean enabled;
    private final String bucket;

    public OfflineTilesProviderAdapter(
            @Value("${ndila.providers.offline-tiles.enabled:true}") boolean enabled,
            @Value("${ndila.providers.offline-tiles.bucket:impilo-ndila-tiles}") String bucket) {
        this.enabled = enabled;
        this.bucket = bucket;
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
                .estimatedCostCategory("LOW")
                .dataResidencyNotes("Cached tiles stored in sovereign MinIO bucket.")
                .privacyNotes("No external provider calls when serving from cache.")
                .build();
    }

    @Override
    public TileConfig tileConfig(TileConfigRequest request) {
        return new TileConfig(
                NAME,
                "s3://" + bucket + "/{z}/{x}/{y}.png",
                "Impilo Ndila — offline tile cache",
                17,
                true,
                null);
    }
}
