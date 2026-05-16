package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaCoordinateValidator;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;

import java.util.List;
import java.util.UUID;

@RestController
public class NdilaLocationController {

    private final NdilaLocationService locationService;
    private final NdilaCoordinateValidator validator;

    public NdilaLocationController(NdilaLocationService locationService,
                                   NdilaCoordinateValidator validator) {
        this.locationService = locationService;
        this.validator = validator;
    }

    // ── Canonical ───────────────────────────────────────────────────────────
    @PostMapping({"/api/v1/ndila/locations", "/api/v1/maps/locations"})
    public ResponseEntity<LocationResponse> create(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody CreateLocationRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        UUID corr = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        NdilaLocationEntity created = locationService.create(
                new NdilaLocationService.CreateLocation(
                        tenant, req.ownerService(), req.ownerEntityType(), req.ownerEntityId(),
                        req.name(), req.description(), req.locationType(),
                        req.latitude(), req.longitude(), req.altitudeMeters(),
                        req.addressLine1(), req.locality(), req.ward(), req.district(),
                        req.province(), req.country(), req.source(), req.sensitivityLevel(),
                        req.coordinateAccuracyMeters()),
                corr);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping({"/api/v1/ndila/locations/{id}", "/api/v1/maps/locations/{id}"})
    public ResponseEntity<LocationResponse> get(@PathVariable UUID id) {
        return locationService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping({"/api/v1/ndila/locations/{id}", "/api/v1/maps/locations/{id}"})
    public ResponseEntity<LocationResponse> updateCoordinates(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody UpdateLocationCoordinatesRequest req) {
        UUID corr = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        return locationService.updateCoordinates(id, req.latitude(), req.longitude(),
                        req.coordinateAccuracyMeters(), req.source(), corr)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping({
            "/api/v1/ndila/locations/by-owner/{ownerService}/{ownerEntityId}",
            "/api/v1/maps/locations/by-owner/{ownerService}/{ownerEntityId}"
    })
    public ResponseEntity<List<LocationResponse>> byOwner(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String ownerService,
            @PathVariable String ownerEntityId) {
        UUID tenant = UUID.fromString(tenantId);
        List<LocationResponse> out = locationService.findByOwner(tenant, ownerService, ownerEntityId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping({"/api/v1/ndila/validate-location", "/api/v1/maps/validate-location"})
    public ResponseEntity<ValidateLocationResponse> validate(@RequestBody ValidateLocationRequest req) {
        NdilaCoordinateValidator.Result r = validator.validate(
                req.latitude(), req.longitude(), req.accuracyMeters(), req.country());
        return ResponseEntity.ok(new ValidateLocationResponse(
                r.valid(), r.plausible(), r.errors(), r.warnings(), r.confidence()));
    }

    private LocationResponse toResponse(NdilaLocationEntity e) {
        return new LocationResponse(
                e.getId().toString(),
                e.getTenantId().toString(),
                e.getOwnerService(),
                e.getOwnerEntityType(),
                e.getOwnerEntityId(),
                e.getName(),
                e.getLocationType(),
                e.getLatitude(),
                e.getLongitude(),
                e.getDistrict(),
                e.getProvince(),
                e.getCountry(),
                e.getVerificationStatus(),
                e.getSensitivityLevel(),
                e.isActive());
    }
}
