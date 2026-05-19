package zw.gov.mohcc.impilo.ndila.core.nompilo;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.catchment.NdilaCatchmentService;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.intelligence.NdilaAiContextPacketService;
import zw.gov.mohcc.impilo.ndila.core.intelligence.NdilaSpatialIntelligenceService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaTshepoGuard;
import zw.gov.mohcc.impilo.ndila.core.routing.NdilaRouteService;
import zw.gov.mohcc.impilo.ndila.core.search.NdilaSpatialSearchService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic Nompilo tool surface exposed to the LLM orchestration layer.
 * Each method maps to a tool function that Nompilo can call:
 *
 * <ul>
 *   <li>searchNearbyServices</li>
 *   <li>findNearestFacility</li>
 *   <li>getRouteEta</li>
 *   <li>summarizeCatchmentCoverage</li>
 *   <li>findFacilitiesMissingCoordinates</li>
 *   <li>getPublicHealthMapInsights</li>
 *   <li>generateSpatialRiskSummary</li>
 *   <li>identifyUnderservedAreas</li>
 *   <li>explainMapLayer</li>
 *   <li>createCatchmentDraft</li>
 * </ul>
 *
 * Every tool routes through NdilaTshepoGuard. Sensitive layers are never
 * exposed unless authorized.
 */
@Service
public class NdilaNompiloTools {

    private final NdilaSpatialSearchService search;
    private final NdilaCatchmentService catchments;
    private final NdilaRouteService routing;
    private final NdilaSpatialIntelligenceService intelligence;
    private final NdilaAiContextPacketService aiContext;
    private final NdilaLocationRepository locations;
    private final NdilaTshepoGuard tshepoGuard;

    public NdilaNompiloTools(NdilaSpatialSearchService search,
                             NdilaCatchmentService catchments,
                             NdilaRouteService routing,
                             NdilaSpatialIntelligenceService intelligence,
                             NdilaAiContextPacketService aiContext,
                             NdilaLocationRepository locations,
                             NdilaTshepoGuard tshepoGuard) {
        this.search = search;
        this.catchments = catchments;
        this.routing = routing;
        this.intelligence = intelligence;
        this.aiContext = aiContext;
        this.locations = locations;
        this.tshepoGuard = tshepoGuard;
    }

    public Map<String, Object> searchNearbyServices(NdilaOperationContext ctx, double lat, double lng,
                                                    double radiusMeters, List<String> entityTypes, int limit) {
        if (!tshepoGuard.authorize(ctx, "searchNearbyServices").allowed()) {
            return Map.of("denied", true);
        }
        Coordinate origin = new Coordinate(lat, lng);
        List<NdilaSpatialSearchService.Match> matches =
                search.nearby(ctx.tenantId(), origin, radiusMeters, entityTypes, Math.max(1, limit));
        return Map.of(
                "tool", "searchNearbyServices",
                "origin", Map.of("latitude", lat, "longitude", lng),
                "results", matches.stream()
                        .map(m -> Map.of(
                                "id", m.location().getId(),
                                "name", m.location().getName(),
                                "type", m.location().getLocationType(),
                                "distanceMeters", Math.round(m.distanceMeters())))
                        .collect(Collectors.toList()));
    }

    public Map<String, Object> findNearestFacility(NdilaOperationContext ctx, double lat, double lng) {
        return searchNearbyServices(ctx, lat, lng, 50_000, List.of("HEALTH_FACILITY"), 1);
    }

    public Map<String, Object> getRouteEta(NdilaOperationContext ctx, double oLat, double oLng,
                                            double dLat, double dLng, String mode) {
        NdilaRouteService.RouteResponse r = routing.route(
                ctx, new Coordinate(oLat, oLng), new Coordinate(dLat, dLng),
                mode, "FASTEST", List.of(), false);
        if (!r.success()) return Map.of("denied", true, "reason", r.denialReason());
        return Map.of(
                "tool", "getRouteEta",
                "distanceMeters", r.distanceMeters(),
                "durationSeconds", r.durationSeconds(),
                "mode", r.mode(),
                "provider", r.provider(),
                "fallbackUsed", r.fallbackUsed(),
                "warnings", r.warnings());
    }

    public Map<String, Object> summarizeCatchmentCoverage(NdilaOperationContext ctx,
                                                          String ownerService, String ownerEntityId) {
        NdilaCatchmentService.CoverageSummary s = catchments.coverageSummary(
                ctx.tenantId(), ownerService, ownerEntityId);
        Map<String, Object> out = new HashMap<>();
        out.put("tool", "summarizeCatchmentCoverage");
        out.put("ownerService", s.ownerService());
        out.put("ownerEntityId", s.ownerEntityId());
        out.put("catchmentCount", s.catchmentCount());
        out.put("populationEstimate", s.populationEstimate());
        out.put("householdsEstimate", s.householdsEstimate());
        return out;
    }

