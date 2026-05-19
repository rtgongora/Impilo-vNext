package zw.gov.mohcc.impilo.ndila.core.search;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geo.GeoMath;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spatial search service — Ndila answers questions like "what facilities are
 * near this person?", "which providers are inside this district?", "which
 * pharmacies are within 10km?", and so on.
 */
@Service
public class NdilaSpatialSearchService {

    private final NdilaLocationRepository locationRepository;

    public NdilaSpatialSearchService(NdilaLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public List<Match> nearby(UUID tenantId, Coordinate origin, double radiusMeters,
                              List<String> entityTypes, int limit) {
        List<NdilaLocationEntity> candidates = entityTypes == null || entityTypes.isEmpty()
                ? locationRepository.findByTenantId(tenantId)
                : entityTypes.stream()
                .flatMap(t -> locationRepository.findByTenantIdAndLocationType(tenantId, t).stream())
                .collect(Collectors.toList());

        List<Match> matches = new ArrayList<>();
        for (NdilaLocationEntity loc : candidates) {
            if (!loc.isActive() || loc.getLatitude() == null || loc.getLongitude() == null) continue;
            Coordinate c = new Coordinate(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
            double d = GeoMath.haversineMeters(origin, c);
            if (d <= radiusMeters) {
                matches.add(new Match(loc, d));
            }
        }
        matches.sort(Comparator.comparingDouble(Match::distanceMeters));
        return matches.stream().limit(Math.max(1, limit)).toList();
    }

    public List<NdilaLocationEntity> withinDistrict(UUID tenantId, String district) {
        return locationRepository.findByTenantIdAndDistrict(tenantId, district);
    }

    public List<Match> nearest(UUID tenantId, Coordinate origin, String locationType, int limit) {
        List<NdilaLocationEntity> candidates = locationType == null
                ? locationRepository.findByTenantId(tenantId)
                : locationRepository.findByTenantIdAndLocationType(tenantId, locationType);
        List<Match> matches = new ArrayList<>();
        for (NdilaLocationEntity loc : candidates) {
            if (!loc.isActive() || loc.getLatitude() == null || loc.getLongitude() == null) continue;
            Coordinate c = new Coordinate(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
            matches.add(new Match(loc, GeoMath.haversineMeters(origin, c)));
        }
        matches.sort(Comparator.comparingDouble(Match::distanceMeters));
        return matches.stream().limit(Math.max(1, limit)).toList();
    }

    public record Match(NdilaLocationEntity location, double distanceMeters) {}
}
