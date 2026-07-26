package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * EmONC signal-function readiness — observed, expiring, and never inferred.
 *
 * <p><b>The question this answers, and the one it refuses.</b> "Can this facility perform a
 * caesarean tonight?" cannot be answered from {@code facility_capability}: those tags were produced
 * from templates by facility tier, which is why the live counts cluster at 1757 / 904 / 199. A
 * SURGERY tag says a facility is of a type that ought to have surgery. It says nothing about
 * whether a theatre is staffed or an anaesthetist is on call.</p>
 *
 * <p>WHO signal functions are the honest unit: a facility <em>provides</em> one if it has actually
 * performed it at least once in the recall period, conventionally three months. So this service
 * reports what was observed, when, by whom, and how stale that now is — and it reports
 * {@link Status#UNKNOWN} loudly rather than guessing.</p>
 *
 * <p><b>The invariant that matters clinically: absence of evidence is not evidence of absence.</b>
 * An unassessed facility is UNKNOWN, never NOT_AVAILABLE. Telling a midwife with an obstructed
 * labour that a facility <em>cannot</em> do a caesarean, when nobody has ever looked, sends her
 * past the nearest theatre. That is a worse error than admitting ignorance, and this service is
 * built so it cannot be made.</p>
 */
@Service
public class EmoncReadinessService {

    private static final Logger log = LoggerFactory.getLogger(EmoncReadinessService.class);

    /** The WHO recall period: "performed at least once in the last three months". */
    static final int DEFAULT_RECALL_MONTHS = 3;

    /** Beyond its recall window an observation is STALE — aged out, not disproved. */
    public enum Status { AVAILABLE, NOT_AVAILABLE, STALE, UNKNOWN }

    /** BEmONC is the first seven signal functions; CEmONC is all nine. */
    public enum Classification { CEMONC, BEMONC, NEITHER, INSUFFICIENT_EVIDENCE }

    private final JdbcTemplate jdbc;

    public EmoncReadinessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SignalFunctionStatus(String code, String displayName, String tier, int ordinal,
                                       Status status, String barrier, LocalDate lastPerformedOn,
                                       Instant observedAt, String assessmentMethod, Integer daysSinceObserved) {}

    /**
     * @param classification         computed, never stored — a stored CEmONC label outlives the
     *                               anaesthetist who justified it.
     * @param unknownFunctions       functions never assessed. Non-empty means the classification is
     *                               {@code INSUFFICIENT_EVIDENCE}, not a negative verdict.
     * @param staleFunctions         functions whose last observation has aged past its recall window.
     * @param missingFunctions       functions positively assessed as not performed.
     * @param narrative              a sentence a clinician can act on, including what is not known.
     */
    public record FacilityEmoncReadiness(long facilityId, String facilityName,
                                         Classification classification,
                                         List<SignalFunctionStatus> signalFunctions,
                                         List<String> unknownFunctions,
                                         List<String> staleFunctions,
                                         List<String> missingFunctions,
                                         Instant lastAssessedAt,
                                         String narrative) {}

    public record RecordAssessmentRequest(Long facilityId, String method, String assessorRole,
                                          Integer recallWindowMonths, String sourceReference,
                                          String notes, List<Finding> findings) {}

    public record Finding(String signalFunction, String finding, String barrier,
                          LocalDate lastPerformedOn, String evidenceReference) {}

    // ── Read ────────────────────────────────────────────────────────────────

    /**
     * Current readiness for one facility, derived from the most recent observation of each signal
     * function. Never materialised — the verdict and its evidence cannot drift apart if the verdict
     * is computed from the evidence on every read.
     */
    @Transactional(readOnly = true)
    public FacilityEmoncReadiness readiness(long facilityId) {
        TrustContext ctx = TrustContextHolder.require();

        Map<String, Object> facility;
        try {
            facility = jdbc.queryForMap(
                    "SELECT id, name FROM tuso.facility WHERE id = ? AND tenant_id = ?",
                    facilityId, ctx.tenantId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Facility not found: " + facilityId);
        }

        // Every catalogue function, left-joined to its latest observation. The LEFT JOIN is the
        // whole point: a function with no observation comes back with nulls and becomes UNKNOWN,
        // rather than silently dropping out of the result and reading as "not offered".
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT c.code, c.display_name, c.tier, c.ordinal,
                       o.finding, o.barrier, o.last_performed_on,
                       a.assessed_at, a.method, a.recall_window_months
                  FROM tuso.emonc_signal_function c
                  LEFT JOIN LATERAL (
                      SELECT obs.*, asm.assessed_at AS a_assessed_at
                        FROM tuso.facility_signal_function_observation obs
                        JOIN tuso.facility_readiness_assessment asm ON asm.id = obs.assessment_id
                       WHERE obs.facility_id = ? AND obs.signal_function = c.code
                         AND obs.finding <> 'NOT_ASSESSED'
                       ORDER BY asm.assessed_at DESC
                       LIMIT 1
                  ) o ON TRUE
                  LEFT JOIN tuso.facility_readiness_assessment a ON a.id = o.assessment_id
                 ORDER BY c.ordinal
                """,
                facilityId);

        List<SignalFunctionStatus> statuses = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Instant lastAssessed = null;

        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            String finding = (String) row.get("finding");
            Instant observedAt = toInstant(row.get("assessed_at"));
            int recall = row.get("recall_window_months") == null
                    ? DEFAULT_RECALL_MONTHS : ((Number) row.get("recall_window_months")).intValue();
            Integer ageDays = observedAt == null ? null
                    : (int) ChronoUnit.DAYS.between(observedAt, Instant.now());

            Status status = resolveStatus(finding, observedAt, recall);
            switch (status) {
                case UNKNOWN -> unknown.add(code);
                case STALE -> stale.add(code);
                case NOT_AVAILABLE -> missing.add(code);
                case AVAILABLE -> { /* nothing to record */ }
            }
            if (observedAt != null && (lastAssessed == null || observedAt.isAfter(lastAssessed))) {
                lastAssessed = observedAt;
            }

            statuses.add(new SignalFunctionStatus(
                    code,
                    (String) row.get("display_name"),
                    (String) row.get("tier"),
                    ((Number) row.get("ordinal")).intValue(),
                    status,
                    (String) row.get("barrier"),
                    toLocalDate(row.get("last_performed_on")),
                    observedAt,
                    (String) row.get("method"),
                    ageDays));
        }

        Classification classification = classify(statuses);
        return new FacilityEmoncReadiness(
                facilityId,
                (String) facility.get("name"),
                classification,
                statuses,
                unknown, stale, missing,
                lastAssessed,
                narrate(classification, unknown, stale, missing, lastAssessed));
    }

    /**
     * An observation is AVAILABLE only while it is within the recall window it was gathered under.
     * Past that it becomes STALE — the facility has not been shown to have stopped, only to have
     * not been looked at recently, and those are different things.
     */
    static Status resolveStatus(String finding, Instant observedAt, int recallMonths) {
        if (finding == null || observedAt == null) {
            return Status.UNKNOWN;
        }
        if ("NOT_PERFORMED".equals(finding)) {
            return Status.NOT_AVAILABLE;
        }
        if (!"PERFORMED".equals(finding)) {
            return Status.UNKNOWN;
        }
        Instant expiry = observedAt.plus(recallMonths * 30L, ChronoUnit.DAYS);
        return Instant.now().isBefore(expiry) ? Status.AVAILABLE : Status.STALE;
    }

    /**
     * Classification is all-or-nothing and evidence-gated.
     *
     * <p>The important branch is the first: if <em>any</em> function is unknown or stale we return
     * {@code INSUFFICIENT_EVIDENCE}, not {@code NEITHER}. "We cannot tell" and "it cannot" are
     * different answers, and only one of them is honest about an unassessed facility.</p>
     */
    static Classification classify(List<SignalFunctionStatus> statuses) {
        if (statuses.isEmpty()) {
            return Classification.INSUFFICIENT_EVIDENCE;
        }
        boolean anyUnresolved = statuses.stream()
                .anyMatch(s -> s.status() == Status.UNKNOWN || s.status() == Status.STALE);

        boolean allBasicAvailable = statuses.stream()
                .filter(s -> "BASIC".equals(s.tier()))
                .allMatch(s -> s.status() == Status.AVAILABLE);
        boolean allAvailable = statuses.stream().allMatch(s -> s.status() == Status.AVAILABLE);

        if (allAvailable) {
            return Classification.CEMONC;
        }
        if (allBasicAvailable && !anyUnresolvedInTier(statuses, "COMPREHENSIVE")) {
            // Every basic function confirmed, and the two comprehensive ones positively assessed as
            // not performed — a genuine BEmONC facility, not an unmeasured one.
            return Classification.BEMONC;
        }
        if (anyUnresolved) {
            return Classification.INSUFFICIENT_EVIDENCE;
        }
        return Classification.NEITHER;
    }

    private static boolean anyUnresolvedInTier(List<SignalFunctionStatus> statuses, String tier) {
        return statuses.stream()
                .filter(s -> tier.equals(s.tier()))
                .anyMatch(s -> s.status() == Status.UNKNOWN || s.status() == Status.STALE);
    }

    /** A sentence a referring clinician can act on — including, explicitly, what is not known. */
    static String narrate(Classification classification, List<String> unknown, List<String> stale,
                          List<String> missing, Instant lastAssessed) {
        return switch (classification) {
            case CEMONC -> "All nine EmONC signal functions were performed within their recall "
                    + "period. This does not confirm the facility is open or staffed right now — "
                    + "call ahead.";
            case BEMONC -> "All seven basic EmONC signal functions were performed within their "
                    + "recall period; caesarean section and/or blood transfusion were not. Refer "
                    + "elsewhere for surgical or transfusion needs, and call ahead.";
            case NEITHER -> "This facility was assessed and did not perform "
                    + missing.size() + " of the nine signal functions: " + String.join(", ", missing)
                    + ". Call ahead before referring.";
            case INSUFFICIENT_EVIDENCE -> lastAssessed == null
                    ? "This facility has never had an EmONC readiness assessment. Nothing is known "
                      + "about its emergency obstetric capability — this is not a statement that it "
                      + "has none. Call ahead."
                    : "EmONC readiness cannot be determined: "
                      + (unknown.isEmpty() ? "" : unknown.size() + " signal function(s) never assessed")
                      + (unknown.isEmpty() || stale.isEmpty() ? "" : ", ")
                      + (stale.isEmpty() ? "" : stale.size() + " observation(s) older than their "
                          + "recall period")
                      + ". Absent evidence is not evidence of absence — call ahead.";
        };
    }

    // ── Write ───────────────────────────────────────────────────────────────

    /**
     * Record one readiness assessment and its findings. Append-only: a later assessment supersedes
     * an earlier one by being more recent, and nothing is ever overwritten, so the history of what
     * was believed and when survives.
     */
    @Transactional
    public UUID recordAssessment(RecordAssessmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request == null || request.facilityId() == null) {
            throw new IllegalArgumentException("facilityId is required");
        }
        if (request.findings() == null || request.findings().isEmpty()) {
            throw new IllegalArgumentException(
                    "An assessment with no findings records nothing — supply at least one finding");
        }
        Integer facilityExists = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility WHERE id = ? AND tenant_id = ?",
                Integer.class, request.facilityId(), ctx.tenantId());
        if (facilityExists == null || facilityExists == 0) {
            throw new IllegalArgumentException("Facility not found: " + request.facilityId());
        }

        UUID assessmentId = UUID.randomUUID();
        int recall = request.recallWindowMonths() == null
                ? DEFAULT_RECALL_MONTHS : request.recallWindowMonths();

        jdbc.update(
                "INSERT INTO tuso.facility_readiness_assessment "
                        + "(id, tenant_id, facility_id, method, assessed_at, assessed_by, assessor_role, "
                        + " recall_window_months, source_reference, notes) "
                        + "VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, ?)",
                assessmentId, ctx.tenantId(), request.facilityId(),
                normaliseMethod(request.method()), ctx.actorId(), request.assessorRole(),
                recall, request.sourceReference(), request.notes());

        for (Finding finding : request.findings()) {
            jdbc.update(
                    "INSERT INTO tuso.facility_signal_function_observation "
                            + "(tenant_id, assessment_id, facility_id, signal_function, finding, "
                            + " barrier, last_performed_on, evidence_reference) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    ctx.tenantId(), assessmentId, request.facilityId(),
                    finding.signalFunction(), normaliseFinding(finding.finding()),
                    finding.barrier(), finding.lastPerformedOn(), finding.evidenceReference());
        }

        log.info("EmONC readiness assessment {} recorded for facility {} ({} findings, method {})",
                assessmentId, request.facilityId(), request.findings().size(), request.method());
        return assessmentId;
    }

    /**
     * Facilities whose EmONC readiness is unknown or stale — the assessment worklist. Ordered by
     * how little is known, because the facilities nobody has ever looked at are the ones a referral
     * network is most blind about.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> assessmentWorklist(String province, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return jdbc.queryForList(
                """
                SELECT f.id AS facility_id, f.name, f.province, f.district, f.regulatory_status,
                       max(a.assessed_at) AS last_assessed_at,
                       count(DISTINCT o.signal_function) FILTER (
                           WHERE o.finding <> 'NOT_ASSESSED') AS functions_observed
                  FROM tuso.facility f
                  LEFT JOIN tuso.facility_readiness_assessment a ON a.facility_id = f.id
                  LEFT JOIN tuso.facility_signal_function_observation o ON o.assessment_id = a.id
                 WHERE f.tenant_id = ?
                   AND (CAST(? AS text) IS NULL OR lower(f.province) = lower(?))
                   AND EXISTS (SELECT 1 FROM tuso.facility_capability fc
                                WHERE fc.facility_id = f.id AND fc.active
                                  AND upper(fc.capability_code) IN ('MATERNITY', 'OBSTETRICS'))
                 GROUP BY f.id, f.name, f.province, f.district, f.regulatory_status
                 ORDER BY count(DISTINCT o.signal_function) FILTER (
                              WHERE o.finding <> 'NOT_ASSESSED') ASC,
                          max(a.assessed_at) ASC NULLS FIRST,
                          f.name
                 LIMIT ?
                """,
                ctx.tenantId(), blankToNull(province), blankToNull(province), Math.min(Math.max(limit, 1), 500));
    }

    /** Coverage across the estate — how much of the referral network is actually measured. */
    @Transactional(readOnly = true)
    public Map<String, Object> coverageSummary() {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("maternityFacilities", jdbc.queryForObject(
                "SELECT count(DISTINCT fc.facility_id) FROM tuso.facility_capability fc "
                        + " JOIN tuso.facility f ON f.id = fc.facility_id "
                        + " WHERE f.tenant_id = ? AND fc.active "
                        + "   AND upper(fc.capability_code) IN ('MATERNITY','OBSTETRICS')",
                Integer.class, ctx.tenantId()));
        out.put("facilitiesEverAssessed", jdbc.queryForObject(
                "SELECT count(DISTINCT facility_id) FROM tuso.facility_readiness_assessment WHERE tenant_id = ?",
                Integer.class, ctx.tenantId()));
        out.put("assessmentsRecorded", jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility_readiness_assessment WHERE tenant_id = ?",
                Integer.class, ctx.tenantId()));
        return out;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String normaliseMethod(String method) {
        return method == null || method.isBlank() ? "SELF_REPORTED"
                : method.trim().toUpperCase(Locale.ROOT);
    }

    private static String normaliseFinding(String finding) {
        return finding == null || finding.isBlank() ? "NOT_ASSESSED"
                : finding.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof Instant i) return i;
        return null;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDate ld) return ld;
        return null;
    }
}
