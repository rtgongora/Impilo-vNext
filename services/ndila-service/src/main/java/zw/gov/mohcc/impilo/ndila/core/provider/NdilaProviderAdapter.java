package zw.gov.mohcc.impilo.ndila.core.provider;

/**
 * Root provider adapter interface. Every concrete adapter (mock, OSM/OSRM,
 * Nominatim, Pelias, Mapbox, Google Maps, HERE, Azure Maps, Zimbabwe GIS,
 * Offline Tiles, etc.) implements this and exposes capability metadata so the
 * NdilaProviderPolicyService can choose appropriately.
 */
public interface NdilaProviderAdapter {

    /** Canonical provider name (uppercase). */
    String providerName();

    /** Capability metadata advertised by this adapter. */
    ProviderCapabilities capabilities();

    /** Is this adapter currently configured / enabled to take live calls? */
    boolean enabled();
}
