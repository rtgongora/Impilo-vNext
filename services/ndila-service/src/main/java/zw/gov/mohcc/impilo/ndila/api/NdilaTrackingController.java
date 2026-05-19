package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.tracking.NdilaTrackingService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaTrackingAssetEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaTrackingEventEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class NdilaTrackingController {

    private final NdilaTrackingService trackingService;

    public NdilaTrackingController(NdilaTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @PostMapping({"/api/v1/ndila/tracking/assets", "/api/v1/maps/tracking/assets"})
    public ResponseEntity<TrackingAssetResponse> registerAsset(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody RegisterAssetRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        UUID corr = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        NdilaTrackingAssetEntity asset = trackingService.registerAsset(
                tenant, req.assetId(), req.assetType(), req.ownerService(),
                req.ownerEntityId(), req.label(), req.sensitivityLevel(), corr);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssetResponse(asset));
    }

    @GetMapping({"/api/v1/ndila/tracking/assets/{assetId}", "/api/v1/maps/tracking/assets/{assetId}"})
    public ResponseEntity<TrackingAssetResponse> getAsset(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String assetId) {
        return ResponseEntity.ok(toAssetResponse(
                new NdilaTrackingAssetEntity(UUID.fromString(tenantId), assetId, "OTHER")));
    }

    @PostMapping({"/api/v1/ndila/tracking/assets/{assetId}/location",
                  "/api/v1/maps/tracking/assets/{assetId}/location"})
    public ResponseEntity<TrackingEventResponse> ingestEvent(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable String assetId,
            @RequestBody TrackingEventRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        UUID corr = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        OffsetDateTime ts = req.eventTimestamp() == null
                ? OffsetDateTime.now() : OffsetDateTime.parse(req.eventTimestamp());
        NdilaTrackingEventEntity e = trackingService.ingestEvent(
                tenant, assetId, req.latitude(), req.longitude(), req.accuracyMeters(),
                ts, req.telemetrySource(), corr);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TrackingEventResponse(
                e.getEventId().toString(), e.getAssetId(),
                e.getLatitude(), e.getLongitude(), e.getEventTimestamp().toString()));
    }

    @GetMapping({"/api/v1/ndila/tracking/assets/{assetId}/latest",
                 "/api/v1/maps/tracking/assets/{assetId}/latest"})
    public ResponseEntity<TrackingEventResponse> latest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String assetId) {
        Optional<NdilaTrackingEventEntity> last = trackingService.latest(UUID.fromString(tenantId), assetId);
        return last.map(e -> ResponseEntity.ok(new TrackingEventResponse(
                e.getEventId().toString(), e.getAssetId(),
                e.getLatitude(), e.getLongitude(), e.getEventTimestamp().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping({"/api/v1/ndila/tracking/assets/{assetId}/history",
                 "/api/v1/maps/tracking/assets/{assetId}/history"})
    public ResponseEntity<List<TrackingEventResponse>> history(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String assetId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        OffsetDateTime f = from == null ? null : OffsetDateTime.parse(from);
        OffsetDateTime t = to == null ? null : OffsetDateTime.parse(to);
        List<NdilaTrackingEventEntity> events = trackingService.history(
                UUID.fromString(tenantId), assetId, f, t);
        return ResponseEntity.ok(events.stream()
                .map(e -> new TrackingEventResponse(
                        e.getEventId().toString(), e.getAssetId(),
                        e.getLatitude(), e.getLongitude(), e.getEventTimestamp().toString()))
                .toList());
    }

    @PostMapping({"/api/v1/ndila/tracking/assets/nearby", "/api/v1/maps/tracking/assets/nearby"})
    public ResponseEntity<List<TrackingEventResponse>> nearby(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody NearbyAssetsRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        Coordinate origin = new Coordinate(
                req.origin().latitude().doubleValue(), req.origin().longitude().doubleValue());
        List<NdilaTrackingEventEntity> events = trackingService.nearby(tenant, origin, req.radiusMeters());
        return ResponseEntity.ok(events.stream()
                .map(e -> new TrackingEventResponse(
                        e.getEventId().toString(), e.getAssetId(),
                        e.getLatitude(), e.getLongitude(), e.getEventTimestamp().toString()))
                .toList());
    }

    private TrackingAssetResponse toAssetResponse(NdilaTrackingAssetEntity a) {
        return new TrackingAssetResponse(
                a.getId() == null ? null : a.getId().toString(),
                a.getAssetId(), a.getAssetType(), a.getOwnerService(),
                a.getOwnerEntityId(), a.isActive(), a.getSensitivityLevel());
    }
}
