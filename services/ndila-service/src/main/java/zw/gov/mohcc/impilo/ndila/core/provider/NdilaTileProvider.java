package zw.gov.mohcc.impilo.ndila.core.provider;

public interface NdilaTileProvider extends NdilaProviderAdapter {

    /**
     * Returns a per-tenant tile configuration that the BFF can safely surface to
     * the frontend. The actual provider key is never returned; only a stable
     * provider name and a short-lived token reference / URL template.
     */
    TileConfig tileConfig(TileConfigRequest request);

    /**
     * Whether this provider is permitted to be selected in production. The
     * Ndila platform refuses to select {@code production_safe = false}
     * providers when {@link NdilaProviderAdapter#enabled()} is true and the
     * environment is "production".
     */
    boolean productionSafe();

    record TileConfigRequest(String tenantId, String environment, String useCase) {}
    record TileConfig(
            String providerName,
            String tileUrlTemplate,
            String attribution,
            int maxZoom,
            boolean supportsOffline,
            String shortLivedTokenRef
    ) {}
}
