package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

/**
 * Lapses Ministry legitimacy decisions that have passed their expiry (FCV-W1).
 *
 * <p>A decision may be issued for a bounded period — a conditional operating verdict pending
 * remediation, say. Storing {@code expires_at} and never reading it would be worse than not storing
 * it at all: the facility would keep its platform allow indefinitely while the record claimed the
 * permission had lapsed, and the system would be over-granting silently. This sweep is what makes
 * the expiry mean something.</p>
 *
 * <p><b>Expiry is a lapse, not a judgement.</b> The sweep never decides a facility is illegitimate —
 * it withdraws the platform allow and returns the facility to "not currently verified", which is
 * exactly where an unverified facility sits. Restoring it is a new decision by a verifier, not an
 * automatic renewal: the whole point of an expiry date is that somebody looks again.</p>
 */
@Component
public class FacilityLegitimacyExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(FacilityLegitimacyExpirySweep.class);

    private final JdbcTemplate jdbc;
    private final FacilitySourceLegitimacyRepository legitimacyRepository;

    public FacilityLegitimacyExpirySweep(JdbcTemplate jdbc,
                                         FacilitySourceLegitimacyRepository legitimacyRepository) {
        this.jdbc = jdbc;
        this.legitimacyRepository = legitimacyRepository;
    }

    @Scheduled(fixedDelayString = "${tuso.legitimacy-expiry-sweep.interval-ms:3600000}")
    public void sweep() {
        int lapsed = expireLapsed(LocalDate.now());
        if (lapsed > 0) {
            log.info("Facility legitimacy sweep: {} decision(s) lapsed and their platform allow withdrawn", lapsed);
        }
    }

    /**
     * Withdraw the platform allow for every standing decision whose expiry has passed.
     *
     * <p>Idempotent: a decision is only acted on while its projected {@code PLATFORM_OPERATIONAL}
     * row still allows, so re-running changes nothing.</p>
     */
    @Transactional
    public int expireLapsed(LocalDate asOf) {
        List<Map<String, Object>> lapsed = jdbc.queryForList(
                "SELECT d.id, d.facility_uuid, d.verdict, d.expires_at "
                        + "FROM tuso.facility_legitimacy_decision d "
                        + "JOIN tuso.facility_source_legitimacy l "
                        + "  ON l.facility_id = d.facility_uuid "
                        + " AND l.source = 'PLATFORM_OPERATIONAL' "
                        + " AND l.decision_id = d.id "
                        + " AND l.allowed_on_platform "
                        + "WHERE d.superseded_by IS NULL AND d.expires_at IS NOT NULL AND d.expires_at < ?",
                asOf);

        int count = 0;
        for (Map<String, Object> row : lapsed) {
            UUID facilityUuid = (UUID) row.get("facility_uuid");
            UUID decisionId = (UUID) row.get("id");
            legitimacyRepository.findByFacilityIdAndSource(
                            facilityUuid, FacilityLegitimacySource.PLATFORM_OPERATIONAL)
                    .ifPresent(platform -> {
                        platform.setAllowedOnPlatform(false);
                        platform.setStatus(FacilityLegitimacyStatus.EXPIRED);
                        platform.setAsOf(Instant.now());
                        platform.setReason("Ministry legitimacy decision " + decisionId
                                + " expired on " + row.get("expires_at")
                                + "; platform operation withdrawn pending a fresh decision.");
                        platform.setRecordedBy("legitimacy-expiry-sweep");
                        legitimacyRepository.save(platform);
                    });
            // The Ministry's recognition is NOT withdrawn — the Ministry still recognises this
            // facility; what lapsed is the platform's time-bounded permission to operate.
            log.info("Facility {} legitimacy decision {} expired ({}); platform allow withdrawn",
                    facilityUuid, decisionId, row.get("expires_at"));
            count++;
        }
        return count;
    }

    /**
     * Decisions due for review but not yet expired — the queue a verifier works so a permission
     * lapses by decision rather than by neglect.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> dueForReview(LocalDate asOf) {
        return jdbc.queryForList(
                "SELECT id, facility_uuid, verdict, review_date, expires_at, decided_by_role, jurisdiction_code "
                        + "FROM tuso.facility_legitimacy_decision "
                        + "WHERE superseded_by IS NULL AND review_date IS NOT NULL AND review_date <= ? "
                        + "ORDER BY review_date", asOf);
    }
}
