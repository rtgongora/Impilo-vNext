package zw.gov.mohcc.impilo.ndila.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Single-file DTO container for the Ndila API surface. Keeping all request /
 * response shapes together keeps the API contract easy to scan and review.
 */
public final class NdilaDtos {
    private NdilaDtos() {}

    // ── Common ──────────────────────────────────────────────────────────────
    public record CoordinateDto(BigDecimal latitude, BigDecimal longitude) {}

    // ── Geocoding ───────────────────────────────────────────────────────────
    public record GeocodeRequest(
            String query, String country, String province, String district, String purposeOfUse) {}
    public record GeocodeResultDto(
            String name, BigDecimal latitude, BigDecimal longitude, String address,
            String locality, String district, String province, String country,
            double confidence, String provider, String providerPlaceId, String verificationStatus) {}
    public record GeocodeResponse(
            boolean success, String denialReason, List<GeocodeResultDto> results,
            String provider, boolean fallbackUsed, boolean servedFromCache, String policyDecisionId) {}

    public record ReverseGeocodeRequest(BigDecimal latitude, BigDecimal longitude, String country) {}

    // ── Location ────────────────────────────────────────────────────────────
    public record CreateLocationRequest(
            String ownerService, String ownerEntityType, String ownerEntityId,
            String name, String description, String locationType,
            BigDecimal latitude, BigDecimal longitude, BigDecimal altitudeMeters,
            String addressLine1, String locality, String ward, String district,
            String province, String country, String source, String sensitivityLevel,
            BigDecimal coordinateAccuracyMeters) {}
    public record UpdateLocationCoordinatesRequest(
            BigDecimal latitude, BigDecimal longitude, BigDecimal coordinateAccuracyMeters, String source) {}
    public record LocationResponse(
            String id, String tenantId, String ownerService, String ownerEntityType, String ownerEntityId,
            String name, String locationType, BigDecimal latitude, BigDecimal longitude,
            String district, String province, String country, String verificationStatus,
            String sensitivityLevel, boolean active) {}

    public record ValidateLocationRequest(
            BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters, String country) {}
    public record ValidateLocationResponse(
            boolean valid, boolean plausible, List<String> errors, List<String> warnings, double confidence) {}

    // ── Routing ─────────────────────────────────────────────────────────────
    public record RouteRequest(
            CoordinateDto origin, CoordinateDto destination, String mode, String optimizeFor,
            List<String> avoid, boolean includeGeometry) {}
    public record RouteResponse(
            boolean success, String denialReason,
            double distanceMeters, double durationSeconds, String mode,
            String provider, String providerMode, boolean fallbackUsed,
            boolean servedFromCache, String polyline, List<String> warnings,
            double confidence, String policyDecisionId) {}
    public record MultiStopRouteRequest(List<CoordinateDto> waypoints, String mode, String optimizeFor) {}
    public record DistanceMatrixRequest(
            List<CoordinateDto> origins, List<CoordinateDto> destinations, String mode) {}
    public record DistanceMatrixResponse(
            boolean success, String denialReason, double[][] distancesMeters,
            double[][] durationsSeconds, String provider) {}

    // ── Geofences ───────────────────────────────────────────────────────────
    public record CreateGeofenceRequest(
            String name, String description, String geofenceType, String geometryJson,
            String ownerService, String ownerEntityType, String ownerEntityId,
            String riskLevel, BigDecimal centerLatitude, BigDecimal centerLongitude,
            BigDecimal radiusMeters) {}
    public record GeofenceResponse(
            String id, String name, String geofenceType, String riskLevel, String ownerService,
            String ownerEntityId, BigDecimal centerLatitude, BigDecimal centerLongitude,
            BigDecimal radiusMeters, boolean active) {}
    public record CheckPointRequest(CoordinateDto point) {}
    public record CheckPointResponse(CoordinateDto point, List<GeofenceResponse> matches) {}

