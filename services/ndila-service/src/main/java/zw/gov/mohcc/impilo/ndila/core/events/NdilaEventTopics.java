package zw.gov.mohcc.impilo.ndila.core.events;

/**
 * Canonical Ndila topic registry. All Ndila events use the {@code ndila.}
 * prefix and are versioned with {@code .v1} (and beyond) as the contract
 * evolves.
 */
public final class NdilaEventTopics {

    private NdilaEventTopics() {}

    public static final String LOCATION_CREATED            = "ndila.location.created.v1";
    public static final String LOCATION_UPDATED            = "ndila.location.updated.v1";
    public static final String LOCATION_VERIFIED           = "ndila.location.verified.v1";
    public static final String LOCATION_REJECTED           = "ndila.location.rejected.v1";

    public static final String ROUTE_CALCULATED            = "ndila.route.calculated.v1";
    public static final String ROUTE_DEVIATION_DETECTED    = "ndila.route.deviation.detected.v1";

    public static final String PROVIDER_SELECTED           = "ndila.provider.selected.v1";
    public static final String PROVIDER_FAILED             = "ndila.provider.failed.v1";
    public static final String PROVIDER_FALLBACK_USED      = "ndila.provider.fallback.used.v1";
    public static final String PROVIDER_QUOTA_WARNING      = "ndila.provider.quota.warning.v1";
    public static final String PROVIDER_LATENCY_WARNING    = "ndila.provider.latency.warning.v1";
    public static final String PROVIDER_CACHE_HIT          = "ndila.provider.cache.hit.v1";
    public static final String PROVIDER_CACHE_MISS         = "ndila.provider.cache.miss.v1";
    public static final String PROVIDER_POLICY_DENIED      = "ndila.provider.policy.denied.v1";

    public static final String GEOFENCE_CREATED            = "ndila.geofence.created.v1";
    public static final String GEOFENCE_UPDATED            = "ndila.geofence.updated.v1";
    public static final String GEOFENCE_BREACH_DETECTED    = "ndila.geofence.breach.detected.v1";

    public static final String CATCHMENT_CREATED           = "ndila.catchment.created.v1";
    public static final String CATCHMENT_UPDATED           = "ndila.catchment.updated.v1";
    public static final String CATCHMENT_OVERLAP_DETECTED  = "ndila.catchment.overlap.detected.v1";

    public static final String TRACKING_ASSET_CREATED      = "ndila.tracking.asset.created.v1";
    public static final String TRACKING_LOCATION_UPDATED   = "ndila.tracking.location.updated.v1";
    public static final String TRACKING_ASSET_OUTSIDE_ZONE = "ndila.tracking.asset.outside-zone.v1";

    public static final String INTEL_RISK_CLUSTER          = "ndila.intelligence.risk-cluster.detected.v1";
    public static final String INTEL_SERVICE_GAP           = "ndila.intelligence.service-gap.detected.v1";
    public static final String INTEL_COORD_QUALITY         = "ndila.intelligence.coordinate-quality-issue.detected.v1";
    public static final String INTEL_RECOMMENDATION        = "ndila.intelligence.recommendation.generated.v1";
}
