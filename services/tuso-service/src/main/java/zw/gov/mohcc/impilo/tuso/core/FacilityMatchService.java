package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Live silent duplicate detection for facility registration (FJ2, D-L5) — the
 * registration-time counterpart of the batch import matcher: exact facility
 * code, exact governed alias, then trigram name similarity (the V002 gin_trgm
 * index), tightened when the candidate is in the same district.
 *
 * <p><b>INTERNAL ONLY.</b> The result — including every candidate — is steward
 * material. It must never be serialised onto a claimant/registrant-facing
 * response: the registrant sees one generic "received, under review" shape
 * whether or not a credible match exists (Place Journey Doctrine §2).</p>
 */
@Service
public class FacilityMatchService {

    /** A match strong enough to silently block creation and open a steward case. */
    public record MatchCandidate(FacilityEntity facility, String matchType, double score) {}

    public record MatchResult(boolean credibleMatch, List<MatchCandidate> candidates) {
        public static MatchResult none() {
            return new MatchResult(false, List.of());
        }
    }

    private final FacilityRepository facilityRepository;
    private final FacilityIdentifierRepository identifierRepository;
    private final double nameSimilarityThreshold;
    private final double sameDistrictSimilarityThreshold;

    public FacilityMatchService(
            FacilityRepository facilityRepository,
            FacilityIdentifierRepository identifierRepository,
            @Value("${tuso.match.name-similarity-threshold:0.75}") double nameSimilarityThreshold,
            @Value("${tuso.match.same-district-similarity-threshold:0.55}") double sameDistrictSimilarityThreshold) {
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.nameSimilarityThreshold = nameSimilarityThreshold;
        this.sameDistrictSimilarityThreshold = sameDistrictSimilarityThreshold;
    }

    /**
     * Search-before-create: find existing facilities a registration credibly
     * duplicates. Identifier hits (code, governed aliases) are always credible;
     * a name-similarity hit is credible above the global threshold, or above
     * the lower same-district threshold ("Bangure Clinic" twice in one district
     * is a duplicate; the same name two provinces apart may not be).
     */
    @Transactional(readOnly = true)
    public MatchResult matchForRegistration(UUID tenantId, String name, String facilityCode,
                                            Map<String, String> identifiers, String district) {
        List<MatchCandidate> candidates = new ArrayList<>();

        if (facilityCode != null && !facilityCode.isBlank()) {
            facilityRepository.findByTenantIdAndFacilityCode(tenantId, facilityCode.trim())
                    .ifPresent(f -> candidates.add(new MatchCandidate(f, "FACILITY_CODE", 1.0)));
        }

        if (identifiers != null) {
            for (Map.Entry<String, String> alias : identifiers.entrySet()) {
                if (alias.getKey() == null || alias.getValue() == null || alias.getValue().isBlank()) {
                    continue;
                }
                Optional<FacilityEntity> byAlias = identifierRepository
                        .findBySystemAndValue(alias.getKey().trim(), alias.getValue().trim())
                        .map(FacilityIdentifierEntity::getFacility)
                        .filter(f -> tenantId.equals(f.getTenantId()));
                byAlias.ifPresent(f -> candidates.add(
                        new MatchCandidate(f, "ALIAS:" + alias.getKey().trim(), 1.0)));
            }
        }

        if (name != null && !name.isBlank()) {
            String normalised = normalise(name);
            for (Object[] row : facilityRepository.findSimilarByName(
                    tenantId, normalised, sameDistrictSimilarityThreshold)) {
                // Native query returns (facility id, score) columns — row[0] is the id
                // (a Number), NOT an entity — so load the FacilityEntity by id.
                Long facilityId = ((Number) row[0]).longValue();
                double score = ((Number) row[1]).doubleValue();
                FacilityEntity facility = facilityRepository.findById(facilityId).orElse(null);
                if (facility == null) {
                    continue;
                }
                boolean sameDistrict = district != null && !district.isBlank()
                        && district.trim().equalsIgnoreCase(facility.getDistrict());
                double threshold = sameDistrict ? sameDistrictSimilarityThreshold : nameSimilarityThreshold;
                if (score >= threshold) {
                    candidates.add(new MatchCandidate(
                            facility, sameDistrict ? "NAME_SAME_DISTRICT" : "NAME", score));
                }
            }
        }

        return new MatchResult(!candidates.isEmpty(), List.copyOf(candidates));
    }

    /** Same normalisation as the batch import matcher — the two must not diverge. */
    static String normalise(String name) {
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
