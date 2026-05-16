package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.intelligence.NdilaAiContextPacketService;
import zw.gov.mohcc.impilo.ndila.core.intelligence.NdilaSpatialIntelligenceService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;

import java.util.List;
import java.util.UUID;

@RestController
public class NdilaIntelligenceController {

    private final NdilaSpatialIntelligenceService intelligence;
    private final NdilaAiContextPacketService aiContext;

    public NdilaIntelligenceController(NdilaSpatialIntelligenceService intelligence,
                                       NdilaAiContextPacketService aiContext) {
        this.intelligence = intelligence;
        this.aiContext = aiContext;
    }

    @PostMapping({"/api/v1/ndila/intelligence/facility-coordinate-quality",
                  "/api/v1/maps/intelligence/facility-coordinate-quality"})
    public ResponseEntity<CoordinateQualityResponse> coordinateQuality(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody CoordinateQualityRequest req) {
        var q = intelligence.coordinateQuality(UUID.fromString(tenantId), req.district());
        return ResponseEntity.ok(new CoordinateQualityResponse(
                q.total(), q.missing(), q.unverified(), q.implausible(),
                q.duplicateGroups(), q.confidence(), q.explainability()));
    }

    @PostMapping({"/api/v1/ndila/intelligence/risk-clusters",
                  "/api/v1/maps/intelligence/risk-clusters"})
    public ResponseEntity<RiskClustersResponse> riskClusters(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody RiskClustersRequest req) {
        List<Coordinate> incidents = req.incidents().stream()
                .map(c -> new Coordinate(c.latitude().doubleValue(), c.longitude().doubleValue()))
                .toList();
        var s = intelligence.riskClusters(UUID.fromString(tenantId), incidents, req.radiusMeters());
        return ResponseEntity.ok(new RiskClustersResponse(
                s.incidentCount(), s.clusterCount(), s.radiusMeters(),
                s.confidence(), s.explainability()));
    }

    @PostMapping({"/api/v1/ndila/intelligence/ai-context-packet",
                  "/api/v1/maps/intelligence/ai-context-packet"})
    public ResponseEntity<AiContextPacketResponse> aiContextPacket(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody AiContextPacketRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType,
                req.purposeOfUse() == null ? "OPERATIONS_MANAGEMENT" : req.purposeOfUse(),
                "AI_CONTEXT_PACKET", "ndila-api", "INTERNAL", correlationId,
                "development", "ZW");
        NdilaAiContextPacketService.AreaScope area = req.areaScope() == null ? null
                : new NdilaAiContextPacketService.AreaScope(req.areaScope().level(), req.areaScope().id());
        var packet = aiContext.generate(ctx, req.question(), area, req.includeRecommendations());
        return ResponseEntity.ok(new AiContextPacketResponse(
                packet.success(), packet.denialReason(), packet.contextPacketId(), packet.summary(),
                packet.signals(), packet.recommendedActions(), packet.confidence(),
                packet.restrictions(),
                packet.areaScope() == null ? null : new AreaScopeDto(packet.areaScope().level(), packet.areaScope().id()),
                packet.explainability()));
    }
}
