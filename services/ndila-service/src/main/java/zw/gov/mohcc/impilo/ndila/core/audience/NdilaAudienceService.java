package zw.gov.mohcc.impilo.ndila.core.audience;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.catchment.NdilaCatchmentService;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geofence.NdilaGeofenceService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaTshepoGuard;
import zw.gov.mohcc.impilo.ndila.core.search.NdilaSpatialSearchService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audience service — answers Comms Hub's "send to who?" questions by geofence,
 * catchment, radius or administrative area. Returns audience reference handles
 * (NOT raw PII), and the comms hub executes communication workflows.
 */
@Service
public class NdilaAudienceService {

    private final NdilaGeofenceService geofences;
    private final NdilaCatchmentService catchments;
    private final NdilaSpatialSearchService search;
    private final NdilaTshepoGuard tshepoGuard;

    public NdilaAudienceService(NdilaGeofenceService geofences,
                                NdilaCatchmentService catchments,
                                NdilaSpatialSearchService search,
                                NdilaTshepoGuard tshepoGuard) {
        this.geofences = geofences;
        this.catchments = catchments;
        this.search = search;
        this.tshepoGuard = tshepoGuard;
    }

    public AudienceHandle byGeofence(NdilaOperationContext ctx, UUID geofenceId) {
        if (!tshepoGuard.authorize(ctx, "audience.byGeofence").allowed()) return AudienceHandle.denied();
        return geofences.findById(geofenceId)
                .map(g -> new AudienceHandle("ndila-audience-" + UUID.randomUUID(),
                        "GEOFENCE", Map.of(
                                "geofenceId", g.getId().toString(),
                                "geofenceType", g.getGeofenceType(),
                                "tenantId", ctx.tenantId().toString())))
                .orElse(AudienceHandle.notFound());
    }

    public AudienceHandle byCatchment(NdilaOperationContext ctx, UUID catchmentId) {
        if (!tshepoGuard.authorize(ctx, "audience.byCatchment").allowed()) return AudienceHandle.denied();
        return catchments.findById(catchmentId)
                .map(c -> new AudienceHandle("ndila-audience-" + UUID.randomUUID(),
                        "CATCHMENT", Map.of(
                                "catchmentId", c.getId().toString(),
                                "ownerService", c.getOwnerService(),
                                "tenantId", ctx.tenantId().toString())))
                .orElse(AudienceHandle.notFound());
    }

    public AudienceHandle byRadius(NdilaOperationContext ctx, double lat, double lng, double radiusMeters) {
        if (!tshepoGuard.authorize(ctx, "audience.byRadius").allowed()) return AudienceHandle.denied();
        List<NdilaSpatialSearchService.Match> matches =
                search.nearby(ctx.tenantId(), new Coordinate(lat, lng), radiusMeters, List.of(), 1000);
        int count = matches.size();
        return new AudienceHandle("ndila-audience-" + UUID.randomUUID(),
                "RADIUS", Map.of(
                        "centerLatitude", lat,
                        "centerLongitude", lng,
                        "radiusMeters", radiusMeters,
                        "approximateCount", count));
    }

    public AudienceHandle byAdminArea(NdilaOperationContext ctx, String level, String areaId) {
        if (!tshepoGuard.authorize(ctx, "audience.byAdminArea").allowed()) return AudienceHandle.denied();
        List<NdilaLocationEntity> locations = "DISTRICT".equalsIgnoreCase(level)
                ? search.withinDistrict(ctx.tenantId(), areaId)
                : List.of();
        return new AudienceHandle("ndila-audience-" + UUID.randomUUID(),
                "ADMIN_AREA", Map.of(
                        "level", level,
                        "areaId", areaId,
                        "approximateCount", locations.size()));
    }

    public record AudienceHandle(String audienceId, String selectorType, Map<String, Object> summary) {
        public static AudienceHandle denied() { return new AudienceHandle(null, "DENIED", Map.of()); }
        public static AudienceHandle notFound() { return new AudienceHandle(null, "NOT_FOUND", Map.of()); }
    }
}
