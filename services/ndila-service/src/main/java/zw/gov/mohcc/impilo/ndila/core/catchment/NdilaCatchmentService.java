package zw.gov.mohcc.impilo.ndila.core.catchment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaEventTopics;
import zw.gov.mohcc.impilo.ndila.core.events.NdilaOutboxPublisher;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoJsonReader;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoMath;
import zw.gov.mohcc.impilo.ndila.domain.NdilaCatchmentAreaEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaCatchmentRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NdilaCatchmentService {

    private final NdilaCatchmentRepository repository;
    private final NdilaLocationRepository locationRepository;
    private final NdilaOutboxPublisher outbox;

    public NdilaCatchmentService(NdilaCatchmentRepository repository,
                                 NdilaLocationRepository locationRepository,
                                 NdilaOutboxPublisher outbox) {
        this.repository = repository;
        this.locationRepository = locationRepository;
        this.outbox = outbox;
    }

    @Transactional
    public NdilaCatchmentAreaEntity create(UUID tenantId, String name, String ownerService,
                                           String ownerEntityType, String ownerEntityId,
                                           String geometryType, String geometryJson,
                                           BigDecimal centerLat, BigDecimal centerLng,
                                           BigDecimal radiusMeters, Integer population,
                                           UUID correlationId) {
        NdilaCatchmentAreaEntity c = new NdilaCatchmentAreaEntity(tenantId, name, ownerService);
        c.setOwnerEntityType(ownerEntityType);
        c.setOwnerEntityId(ownerEntityId);
        c.setGeometryType(geometryType == null ? "POLYGON" : geometryType);
        c.setGeometryJson(geometryJson);
        c.setCenterLatitude(centerLat);
        c.setCenterLongitude(centerLng);
        c.setRadiusMeters(radiusMeters);
        c.setPopulationEstimate(population);
        repository.save(c);

        Map<String, Object> payload = new HashMap<>();
        payload.put("catchmentId", c.getId());
        payload.put("ownerService", ownerService);
        payload.put("ownerEntityId", ownerEntityId);
        payload.put("geometryType", c.getGeometryType());
        outbox.publish(new NdilaOutboxPublisher.NdilaEvent(
                NdilaEventTopics.CATCHMENT_CREATED, "Catchment", c.getId().toString(),
                tenantId, correlationId, payload));
        return c;
    }

    public Optional<NdilaCatchmentAreaEntity> findById(UUID id) { return repository.findById(id); }

    public List<NdilaCatchmentAreaEntity> findByOwner(UUID tenantId, String ownerService, String ownerEntityId) {
        return repository.findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, ownerService, ownerEntityId);
    }

    public boolean catchmentContains(NdilaCatchmentAreaEntity c, Coordinate point) {
        if ("RADIUS".equalsIgnoreCase(c.getGeometryType()) && c.getCenterLatitude() != null
                && c.getCenterLongitude() != null && c.getRadiusMeters() != null) {
            Coordinate center = new Coordinate(
                    c.getCenterLatitude().doubleValue(), c.getCenterLongitude().doubleValue());
            return GeoMath.isWithinRadius(center, c.getRadiusMeters().doubleValue(), point);
        }
        if (c.getGeometryJson() != null) {
            GeoJsonReader.ParsedGeometry geom = GeoJsonReader.parse(c.getGeometryJson());
            if (!(geom instanceof GeoJsonReader.ParsedGeometry.Unsupported)) {
                return geom.contains(point);
            }
        }
        return false;
    }

    public CheckResult checkPoint(UUID tenantId, Coordinate point) {
        List<NdilaCatchmentAreaEntity> matches = new ArrayList<>();
        for (NdilaCatchmentAreaEntity c : repository.findByTenantIdAndActiveTrue(tenantId)) {
            if (catchmentContains(c, point)) {
                matches.add(c);
            }
        }
        return new CheckResult(point, matches);
    }

    public NearestServiceResult nearestService(UUID tenantId, Coordinate point, String locationType, int limit) {
        List<NdilaLocationEntity> candidates = locationType != null
                ? locationRepository.findByTenantIdAndLocationType(tenantId, locationType)
                : locationRepository.findByTenantId(tenantId);
        List<ServiceMatch> matches = new ArrayList<>();
        for (NdilaLocationEntity loc : candidates) {
            if (loc.getLatitude() == null || loc.getLongitude() == null || !loc.isActive()) continue;
            Coordinate c = new Coordinate(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
            double dist = GeoMath.haversineMeters(point, c);
            matches.add(new ServiceMatch(loc, dist));
        }
        matches.sort(Comparator.comparingDouble(ServiceMatch::distanceMeters));
        return new NearestServiceResult(point, matches.stream().limit(Math.max(1, limit)).toList());
    }

    public CoverageSummary coverageSummary(UUID tenantId, String ownerService, String ownerEntityId) {
        List<NdilaCatchmentAreaEntity> catchments = findByOwner(tenantId, ownerService, ownerEntityId);
        int population = catchments.stream()
                .mapToInt(c -> c.getPopulationEstimate() == null ? 0 : c.getPopulationEstimate())
                .sum();
        int households = catchments.stream()
                .mapToInt(c -> c.getHouseholdsEstimate() == null ? 0 : c.getHouseholdsEstimate())
                .sum();
        return new CoverageSummary(ownerService, ownerEntityId, catchments.size(), population, households);
    }

    /** Detects overlapping catchments owned by different entities. */
    public List<OverlapPair> detectOverlaps(UUID tenantId) {
        List<NdilaCatchmentAreaEntity> all = repository.findByTenantIdAndActiveTrue(tenantId);
        List<OverlapPair> overlaps = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            NdilaCatchmentAreaEntity a = all.get(i);
            if (a.getCenterLatitude() == null || a.getCenterLongitude() == null) continue;
            Coordinate aCenter = new Coordinate(
                    a.getCenterLatitude().doubleValue(), a.getCenterLongitude().doubleValue());
            for (int j = i + 1; j < all.size(); j++) {
                NdilaCatchmentAreaEntity b = all.get(j);
                if (catchmentContains(b, aCenter)) {
                    overlaps.add(new OverlapPair(a.getId(), b.getId()));
                }
            }
        }
        return overlaps;
    }

    public record ServiceMatch(NdilaLocationEntity location, double distanceMeters) {}
    public record NearestServiceResult(Coordinate point, List<ServiceMatch> matches) {}
    public record CheckResult(Coordinate point, List<NdilaCatchmentAreaEntity> matches) {}
    public record CoverageSummary(String ownerService, String ownerEntityId, int catchmentCount,
                                   int populationEstimate, int householdsEstimate) {}
    public record OverlapPair(UUID a, UUID b) {}
}
