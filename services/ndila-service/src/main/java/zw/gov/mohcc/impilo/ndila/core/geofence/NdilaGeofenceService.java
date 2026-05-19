package zw.gov.mohcc.impilo.ndila.core.geofence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaEventTopics;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoJsonReader;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoJsonReader.ParsedGeometry;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoMath;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeofenceEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaGeofenceRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NdilaGeofenceService {

    private final NdilaGeofenceRepository repository;
    private final NdilaOutboxPublisher outbox;

    public NdilaGeofenceService(NdilaGeofenceRepository repository, NdilaOutboxPublisher outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public NdilaGeofenceEntity create(UUID tenantId, String name, String geofenceType,
                                      String geometryJson, String ownerService, String ownerEntityType,
                                      String ownerEntityId, String riskLevel,
                                      BigDecimal centerLat, BigDecimal centerLng, BigDecimal radiusMeters,
                                      UUID correlationId) {
        NdilaGeofenceEntity g = new NdilaGeofenceEntity(tenantId, name, geofenceType, geometryJson);
        g.setOwnerService(ownerService);
        g.setOwnerEntityType(ownerEntityType);
        g.setOwnerEntityId(ownerEntityId);
        if (riskLevel != null) g.setRiskLevel(riskLevel);
        g.setCenterLatitude(centerLat);
        g.setCenterLongitude(centerLng);
        g.setRadiusMeters(radiusMeters);
        repository.save(g);
        publish(NdilaEventTopics.GEOFENCE_CREATED, g, correlationId, null);
        return g;
    }

    public Optional<NdilaGeofenceEntity> findById(UUID id) { return repository.findById(id); }

    public CheckResult checkPoint(UUID tenantId, Coordinate point) {
        List<NdilaGeofenceEntity> all = repository.findByTenantIdAndActiveTrue(tenantId);
        List<NdilaGeofenceEntity> matches = new ArrayList<>();
        for (NdilaGeofenceEntity g : all) {
            if (geometryContains(g, point)) {
                matches.add(g);
            }
        }
        return new CheckResult(point, matches);
    }

    public boolean geometryContains(NdilaGeofenceEntity g, Coordinate point) {
        // Prefer geometry-json; fall back to center+radius circle.
        if (g.getGeometryJson() != null) {
            ParsedGeometry geom = GeoJsonReader.parse(g.getGeometryJson());
            if (!(geom instanceof ParsedGeometry.Unsupported)) {
                return geom.contains(point);
            }
        }
        if (g.getCenterLatitude() != null && g.getCenterLongitude() != null && g.getRadiusMeters() != null) {
            Coordinate center = new Coordinate(
                    g.getCenterLatitude().doubleValue(), g.getCenterLongitude().doubleValue());
            return GeoMath.isWithinRadius(center, g.getRadiusMeters().doubleValue(), point);
        }
        return false;
    }

    public List<UUID> findIntersectingGeofenceIds(UUID tenantId, List<Coordinate> path) {
        List<NdilaGeofenceEntity> all = repository.findByTenantIdAndActiveTrue(tenantId);
        List<UUID> ids = new ArrayList<>();
        for (NdilaGeofenceEntity g : all) {
            for (Coordinate p : path) {
                if (geometryContains(g, p)) {
                    ids.add(g.getId());
                    break;
                }
            }
        }
        return ids;
    }

    /**
     * Detects geofence breaches for a tracking event and emits an event for
     * each entry into a HIGH/CRITICAL risk geofence and for each "outside
     * expected zone" condition.
     */
    public List<UUID> detectBreaches(UUID tenantId, String assetId, Coordinate point, UUID correlationId) {
        List<UUID> breaches = new ArrayList<>();
        for (NdilaGeofenceEntity g : repository.findByTenantIdAndActiveTrue(tenantId)) {
            boolean inside = geometryContains(g, point);
            boolean restricted = "RESTRICTED_ZONE".equals(g.getGeofenceType())
                    || "NO_FLY_ZONE".equals(g.getGeofenceType())
                    || "HIGH".equals(g.getRiskLevel()) || "CRITICAL".equals(g.getRiskLevel());
            if (inside && restricted) {
                breaches.add(g.getId());
                Map<String, Object> payload = new HashMap<>();
                payload.put("assetId", assetId);
                payload.put("geofenceId", g.getId());
                payload.put("geofenceType", g.getGeofenceType());
                payload.put("riskLevel", g.getRiskLevel());
                payload.put("point", Map.of("latitude", point.latitude(), "longitude", point.longitude()));
                outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                        NdilaEventTopics.GEOFENCE_BREACH_DETECTED,
                        "Geofence", g.getId().toString(), tenantId, correlationId, payload));
            }
        }
        return breaches;
    }

    private void publish(String topic, NdilaGeofenceEntity g, UUID correlationId, Object extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", g.getTenantId());
        payload.put("geofenceId", g.getId());
        payload.put("geofenceType", g.getGeofenceType());
        payload.put("riskLevel", g.getRiskLevel());
        payload.put("ownerService", g.getOwnerService());
        if (extra != null) payload.put("extra", extra);
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                topic, "Geofence", g.getId().toString(), g.getTenantId(), correlationId, payload));
    }

    public record CheckResult(Coordinate point, List<NdilaGeofenceEntity> matches) {}
}
