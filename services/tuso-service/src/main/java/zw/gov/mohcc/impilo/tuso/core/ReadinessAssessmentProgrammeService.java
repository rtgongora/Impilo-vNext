package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The readiness assessment programme — rounds, assignments, and a cycle driven by decay.
 *
 * <p>V042 gave readiness a shape but no source: 904 maternity-capable facilities, none ever
 * assessed. This is what gets observations recorded, governed rather than ad hoc, because an
 * assessment is a claim about clinical readiness that referral decisions rest on.</p>
 *
 * <p><b>Currency, not coverage.</b> Signal functions expire after their recall window — three
 * months by WHO convention — so "904 facilities assessed at some point" can be entirely worthless.
 * The number a referral network depends on is how much of it is current <em>today</em>. Coverage
 * flatters a programme; currency describes the map. Both are reported, and the difference between
 * them is the honest measure of whether the cadence is working.</p>
 *
 * <p><b>An assignment is not an observation.</b> Assigning changes nothing about a facility, and
 * closing a round does not resolve the facilities nobody reached. A round that completes at 40%
 * leaves 60% of the network UNKNOWN — not 60% unequipped.</p>
 */
@Service
public class ReadinessAssessmentProgrammeService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessAssessmentProgrammeService.class);

    /**
     * A facility becomes due before its data expires, not after. Assessing only once a facility has
     * already gone STALE guarantees the register oscillates between current and misleading; this
     * lead time is what keeps it continuously current.
     */
    static final int DUE_LEAD_DAYS = 21;

    private final JdbcTemplate jdbc;

    public ReadinessAssessmentProgrammeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record OpenRoundRequest(String code, String name, String method, String owningAuthority,
                                   String scopeProvince, String scopeFacilityType,
                                   LocalDate opensOn, LocalDate closesOn, Integer recallWindowMonths) {}

    public record RoundSummary(UUID roundId, String code, int facilitiesAssigned) {}

    public record ProgrammeStatus(int maternityFacilities,
                                  int everAssessed,
                                  int currentlyAssessed,
                                  int staleOrExpired,
                                  int neverAssessed,
                                  double currencyPercent,
                                  double coveragePercent,
                                  int dueSoon,
                                  String narrative) {}

    // ── Rounds ──────────────────────────────────────────────────────────────

    /**
     * Open a round and generate its assignments over the maternity-capable facilities in scope.
     *
     * <p>Assignments are generated once, at open. A facility added to the register later is not
     * retro-assigned — it appears in the next round, and meanwhile shows as never assessed, which
     * is true.</p>
     */
    @Transactional
    public RoundSummary openRound(OpenRoundRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        validate(request);

        UUID roundId = UUID.randomUUID();
        int recall = request.recallWindowMonths() == null ? 3 : request.recallWindowMonths();

        jdbc.update(
                "INSERT INTO tuso.readiness_assessment_round "
                        + "(id, tenant_id, code, name, method, owning_authority, scope_province, "
                        + " scope_facility_type, opens_on, closes_on, recall_window_months, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)",
                roundId, ctx.tenantId(), request.code(), request.name(),
                request.method().toUpperCase(Locale.ROOT), request.owningAuthority(),
                blankToNull(request.scopeProvince()), blankToNull(request.scopeFacilityType()),
                request.opensOn(), request.closesOn(), recall, ctx.actorId());

        // Scope is maternity-capable facilities: the ones whose readiness a referral network needs.
        // Deliberately NOT every facility — assigning 7,285 assessments nobody will do produces a
        // worklist that reads as failure rather than a programme.
        int assigned = jdbc.update(
                """
                INSERT INTO tuso.readiness_assessment_assignment
                       (tenant_id, round_id, facility_id, due_on, status)
                SELECT DISTINCT ?, ?, f.id, ?, 'PENDING'
                  FROM tuso.facility f
                  JOIN tuso.facility_capability c ON c.facility_id = f.id AND c.active
                 WHERE f.tenant_id = ?
                   AND upper(c.capability_code) IN ('MATERNITY', 'OBSTETRICS')
                   AND (CAST(? AS text) IS NULL OR lower(f.province) = lower(?))
                   AND (CAST(? AS text) IS NULL OR f.facility_type = ?)
                ON CONFLICT (round_id, facility_id) DO NOTHING
                """,
                ctx.tenantId(), roundId, request.closesOn(), ctx.tenantId(),
                blankToNull(request.scopeProvince()), blankToNull(request.scopeProvince()),
                blankToNull(request.scopeFacilityType()), blankToNull(request.scopeFacilityType()));

        log.info("Readiness round {} ({}) opened by {} — {} facilities assigned, method {}, recall {}m",
                request.code(), roundId, ctx.actorId(), assigned, request.method(), recall);
        return new RoundSummary(roundId, request.code(), assigned);
    }

    private static void validate(OpenRoundRequest r) {
        if (r == null || isBlank(r.code()) || isBlank(r.name())
                || isBlank(r.method()) || isBlank(r.owningAuthority())) {
            throw new IllegalArgumentException(
                    "code, name, method and owningAuthority are required — an unattributed round "
                            + "produces findings nobody can weigh");
        }
        if (r.opensOn() == null || r.closesOn() == null || r.closesOn().isBefore(r.opensOn())) {
            throw new IllegalArgumentException("opensOn and closesOn are required, and must not invert");
        }
        int recall = r.recallWindowMonths() == null ? 3 : r.recallWindowMonths();
        if (r.opensOn().plusDays(recall * 31L).isBefore(r.closesOn())) {
            throw new IllegalArgumentException(
                    "A round longer than its own recall window cannot produce a current register: "
                            + "the facilities assessed first have expired before the last are "
                            + "visited. Shorten the round or state a longer recall window.");
        }
    }

    /**
     * Close a round. Deliberately does <b>not</b> resolve outstanding assignments: the facilities
     * nobody reached stay PENDING, and their readiness stays UNKNOWN. Marking them anything else
     * would convert "we ran out of time" into a finding.
     */
    @Transactional
    public Map<String, Object> closeRound(UUID roundId, String note) {
        TrustContext ctx = TrustContextHolder.require();
        int updated = jdbc.update(
                "UPDATE tuso.readiness_assessment_round "
                        + "   SET status = 'CLOSED', closed_at = now(), closure_note = ? "
                        + " WHERE id = ? AND tenant_id = ? AND status = 'OPEN'",
                note, roundId, ctx.tenantId());
        if (updated == 0) {
            throw new IllegalStateException("No open round found: " + roundId);
        }
        Map<String, Object> outcome = jdbc.queryForMap(
                "SELECT count(*) FILTER (WHERE status = 'COMPLETED')   AS completed, "
                        + "       count(*) FILTER (WHERE status = 'PENDING')     AS not_reached, "
                        + "       count(*) FILTER (WHERE status = 'UNREACHABLE') AS unreachable, "
                        + "       count(*) AS assigned "
                        + "  FROM tuso.readiness_assessment_assignment WHERE round_id = ?",
                roundId);
        log.info("Readiness round {} closed by {}: {}", roundId, ctx.actorId(), outcome);
        return outcome;
    }

    // ── Assignments ─────────────────────────────────────────────────────────

    /** Assign an assessor. Optional — an unassigned round is a queue rather than a rota. */
    @Transactional
    public void assign(UUID assignmentId, String assignedTo, String assignedRole) {
        TrustContext ctx = TrustContextHolder.require();
        jdbc.update(
                "UPDATE tuso.readiness_assessment_assignment "
                        + "   SET assigned_to = ?, assigned_role = ?, "
                        + "       status = CASE WHEN status = 'PENDING' THEN 'IN_PROGRESS' ELSE status END, "
                        + "       updated_at = now() "
                        + " WHERE id = ? AND tenant_id = ?",
                assignedTo, assignedRole, assignmentId, ctx.tenantId());
    }

    /**
     * Mark an assignment complete against a real assessment. The schema refuses COMPLETED without
     * one, so a round cannot report itself finished having recorded nothing.
     */
    @Transactional
    public void complete(UUID assignmentId, UUID assessmentId) {
        TrustContext ctx = TrustContextHolder.require();
        if (assessmentId == null) {
            throw new IllegalArgumentException(
                    "An assignment completes against a recorded assessment — without one there is "
                            + "nothing to complete");
        }
        int updated = jdbc.update(
                "UPDATE tuso.readiness_assessment_assignment "
                        + "   SET status = 'COMPLETED', assessment_id = ?, updated_at = now() "
                        + " WHERE id = ? AND tenant_id = ?",
                assessmentId, assignmentId, ctx.tenantId());
        if (updated == 0) {
            throw new IllegalArgumentException("Assignment not found: " + assignmentId);
        }
    }

    /** Record that a facility could not be reached — a fact about the network, not a non-event. */
    @Transactional
    public void markUnreachable(UUID assignmentId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        jdbc.update(
                "UPDATE tuso.readiness_assessment_assignment "
                        + "   SET status = 'UNREACHABLE', outcome_note = ?, updated_at = now() "
                        + " WHERE id = ? AND tenant_id = ?",
                reason, assignmentId, ctx.tenantId());
    }

    /** An assessor's own queue. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myAssignments(String assignedTo, String status, int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return jdbc.queryForList(
                """
                SELECT a.id, a.facility_id, f.name AS facility_name, f.province, f.district,
                       a.status, a.due_on, r.code AS round_code, r.method
                  FROM tuso.readiness_assessment_assignment a
                  JOIN tuso.facility f ON f.id = a.facility_id
                  JOIN tuso.readiness_assessment_round r ON r.id = a.round_id
                 WHERE a.tenant_id = ?
                   AND (CAST(? AS text) IS NULL OR a.assigned_to = ?)
                   AND (CAST(? AS text) IS NULL OR a.status = ?)
                   AND r.status = 'OPEN'
                 ORDER BY a.due_on, f.province, f.name
                 LIMIT ?
                """,
                ctx.tenantId(), blankToNull(assignedTo), blankToNull(assignedTo),
                blankToNull(status), blankToNull(status), Math.min(Math.max(limit, 1), 500));
    }

    // ── The number that matters ─────────────────────────────────────────────

    /**
     * Programme status, reporting currency and coverage separately.
     *
     * <p>The gap between them is the point. A programme can reach every facility once and still
     * leave the register useless if the cadence is longer than the recall window — coverage would
     * read 100% while currency read 25%.</p>
     */
    @Transactional(readOnly = true)
    public ProgrammeStatus status() {
        TrustContext ctx = TrustContextHolder.require();

        int maternity = intOf(jdbc.queryForObject(
                "SELECT count(DISTINCT f.id) FROM tuso.facility f "
                        + "  JOIN tuso.facility_capability c ON c.facility_id = f.id AND c.active "
                        + " WHERE f.tenant_id = ? AND upper(c.capability_code) IN ('MATERNITY','OBSTETRICS')",
                Integer.class, ctx.tenantId()));

        int everAssessed = intOf(jdbc.queryForObject(
                "SELECT count(DISTINCT facility_id) FROM tuso.facility_readiness_assessment WHERE tenant_id = ?",
                Integer.class, ctx.tenantId()));

        // Current = the facility's most recent assessment is still inside the recall window it was
        // gathered under. Anything older has expired, whatever it said.
        int current = intOf(jdbc.queryForObject(
                """
                SELECT count(*) FROM (
                    SELECT DISTINCT ON (facility_id) facility_id, assessed_at, recall_window_months
                      FROM tuso.facility_readiness_assessment
                     WHERE tenant_id = ?
                     ORDER BY facility_id, assessed_at DESC
                ) latest
                 WHERE latest.assessed_at > now() - make_interval(months => latest.recall_window_months)
                """,
                Integer.class, ctx.tenantId()));

        int dueSoon = intOf(jdbc.queryForObject(
                """
                SELECT count(*) FROM (
                    SELECT DISTINCT ON (facility_id) facility_id, assessed_at, recall_window_months
                      FROM tuso.facility_readiness_assessment
                     WHERE tenant_id = ?
                     ORDER BY facility_id, assessed_at DESC
                ) latest
                 WHERE latest.assessed_at > now() - make_interval(months => latest.recall_window_months)
                   AND latest.assessed_at <= now() - make_interval(months => latest.recall_window_months)
                                                  + make_interval(days => ?)
                """,
                Integer.class, ctx.tenantId(), DUE_LEAD_DAYS));

        int stale = Math.max(0, everAssessed - current);
        int never = Math.max(0, maternity - everAssessed);
        double currency = maternity == 0 ? 0.0 : round1(100.0 * current / maternity);
        double coverage = maternity == 0 ? 0.0 : round1(100.0 * everAssessed / maternity);

        return new ProgrammeStatus(maternity, everAssessed, current, stale, never,
                currency, coverage, dueSoon,
                narrate(maternity, everAssessed, current, stale, never, currency, coverage));
    }

    /** States the gap between coverage and currency in words, because the gap is the finding. */
    static String narrate(int maternity, int everAssessed, int current, int stale, int never,
                          double currency, double coverage) {
        if (maternity == 0) {
            return "No maternity-capable facilities are registered, so there is nothing to assess.";
        }
        if (everAssessed == 0) {
            return "None of the " + maternity + " maternity-capable facilities has ever had a "
                    + "readiness assessment. The emergency obstetric capability of the referral "
                    + "network is unmeasured — which is not the same as absent, and cannot be "
                    + "improved by anything other than going to look.";
        }
        StringBuilder out = new StringBuilder();
        out.append(current).append(" of ").append(maternity).append(" maternity-capable facilities ")
                .append("have current readiness data (").append(currency).append("%).");
        if (stale > 0) {
            out.append(' ').append(stale).append(" were assessed but their observations have expired")
                    .append(" — they are unobserved again, not shown to have declined.");
        }
        if (never > 0) {
            out.append(' ').append(never).append(" have never been assessed.");
        }
        if (coverage - currency > 20.0) {
            out.append(" Coverage (").append(coverage).append("%) is well ahead of currency — the ")
                    .append("assessment cadence is slower than the data decays, so the register is ")
                    .append("aging faster than it is being refreshed.");
        }
        return out.toString();
    }

    /** Per-round progress, for the people running it. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> rounds() {
        TrustContext ctx = TrustContextHolder.require();
        return jdbc.queryForList(
                """
                SELECT r.id, r.code, r.name, r.method, r.owning_authority, r.scope_province,
                       r.opens_on, r.closes_on, r.recall_window_months, r.status,
                       count(a.id) AS assigned,
                       count(a.id) FILTER (WHERE a.status = 'COMPLETED')   AS completed,
                       count(a.id) FILTER (WHERE a.status = 'UNREACHABLE') AS unreachable
                  FROM tuso.readiness_assessment_round r
                  LEFT JOIN tuso.readiness_assessment_assignment a ON a.round_id = r.id
                 WHERE r.tenant_id = ?
                 GROUP BY r.id
                 ORDER BY r.opens_on DESC
                """,
                ctx.tenantId());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static boolean isBlank(String v) { return v == null || v.isBlank(); }
    private static String blankToNull(String v) { return isBlank(v) ? null : v.trim(); }
    private static int intOf(Integer v) { return v == null ? 0 : v; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
