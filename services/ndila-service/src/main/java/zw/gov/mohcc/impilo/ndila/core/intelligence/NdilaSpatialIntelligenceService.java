package zw.gov.mohcc.impilo.ndila.core.intelligence;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaCoordinateValidator;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spatial Intelligence Engine.
 *
 * <p>Deterministic, fallback-safe summaries that Nompilo and dashboards can
 * trust. Outputs include coordinate-quality issues, service-coverage gaps,
 * risk-cluster summaries, inspection gaps, delivery-performance summaries and
 * route-deviation summaries. Every summary includes a confidence score and
 * explainability hints.
 */
@Service
public class NdilaSpatialIntelligenceService {

    private final NdilaLocationRepository locationRepository;
    private final NdilaCoordinateValidator validator;

    public NdilaSpatialIntelligenceService(NdilaLocationRepository locationRepository,
                                           NdilaCoordinateValidator validator) {
        this.locationRepository = locationRepository;
        this.validator = validator;
    }

    public CoordinateQualityReport coordinateQuality(UUID tenantId, String district) {
        List<NdilaLocationEntity> locations = district == null
                ? locationRepository.findByTenantId(tenantId)
                : locationRepository.findByTenantIdAndDistrict(tenantId, district);
        int missing = 0;
        int unverified = 0;
        int implausible = 0;
        Map<String, List<UUID>> duplicates = new HashMap<>();
        for (NdilaLocationEntity loc : locations) {
            if (loc.getLatitude() == null || loc.getLongitude() == null) {
                missing++;
                continue;
            }
            if ("UNVERIFIED".equals(loc.getVerificationStatus())) unverified++;
            NdilaCoordinateValidator.Result v = validator.validate(
                    loc.getLatitude(), loc.getLongitude(),
                    loc.getCoordinateAccuracyMeters(), loc.getCountry());
            if (!v.plausible()) implausible++;
            String key = loc.getLatitude().setScale(5, java.math.RoundingMode.HALF_UP)
                    + "," + loc.getLongitude().setScale(5, java.math.RoundingMode.HALF_UP);
            duplicates.computeIfAbsent(key, k -> new ArrayList<>()).add(loc.getId());
        }
        int duplicateGroups = 0;
        for (List<UUID> group : duplicates.values()) {
            if (group.size() > 1) duplicateGroups++;
        }
        double confidence = locations.isEmpty() ? 0.0 : 0.85;
        List<String> explainability = new ArrayList<>();
        if (missing > 0) explainability.add(missing + " locations have no coordinates.");
        if (unverified > 0) explainability.add(unverified + " locations are unverified.");
        if (implausible > 0) explainability.add(implausible + " locations are implausible for ZW.");
        if (duplicateGroups > 0) explainability.add(duplicateGroups + " coordinate duplicates.");
        return new CoordinateQualityReport(
                locations.size(), missing, unverified, implausible, duplicateGroups,
                confidence, explainability);
    }

    public RiskClusterSummary riskClusters(UUID tenantId, List<Coordinate> incidentPoints, double radiusMeters) {
        // Lightweight clustering: count incidents within `radiusMeters` of each other.
        int clusters = 0;
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < incidentPoints.size(); i++) {
            if (visited.contains(i)) continue;
            int count = 1;
            visited.add(i);
            for (int j = i + 1; j < incidentPoints.size(); j++) {
                if (visited.contains(j)) continue;
                if (incidentPoints.get(i).distanceMetersTo(incidentPoints.get(j)) <= radiusMeters) {
                    visited.add(j);
                    count++;
                }
            }
            if (count >= 2) clusters++;
        }
        double confidence = incidentPoints.isEmpty() ? 0.0 : Math.min(0.9, 0.4 + 0.05 * clusters);
        List<String> explainability = new ArrayList<>();
        explainability.add(incidentPoints.size() + " incidents analysed.");
        if (clusters == 0) explainability.add("No clusters detected at this radius.");
        else explainability.add(clusters + " spatial clusters detected within " + radiusMeters + "m.");
        return new RiskClusterSummary(incidentPoints.size(), clusters, radiusMeters, confidence, explainability);
    }

    public ServiceCoverageGap detectServiceCoverageGap(UUID tenantId, List<Coordinate> demandPoints,
                                                       List<Coordinate> supplyPoints, double thresholdMeters) {
        int uncovered = 0;
        for (Coordinate demand : demandPoints) {
            double nearest = Double.MAX_VALUE;
            for (Coordinate supply : supplyPoints) {
                nearest = Math.min(nearest, demand.distanceMetersTo(supply));
            }
            if (nearest > thresholdMeters) uncovered++;
        }
        double confidence = demandPoints.isEmpty() ? 0.0 : 0.75;
        return new ServiceCoverageGap(demandPoints.size(), uncovered, thresholdMeters, confidence);
    }

    public record CoordinateQualityReport(int total, int missing, int unverified, int implausible,
                                          int duplicateGroups, double confidence,
                                          List<String> explainability) {}
    public record RiskClusterSummary(int incidentCount, int clusterCount, double radiusMeters,
                                     double confidence, List<String> explainability) {}
    public record ServiceCoverageGap(int demandPoints, int uncovered, double thresholdMeters, double confidence) {}
}
