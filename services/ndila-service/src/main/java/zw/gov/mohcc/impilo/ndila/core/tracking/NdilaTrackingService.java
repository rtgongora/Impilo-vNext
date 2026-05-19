package zw.gov.mohcc.impilo.ndila.core.tracking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaEventTopics;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geofence.NdilaGeofenceService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaTrackingAssetEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaTrackingEventEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaTrackingAssetRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaTrackingEventRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NdilaTrackingService {

    private final NdilaTrackingAssetRepository assetRepository;
    private final NdilaTrackingEventRepository eventRepository;
    private final NdilaGeofenceService geofenceService;
    private final NdilaOutboxPublisher outbox;

    public NdilaTrackingService(NdilaTrackingAssetRepository assetRepository,
                                NdilaTrackingEventRepository eventRepository,
                                NdilaGeofenceService geofenceService,
                                NdilaOutboxPublisher outbox) {
        this.assetRepository = assetRepository;
        this.eventRepository = eventRepository;
        this.geofenceService = geofenceService;
        this.outbox = outbox;
    }

    @Transactional
    public NdilaTrackingAssetEntity registerAsset(UUID tenantId, String assetId, String assetType,
                                                  String ownerService, String ownerEntityId,
                                                  String label, String sensitivityLevel,
                                                  UUID correlationId) {
        Optional<NdilaTrackingAssetEntity> existing = assetRepository
                .findByTenantIdAndAssetId(tenantId, assetId);
        if (existing.isPresent()) return existing.get();

        NdilaTrackingAssetEntity asset = new NdilaTrackingAssetEntity(tenantId, assetId, assetType);
        asset.setOwnerService(ownerService);
        asset.setOwnerEntityId(ownerEntityId);
        asset.setLabel(label);
        if (sensitivityLevel != null) asset.setSensitivityLevel(sensitivityLevel);
        assetRepository.save(asset);

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", asset.getAssetId());
        payload.put("assetType", asset.getAssetType());
        payload.put("ownerService", ownerService);
        payload.put("ownerEntityId", ownerEntityId);
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.TRACKING_ASSET_CREATED,
                "TrackingAsset", asset.getAssetId(), tenantId, correlationId, payload));
        return asset;
    }

    @Transactional
    public NdilaTrackingEventEntity ingestEvent(UUID tenantId, String assetId,
                                                BigDecimal latitude, BigDecimal longitude,
                                                BigDecimal accuracyMeters,
                                                OffsetDateTime eventTimestamp,
                                                String telemetrySource,
                                                UUID correlationId) {
        Optional<NdilaTrackingAssetEntity> assetOpt = assetRepository
                .findByTenantIdAndAssetId(tenantId, assetId);
        String assetType = assetOpt.map(NdilaTrackingAssetEntity::getAssetType).orElse("OTHER");

        NdilaTrackingEventEntity event = new NdilaTrackingEventEntity(
                tenantId, assetId, assetType, latitude, longitude,
                eventTimestamp == null ? OffsetDateTime.now() : eventTimestamp);
        event.setAccuracyMeters(accuracyMeters);
        if (telemetrySource != null) event.setTelemetrySource(telemetrySource);
        assetOpt.ifPresent(a -> {
            event.setOwnerService(a.getOwnerService());
            event.setOwnerEntityId(a.getOwnerEntityId());
        });
        eventRepository.save(event);

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", assetId);
        payload.put("latitude", latitude);
        payload.put("longitude", longitude);
        payload.put("eventTimestamp", event.getEventTimestamp().toString());
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.TRACKING_LOCATION_UPDATED,
                "TrackingEvent", assetId, tenantId, correlationId, payload));

        // Geofence-breach detection.
        Coordinate point = new Coordinate(latitude.doubleValue(), longitude.doubleValue());
        geofenceService.detectBreaches(tenantId, assetId, point, correlationId);
        return event;
    }

    public Optional<NdilaTrackingEventEntity> latest(UUID tenantId, String assetId) {
        return eventRepository.findFirstByTenantIdAndAssetIdOrderByEventTimestampDesc(tenantId, assetId);
    }

    public List<NdilaTrackingEventEntity> history(UUID tenantId, String assetId,
                                                   OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime f = from == null ? OffsetDateTime.now().minusDays(7) : from;
        OffsetDateTime t = to == null ? OffsetDateTime.now() : to;
        return eventRepository.findHistory(tenantId, assetId, f, t);
    }

    public List<NdilaTrackingEventEntity> nearby(UUID tenantId, Coordinate point, double radiusMeters) {
        List<NdilaTrackingEventEntity> latests = eventRepository.findLatestPerAsset(tenantId);
        List<NdilaTrackingEventEntity> out = new java.util.ArrayList<>();
        for (NdilaTrackingEventEntity e : latests) {
            Coordinate ec = new Coordinate(e.getLatitude().doubleValue(), e.getLongitude().doubleValue());
            if (point.distanceMetersTo(ec) <= radiusMeters) out.add(e);
        }
        return out;
    }
}