    // ── Catchments ──────────────────────────────────────────────────────────
    public record CreateCatchmentRequest(
            String name, String description, String ownerService, String ownerEntityType,
            String ownerEntityId, String geometryType, String geometryJson,
            BigDecimal centerLatitude, BigDecimal centerLongitude, BigDecimal radiusMeters,
            Integer populationEstimate) {}
    public record CatchmentResponse(
            String id, String name, String ownerService, String ownerEntityId,
            String geometryType, Integer populationEstimate, Integer householdsEstimate, boolean active) {}
    public record NearestServiceRequest(CoordinateDto point, String locationType, Integer limit) {}
    public record NearestServiceMatch(
            String id, String name, String locationType, double distanceMeters) {}
    public record NearestServiceResponse(CoordinateDto point, List<NearestServiceMatch> matches) {}
    public record CoverageSummaryRequest(String ownerService, String ownerEntityId) {}
    public record CoverageSummaryResponse(
            String ownerService, String ownerEntityId, int catchmentCount,
            int populationEstimate, int householdsEstimate) {}

    // ── Spatial search ──────────────────────────────────────────────────────
    public record NearbyRequest(
            CoordinateDto origin, double radiusMeters, List<String> entityTypes, Integer limit) {}
    public record NearbyMatch(
            String id, String name, String locationType, double distanceMeters,
            BigDecimal latitude, BigDecimal longitude) {}
    public record NearbyResponse(CoordinateDto origin, List<NearbyMatch> matches) {}

    // ── Tracking ────────────────────────────────────────────────────────────
    public record RegisterAssetRequest(
            String assetId, String assetType, String ownerService, String ownerEntityId,
            String label, String sensitivityLevel) {}
    public record TrackingAssetResponse(
            String id, String assetId, String assetType, String ownerService,
            String ownerEntityId, boolean active, String sensitivityLevel) {}
    public record TrackingEventRequest(
            BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
            String eventTimestamp, String telemetrySource) {}
    public record TrackingEventResponse(
            String eventId, String assetId, BigDecimal latitude, BigDecimal longitude,
            String eventTimestamp) {}
    public record NearbyAssetsRequest(CoordinateDto origin, double radiusMeters) {}

    // ── Intelligence ────────────────────────────────────────────────────────
    public record CoordinateQualityRequest(String district) {}
    public record CoordinateQualityResponse(
            int total, int missing, int unverified, int implausible, int duplicateGroups,
            double confidence, List<String> explainability) {}
    public record RiskClustersRequest(List<CoordinateDto> incidents, double radiusMeters) {}
    public record RiskClustersResponse(
            int incidentCount, int clusterCount, double radiusMeters, double confidence,
            List<String> explainability) {}
    public record AiContextPacketRequest(
            String question,
            AreaScopeDto areaScope,
            String purposeOfUse,
            boolean includeRecommendations) {}
    public record AreaScopeDto(String level, String id) {}
    public record AiContextPacketResponse(
            boolean success, String denialReason, String contextPacketId, String summary,
            List<Map<String, Object>> signals, List<String> recommendedActions,
            double confidence, List<String> restrictions, AreaScopeDto areaScope,
            List<String> explainability) {}

    // ── Audience ────────────────────────────────────────────────────────────
    public record AudienceByGeofenceRequest(String geofenceId) {}
    public record AudienceByCatchmentRequest(String catchmentId) {}
    public record AudienceByRadiusRequest(CoordinateDto center, double radiusMeters) {}
    public record AudienceByAdminAreaRequest(String level, String areaId) {}
    public record AudienceHandleResponse(String audienceId, String selectorType, Map<String, Object> summary) {}

    // ── Tile config ─────────────────────────────────────────────────────────
    public record TileConfigResponse(
            String providerName, String tileUrlTemplate, String attribution,
            int maxZoom, boolean supportsOffline) {}
}
