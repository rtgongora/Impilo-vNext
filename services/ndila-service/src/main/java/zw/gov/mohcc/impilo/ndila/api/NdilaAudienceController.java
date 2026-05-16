package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.audience.NdilaAudienceService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;

import java.util.UUID;

@RestController
public class NdilaAudienceController {

    private final NdilaAudienceService audienceService;

    public NdilaAudienceController(NdilaAudienceService audienceService) {
        this.audienceService = audienceService;
    }

    @PostMapping({"/api/v1/ndila/audience/by-geofence", "/api/v1/maps/audience/by-geofence"})
    public ResponseEntity<AudienceHandleResponse> byGeofence(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody AudienceByGeofenceRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType, purpose, "AUDIENCE_BY_GEOFENCE",
                "ndila-api", "INTERNAL", correlationId, "development", "ZW");
        var h = audienceService.byGeofence(ctx, UUID.fromString(req.geofenceId()));
        return ResponseEntity.ok(new AudienceHandleResponse(h.audienceId(), h.selectorType(), h.summary()));
    }

    @PostMapping({"/api/v1/ndila/audience/by-catchment", "/api/v1/maps/audience/by-catchment"})
    public ResponseEntity<AudienceHandleResponse> byCatchment(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody AudienceByCatchmentRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, null, null, "OPERATIONS_MANAGEMENT", "AUDIENCE_BY_CATCHMENT",
                "ndila-api", "INTERNAL", correlationId, "development", "ZW");
        var h = audienceService.byCatchment(ctx, UUID.fromString(req.catchmentId()));
        return ResponseEntity.ok(new AudienceHandleResponse(h.audienceId(), h.selectorType(), h.summary()));
    }

    @PostMapping({"/api/v1/ndila/audience/by-radius", "/api/v1/maps/audience/by-radius"})
    public ResponseEntity<AudienceHandleResponse> byRadius(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody AudienceByRadiusRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, null, null, "OPERATIONS_MANAGEMENT", "AUDIENCE_BY_RADIUS",
                "ndila-api", "INTERNAL", correlationId, "development", "ZW");
        var h = audienceService.byRadius(ctx,
                req.center().latitude().doubleValue(),
                req.center().longitude().doubleValue(),
                req.radiusMeters());
        return ResponseEntity.ok(new AudienceHandleResponse(h.audienceId(), h.selectorType(), h.summary()));
    }

    @PostMapping({"/api/v1/ndila/audience/by-admin-area", "/api/v1/maps/audience/by-admin-area"})
    public ResponseEntity<AudienceHandleResponse> byAdminArea(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody AudienceByAdminAreaRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, null, null, "OPERATIONS_MANAGEMENT", "AUDIENCE_BY_ADMIN_AREA",
                "ndila-api", "INTERNAL", correlationId, "development", "ZW");
        var h = audienceService.byAdminArea(ctx, req.level(), req.areaId());
        return ResponseEntity.ok(new AudienceHandleResponse(h.audienceId(), h.selectorType(), h.summary()));
    }
}
