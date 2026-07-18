package zw.gov.mohcc.impilo.vito.core.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.core.MatchDisposition;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.MatchResultEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.MatchResultRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Probabilistic Matching Engine — WHO-recommended identity resolution.
 *
 * Implements multi-field weighted scoring:
 *   - Given name:    Jaro-Winkler similarity (weight 0.15)
 *   - Family name:   Jaro-Winkler similarity (weight 0.20)
 *   - Date of birth: exact match (weight 0.30)
 *   - Sex:           exact match (weight 0.10)
 *   - Phone hash:    exact match (weight 0.25)
 *
 * Thresholds (configurable via VitoProperties):
 *   - Auto-link:      >= 0.95 → AUTO_LINKED disposition
 *   - Manual review:  >= 0.70 → PENDING (operator queue)
 *   - Below threshold: ignored (no match result created)
 *
 * Also supports FHIR $match interop via OpenCR adapter.
 */
@Service
public class MatchingEngine {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MatchingEngine.class);

    private final ClientRepository clientRepository;
    private final MatchResultRepository matchResultRepository;
    private final VitoProperties properties;
    private final ObjectMapper objectMapper;

    public MatchingEngine(ClientRepository clientRepository,
                          MatchResultRepository matchResultRepository,
                          VitoProperties properties,
                          ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.matchResultRepository = matchResultRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Run matching for a source client against all candidates in the same tenant.
     * Returns list of match results above the manual review threshold.
     */
    @Transactional
    public List<MatchResultEntity> findMatches(UUID tenantId, UUID sourceHealthId) {
        ClientEntity source = clientRepository.findByTenantIdAndHealthId(tenantId, sourceHealthId)
                .orElseThrow(() -> new IllegalArgumentException("Source client not found"));

        // Blocking (Identity Contract §13 / E3): restrict scoring to candidates
        // sharing a coarse blocking key (birth year OR family-name initial),
        // instead of scanning the whole tenant with a silent 1000-row cap.
        Integer birthYear = source.getDateOfBirth() != null
                ? source.getDateOfBirth().getYear() : null;
        String familyInitial = (source.getFamilyName() != null && !source.getFamilyName().isBlank())
                ? source.getFamilyName().substring(0, 1).toLowerCase() : null;

        List<ClientEntity> candidates;
        if (birthYear == null && familyInitial == null) {
            // No blocking key on the source — fall back to a bounded scan and log
            // the cap so coverage loss is never silent.
            Page<ClientEntity> page = clientRepository.findByTenantId(tenantId, Pageable.ofSize(1000));
            candidates = page.getContent();
            if (page.getTotalElements() > 1000) {
                log.warn("Matching source {} has no blocking key; scanned first 1000 of {} candidates "
                        + "(coverage capped)", sourceHealthId, page.getTotalElements());
            }
        } else {
            candidates = clientRepository.findBlockingCandidates(
                    tenantId, sourceHealthId, birthYear, familyInitial);
        }

        List<MatchResultEntity> results = new ArrayList<>();

        for (ClientEntity candidate : candidates) {
            if (candidate.getHealthId().equals(sourceHealthId)) continue;

            Map<String, Double> fieldScores = computeFieldScores(source, candidate);
            double totalScore = computeWeightedScore(fieldScores);

            if (totalScore >= properties.getMatching().getThresholdManualReview()) {
                MatchDisposition disposition = totalScore >= properties.getMatching().getThresholdAutoLink()
                        ? MatchDisposition.AUTO_LINKED
                        : MatchDisposition.PENDING;

                MatchResultEntity result = new MatchResultEntity();
                result.setTenantId(tenantId);
                result.setSourceHealthId(sourceHealthId);
                result.setCandidateHealthId(candidate.getHealthId());
                result.setMatchScore(BigDecimal.valueOf(totalScore));
                result.setMatchAlgorithm("PROBABILISTIC");
                result.setMatchFields(toJson(fieldScores));
                result.setDisposition(disposition);

                if (disposition == MatchDisposition.AUTO_LINKED) {
                    result.setResolvedBy("SYSTEM");
                    result.setResolvedAt(OffsetDateTime.now());
                }

                results.add(matchResultRepository.save(result));
            }
        }

        return results;
    }

    /**
     * Resolve a pending match result (operator decision).
     */
    @Transactional
    public MatchResultEntity resolve(Long matchId, MatchDisposition disposition, String resolvedBy) {
        MatchResultEntity result = matchResultRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match result not found"));

        if (result.getDisposition() != MatchDisposition.PENDING) {
            throw new IllegalStateException("Match already resolved: " + result.getDisposition());
        }

        result.setDisposition(disposition);
        result.setResolvedBy(resolvedBy);
        result.setResolvedAt(OffsetDateTime.now());

        return matchResultRepository.save(result);
    }

    /**
     * Get pending match results for operator resolution queue.
     */
    @Transactional(readOnly = true)
    public Page<MatchResultEntity> getPendingMatches(UUID tenantId, Pageable pageable) {
        return matchResultRepository.findByTenantIdAndDisposition(
                tenantId, MatchDisposition.PENDING, pageable);
    }

    // --- Field-level scoring ---

    /**
     * Single estate scorer (Identity Contract §13 / C6): score externally
     * supplied demographics against a candidate with the SAME field functions
     * and weights as entity-vs-entity matching. Replaces the drift-prone copy
     * that lived in ExternalRegistrationService.
     */
    public double scoreDemographics(String givenName, String familyName,
                                    String dateOfBirth, String sex,
                                    ClientEntity candidate) {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("givenName", jaroWinkler(givenName, candidate.getGivenName()));
        scores.put("familyName", jaroWinkler(familyName, candidate.getFamilyName()));
        scores.put("dateOfBirth", exactMatch(dateOfBirth,
                candidate.getDateOfBirth() != null ? candidate.getDateOfBirth().toString() : null));
        scores.put("sex", exactMatch(sex, candidate.getSex()));
        return computeWeightedScore(scores);
    }

    private Map<String, Double> computeFieldScores(ClientEntity source, ClientEntity candidate) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("givenName", jaroWinkler(source.getGivenName(), candidate.getGivenName()));
        scores.put("familyName", jaroWinkler(source.getFamilyName(), candidate.getFamilyName()));
        scores.put("dateOfBirth", exactMatch(
                source.getDateOfBirth() != null ? source.getDateOfBirth().toString() : null,
                candidate.getDateOfBirth() != null ? candidate.getDateOfBirth().toString() : null));
        scores.put("sex", exactMatch(source.getSex(), candidate.getSex()));
        scores.put("phoneHash", exactMatch(source.getPhoneHash(), candidate.getPhoneHash()));

        return scores;
    }

    private double computeWeightedScore(Map<String, Double> fieldScores) {
        double total = 0.0;
        total += fieldScores.getOrDefault("givenName", 0.0) * 0.15;
        total += fieldScores.getOrDefault("familyName", 0.0) * 0.20;
        total += fieldScores.getOrDefault("dateOfBirth", 0.0) * 0.30;
        total += fieldScores.getOrDefault("sex", 0.0) * 0.10;
        total += fieldScores.getOrDefault("phoneHash", 0.0) * 0.25;
        return total;
    }

    private double exactMatch(String a, String b) {
        if (a == null || b == null) return 0.0;
        return a.equalsIgnoreCase(b) ? 1.0 : 0.0;
    }

    /**
     * Jaro-Winkler similarity (0.0 to 1.0).
     * Standard implementation for name matching per WHO recommendations.
     */
    private double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        s1 = s1.toLowerCase().strip();
        s2 = s2.toLowerCase().strip();
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        int maxDist = Math.max(s1.length(), s2.length()) / 2 - 1;
        if (maxDist < 0) maxDist = 0;

        boolean[] s1Matches = new boolean[s1.length()];
        boolean[] s2Matches = new boolean[s2.length()];

        int matches = 0;
        int transpositions = 0;

        for (int i = 0; i < s1.length(); i++) {
            int start = Math.max(0, i - maxDist);
            int end = Math.min(s2.length(), i + maxDist + 1);
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        int k = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        double jaro = (((double) matches / s1.length()) +
                ((double) matches / s2.length()) +
                (((double) matches - transpositions / 2.0) / matches)) / 3.0;

        // Winkler boost for common prefix (up to 4 chars)
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }

        return jaro + (prefix * 0.1 * (1.0 - jaro));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
