package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geofence.NdilaGeofenceService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeofenceEntity;

import java.util.List;
import java.util.UUID;

@RestController
public class NdilaGeofenceController {

    private final NdilaGeofenceService geofenceService;

    public NdilaGeofenceController(NdilaGeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    @PostMapping({"/api/v1/ndila/geofences", "/api/v1/maps/geofences"})
    public ResponseEntity<GeofenceResponse> create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody CreateGeofenceRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        UUID corr = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        NdilaGeofenceEntity g = geofenceService.create(tenant, req.name(), req.geofenceType(),
                req.geometryJson(), req.ownerService(), req.ownerEntityType(), req.ownerEntityId(),
                req.riskLevel(), req.centerLatitude(), req.centerLongitude(), req.radiusMeters(), corr);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(g));
    }

    @GetMapping({"/api/v1/ndila/geofences/{id}", "/api/v1/maps/geofences/{id}"})
    public ResponseEntity<GeofenceResponse> get(@PathVariable UUID id) {
        return geofenceService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping({"/api/v1/ndila/geofences/check-point", "/api/v1/maps/geofences/check-point"})
    public ResponseEntity<CheckPointResponse> checkPoint(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody CheckPointRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        Coordinate point = new Coordinate(req.point().latitude().doubleValue(),
                req.point().longitude().doubleValue());
        NdilaGeofenceService.CheckResult r = geofenceService.checkPoint(tenant, point);
        List<GeofenceResponse> matches = r.matches().stream().map(this::toResponse).toList();
        return ResponseEntity.ok(new CheckPointResponse(req.point(), matches));
    }

    private GeofenceResponse toResponse(NdilaGeofenceEntity g) {
        return new GeofenceResponse(
                g.getId().toString(), g.getName(), g.getGeofenceType(), g.getRiskLevel(),
                g.getOwnerService(), g.getOwnerEntityId(),
                g.getCenterLatitude(), g.getCenterLongitude(), g.getRadiusMeters(), g.isActive());
    }
}