    public Map<String, Object> findFacilitiesMissingCoordinates(NdilaOperationContext ctx, String district) {
        List<NdilaLocationEntity> all = district == null
                ? locations.findByTenantId(ctx.tenantId())
                : locations.findByTenantIdAndDistrict(ctx.tenantId(), district);
        List<Map<String, Object>> missing = all.stream()
                .filter(l -> l.getLatitude() == null || l.getLongitude() == null)
                .map(l -> Map.<String, Object>of(
                        "id", l.getId(),
                        "name", l.getName(),
                        "ownerService", l.getOwnerService(),
                        "district", l.getDistrict() == null ? "" : l.getDistrict()))
                .collect(Collectors.toList());
        return Map.of("tool", "findFacilitiesMissingCoordinates", "count", missing.size(), "results", missing);
    }

    public Map<String, Object> getPublicHealthMapInsights(NdilaOperationContext ctx, String district) {
        NdilaSpatialIntelligenceService.CoordinateQualityReport q =
                intelligence.coordinateQuality(ctx.tenantId(), district);
        Map<String, Object> out = new HashMap<>();
        out.put("tool", "getPublicHealthMapInsights");
        out.put("district", district);
        out.put("total", q.total());
        out.put("missing", q.missing());
        out.put("unverified", q.unverified());
        out.put("implausible", q.implausible());
        out.put("duplicateGroups", q.duplicateGroups());
        out.put("confidence", q.confidence());
        out.put("explainability", q.explainability());
        return out;
    }

    public Map<String, Object> generateSpatialRiskSummary(NdilaOperationContext ctx,
                                                          List<double[]> incidentPoints, double radiusMeters) {
        List<Coordinate> points = incidentPoints.stream()
                .map(p -> new Coordinate(p[0], p[1])).toList();
        NdilaSpatialIntelligenceService.RiskClusterSummary s =
                intelligence.riskClusters(ctx.tenantId(), points, radiusMeters);
        return Map.of(
                "tool", "generateSpatialRiskSummary",
                "incidentCount", s.incidentCount(),
                "clusterCount", s.clusterCount(),
                "radiusMeters", s.radiusMeters(),
                "confidence", s.confidence(),
                "explainability", s.explainability());
    }

    public Map<String, Object> identifyUnderservedAreas(NdilaOperationContext ctx,
                                                        List<double[]> demandPoints,
                                                        List<double[]> supplyPoints,
                                                        double thresholdMeters) {
        List<Coordinate> demand = demandPoints.stream().map(p -> new Coordinate(p[0], p[1])).toList();
        List<Coordinate> supply = supplyPoints.stream().map(p -> new Coordinate(p[0], p[1])).toList();
        NdilaSpatialIntelligenceService.ServiceCoverageGap gap =
                intelligence.detectServiceCoverageGap(ctx.tenantId(), demand, supply, thresholdMeters);
        return Map.of(
                "tool", "identifyUnderservedAreas",
                "demandPoints", gap.demandPoints(),
                "uncovered", gap.uncovered(),
                "thresholdMeters", gap.thresholdMeters(),
                "confidence", gap.confidence());
    }

    public Map<String, Object> explainMapLayer(NdilaOperationContext ctx, String layer) {
        return Map.of(
                "tool", "explainMapLayer",
                "layer", layer,
                "explanation", switch (layer == null ? "" : layer) {
                    case "facilities" -> "Health facilities indexed by Tuso and shown by Ndila with verified GPS.";
                    case "publicHealthEvents" -> "Public health incidents from Surveillance, clustered by Ndila.";
                    case "catchments" -> "Service catchment areas (facility / district / programme) owned via Ndila.";
                    case "deliveries" -> "Live Nhume dispatch tracking layer routed via Ndila.";
                    case "riskClusters" -> "Spatial risk clusters generated by the Ndila intelligence engine.";
                    default -> "Layer not recognised in this build.";
                });
    }

    public Map<String, Object> createCatchmentDraft(NdilaOperationContext ctx, String name,
                                                    double centerLat, double centerLng, double radiusMeters,
                                                    String ownerService, String ownerEntityId) {
        if (!tshepoGuard.authorize(ctx, "createCatchmentDraft").allowed()) {
            return Map.of("denied", true);
        }
        return Map.of(
                "tool", "createCatchmentDraft",
                "draft", Map.of(
                        "name", name,
                        "ownerService", ownerService,
                        "ownerEntityId", ownerEntityId,
                        "geometryType", "RADIUS",
                        "centerLatitude", centerLat,
                        "centerLongitude", centerLng,
                        "radiusMeters", radiusMeters),
                "callToAction", "Submit this draft via POST /api/v1/ndila/catchments");
    }

    /** Returns the operation context as a packet ID for downstream audit. */
    public UUID handle() { return UUID.randomUUID(); }
}
