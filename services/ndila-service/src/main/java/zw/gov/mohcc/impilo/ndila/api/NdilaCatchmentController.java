package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.catchment.NdilaCatchmentService;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.domain.NdilaCatchmentAreaEntity;

import java.util.List;
import java.util.UUID;

@RestController
public class NdilaCatchmentController {

    private final NdilaCatchmentService catchmentService;

    public NdilaCatchmentController(NdilaCatchmentService catchmentService) {
        this.catchmentService = catchmentService;
    }

    @PostMapping({"/api/v1/ndila/catchments", "/api/v1/maps/catchments"})
    public ResponseEntity<CatchmentResponse> create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody CreateCatchmentRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        UUID corr = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        NdilaCatchmentAreaEntity c = catchmentService.create(
                tenant, req.name(), req.ownerService(), req.ownerEntityType(), req.ownerEntityId(),
                req.geometryType(), req.geometryJson(), req.centerLatitude(), req.centerLongitude(),
                req.radiusMeters(), req.populationEstimate(), corr);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(c));
    }

    @GetMapping({"/api/v1/ndila/catchments/{id}", "/api/v1/maps/catchments/{id}"})
    public ResponseEntity<CatchmentResponse> get(@PathVariable UUID id) {
        return catchmentService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping({
            "/api/v1/ndila/catchments/by-owner/{ownerService}/{ownerEntityId}",
            "/api/v1/maps/catchments/by-owner/{ownerService}/{ownerEntityId}"
    })
    public ResponseEntity<List<CatchmentResponse>> byOwner(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String ownerService,
            @PathVariable String ownerEntityId) {
        UUID tenant = UUID.fromString(tenantId);
        return ResponseEntity.ok(catchmentService.findByOwner(tenant, ownerService, ownerEntityId)
                .stream().map(this::toResponse).toList());
    }

    @PostMapping({"/api/v1/ndila/catchments/check-point", "/api/v1/maps/catchments/check-point"})
    public ResponseEntity<List<CatchmentResponse>> checkPoint(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody CheckPointRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        Coordinate point = new Coordinate(req.point().latitude().doubleValue(),
                req.point().longitude().doubleValue());
        return ResponseEntity.ok(catchmentService.checkPoint(tenant, point).matches()
                .stream().map(this::toResponse).toList());
    }

    @PostMapping({"/api/v1/ndila/catchments/nearest-service", "/api/v1/maps/catchments/nearest-service"})
    public ResponseEntity<NearestServiceResponse> nearestService(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody NearestServiceRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        Coordinate point = new Coordinate(req.point().latitude().doubleValue(),
                req.point().longitude().doubleValue());
        int limit = req.limit() == null ? 5 : req.limit();
        NdilaCatchmentService.NearestServiceResult r = catchmentService
                .nearestService(tenant, point, req.locationType(), limit);
        List<NearestServiceMatch> out = r.matches().stream()
                .map(m -> new NearestServiceMatch(
                        m.location().getId().toString(),
                        m.location().getName(),
                        m.location().getLocationType(),
                        m.distanceMeters()))
                .toList();
        return ResponseEntity.ok(new NearestServiceResponse(req.point(), out));
    }

    @PostMapping({"/api/v1/ndila/catchments/coverage-summary", "/api/v1/maps/catchments/coverage-summary"})
    public ResponseEntity<CoverageSummaryResponse> coverageSummary(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody CoverageSummaryRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        NdilaCatchmentService.CoverageSummary s = catchmentService.coverageSummary(
                tenant, req.ownerService(), req.ownerEntityId());
        return ResponseEntity.ok(new CoverageSummaryResponse(
                s.ownerService(), s.ownerEntityId(), s.catchmentCount(),
                s.populationEstimate(), s.householdsEstimate()));
    }

    private CatchmentResponse toResponse(NdilaCatchmentAreaEntity c) {
        return new CatchmentResponse(
                c.getId().toString(), c.getName(), c.getOwnerService(), c.getOwnerEntityId(),
                c.getGeometryType(), c.getPopulationEstimate(), c.getHouseholdsEstimate(), c.isActive());
    }
}
