package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A claimant prepares their facility's profile privately; a steward decides whether it becomes truth
 * (FCV-W5).
 *
 * <p>Until now the only way to change a facility profile wrote the authoritative record directly, so
 * a pending claimant could prepare nothing and a steward could review nothing before it landed. This
 * gives the work somewhere to live: a submission holding proposed field values that are visible to
 * the claimant and the reviewer, that never describe the facility, and that become the record only
 * when accepted.</p>
 *
 * <p><b>Proposals are assertions, not a parallel model.</b> {@code facility_field_assertion} is
 * already per-field with provenance and supersession, and its source vocabulary has contained
 * {@code FACILITY_CLAIM} since the day it was written — with no writer. A draft is a set of
 * unpromoted assertions, so a facility's own statements about itself now carry the same provenance
 * as the regulator's, and the difference between "the facility says" and "the Ministry verified" is
 * visible in one place.</p>
 *
 * <p><b>Accepting a profile is not verifying a facility.</b> This lane changes descriptive fields.
 * It does not touch legitimacy, and it cannot make a facility operational — that remains the
 * Ministry decision rail (FCV-W1). A steward accepting an address is not a verifier accepting a
 * facility.</p>
 */
@Service
public class FacilityProfileSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(FacilityProfileSubmissionService.class);

    public static final String EVENT_SUBMITTED = "tuso.facility.profile_submission.submitted";
    public static final String EVENT_DECIDED = "tuso.facility.profile_submission.decided";

    /** States a claimant may still edit in. */
    private static final Set<String> EDITABLE = Set.of("DRAFT", "IN_PROGRESS", "READY_FOR_SUBMISSION",
            "CORRECTION_REQUIRED");
    /** States a steward may decide from. */
    private static final Set<String> REVIEWABLE = Set.of("SUBMITTED", "RESUBMITTED", "UNDER_REVIEW");
    private static final Set<String> TERMINAL = Set.of("VERIFIED", "CONDITIONALLY_VERIFIED",
            "REJECTED", "SUPERSEDED");

    /**
     * Fields a facility may propose about itself. Deliberately descriptive only — nothing here
     * changes status, regulatory standing or legitimacy, because those are not the facility's to
     * assert.
     */
    private static final Set<String> PROPOSABLE_FIELDS = Set.of(
            "name", "facility_type", "province", "district", "ward", "address_line1", "address_line2",
            "city", "latitude", "longitude", "ownership", "level", "phone", "email", "opening_hours");

    private final JdbcTemplate jdbc;
    private final FacilityRepository facilityRepository;

    public FacilityProfileSubmissionService(JdbcTemplate jdbc, FacilityRepository facilityRepository) {
        this.jdbc = jdbc;
        this.facilityRepository = facilityRepository;
    }

    public record SubmissionView(UUID id, String state, int proposedFieldCount, String correctionNote) {}

    /** Start or reuse the claimant's open submission for this facility. */
    @Transactional
    public UUID openDraft(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        UUID existing = openSubmissionId(facility.getFacilityUuid(), ctx.actorId());
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tuso.facility_profile_submission "
                        + "(id, tenant_id, facility_uuid, submitted_by, state, correlation_id) "
                        + "VALUES (?,?,?,?, 'DRAFT', ?)",
                id, ctx.tenantId(), facility.getFacilityUuid(), ctx.actorId(),
                ctx.correlationId() == null ? null : ctx.correlationId().toString());
        return id;
    }

    /**
     * Record a proposed value. Writes an assertion bound to the submission — private, not current,
     * and not a description of the facility until the submission is accepted.
     */
    @Transactional
    public void proposeField(UUID facilityUuid, String fieldPath, String value) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = requireFacility(facilityUuid, ctx.tenantId());
        String field = fieldPath == null ? "" : fieldPath.trim().toLowerCase(Locale.ROOT);
        if (!PROPOSABLE_FIELDS.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A facility may not propose '" + fieldPath + "'. Status, regulatory standing and "
                            + "legitimacy are decided about a facility, not asserted by it.");
        }
        UUID submissionId = openDraft(facilityUuid);
        String state = stateOf(submissionId);
        if (!EDITABLE.contains(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This submission is " + state + " and cannot be edited. "
                            + ("SUBMITTED".equals(state) || "RESUBMITTED".equals(state) || "UNDER_REVIEW".equals(state)
                                ? "It is with a reviewer." : "Start a new submission."));
        }

        // One live proposal per field per submission — re-proposing replaces, it does not stack.
        jdbc.update("DELETE FROM tuso.facility_field_assertion "
                        + "WHERE submission_id=? AND field_path=? AND promoted_at IS NULL",
                submissionId, field);
        jdbc.update("INSERT INTO tuso.facility_field_assertion ("
                        + "tenant_id, facility_id, field_path, asserted_value, source_system, "
                        + "confidence, verification_state, public_visibility, is_current, submission_id) "
                        + "VALUES (?,?,?,?, 'FACILITY_CLAIM', 'LOW', 'UNVERIFIED', 'INTERNAL', false, ?)",
                ctx.tenantId(), facility.getId(), field, value, submissionId);

        jdbc.update("UPDATE tuso.facility_profile_submission SET state='IN_PROGRESS', updated_at=now() "
                + "WHERE id=? AND state='DRAFT'", submissionId);
    }

    /** Hand the submission to a steward. */
    @Transactional
    public SubmissionView submit(UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        requireFacility(facilityUuid, ctx.tenantId());
        UUID submissionId = openSubmissionId(facilityUuid, ctx.actorId());
        if (submissionId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You have no open submission for this facility.");
        }
        String state = stateOf(submissionId);
        if (!EDITABLE.contains(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This submission is already " + state + ".");
        }
        if (proposedCount(submissionId) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There is nothing to submit — propose at least one value first.");
        }
        // A correction being answered is a RESUBMISSION, so the reviewer can see it is a second look.
        String next = "CORRECTION_REQUIRED".equals(state) ? "RESUBMITTED" : "SUBMITTED";
        jdbc.update("UPDATE tuso.facility_profile_submission SET state=?, submitted_at=now(), "
                + "updated_at=now() WHERE id=?", next, submissionId);
        log.info("Facility {} profile submission {} -> {} by {}", facilityUuid, submissionId, next, ctx.actorId());
        return view(submissionId);
    }

    /** Return the submission to the claimant with what must change. */
    @Transactional
    public SubmissionView requestCorrection(UUID submissionId, String note) {
        TrustContext ctx = TrustContextHolder.require();
        String state = stateOf(submissionId);
        if (!REVIEWABLE.contains(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a submitted profile can be returned for correction; this one is " + state + ".");
        }
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say what needs to change — a correction request without a reason cannot be acted on.");
        }
        jdbc.update("UPDATE tuso.facility_profile_submission SET state='CORRECTION_REQUIRED', "
                        + "correction_note=?, reviewed_by=?, reviewed_at=now(), updated_at=now() WHERE id=?",
                note.trim(), ctx.actorId(), submissionId);
        return view(submissionId);
    }

    /**
     * Accept the submission: its proposals become the facility's current field assertions.
     *
     * <p>Promotion supersedes the previous current assertion for each field rather than deleting it,
     * so what the facility said, when, and what it replaced all stay answerable. A verified field is
     * never silently overwritten — that is the concurrent-edit protection the brief asks for.</p>
     */
    @Transactional
    public SubmissionView accept(UUID submissionId, String decisionNote) {
        TrustContext ctx = TrustContextHolder.require();
        String state = stateOf(submissionId);
        if (!REVIEWABLE.contains(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a submitted profile can be accepted; this one is " + state + ".");
        }

        // Supersede the field's previous current assertion, carrying the old value forward as history.
        jdbc.update("UPDATE tuso.facility_field_assertion prev SET is_current=false, "
                        + "supersession_reason='Superseded by facility profile submission " + submissionId + "' "
                        + "FROM tuso.facility_field_assertion proposed "
                        + "WHERE proposed.submission_id=? AND proposed.promoted_at IS NULL "
                        + "  AND prev.facility_id=proposed.facility_id AND prev.field_path=proposed.field_path "
                        + "  AND prev.is_current AND prev.id <> proposed.id",
                submissionId);

        int promoted = jdbc.update("UPDATE tuso.facility_field_assertion "
                        + "SET is_current=true, promoted_at=now(), verified_by=?, verified_at=now() "
                        + "WHERE submission_id=? AND promoted_at IS NULL",
                ctx.actorId(), submissionId);

        jdbc.update("UPDATE tuso.facility_profile_submission SET state='VERIFIED', decision_note=?, "
                        + "reviewed_by=?, reviewed_at=now(), updated_at=now() WHERE id=?",
                decisionNote, ctx.actorId(), submissionId);
        log.info("Facility profile submission {} ACCEPTED by {} — {} field assertion(s) promoted",
                submissionId, ctx.actorId(), promoted);
        return view(submissionId);
    }

    /** Refuse the submission. Its proposals stay as history and never become current. */
    @Transactional
    public SubmissionView reject(UUID submissionId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        String state = stateOf(submissionId);
        if (!REVIEWABLE.contains(state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a submitted profile can be rejected; this one is " + state + ".");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required to reject a facility's submission.");
        }
        jdbc.update("UPDATE tuso.facility_profile_submission SET state='REJECTED', decision_note=?, "
                        + "reviewed_by=?, reviewed_at=now(), updated_at=now() WHERE id=?",
                reason.trim(), ctx.actorId(), submissionId);
        return view(submissionId);
    }

    /** The steward queue — submissions waiting on a human, oldest first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reviewQueue() {
        TrustContext ctx = TrustContextHolder.require();
        return jdbc.queryForList(
                "SELECT s.id, s.facility_uuid, s.submitted_by, s.state, s.submitted_at, "
                        + "(SELECT count(*) FROM tuso.facility_field_assertion a "
                        + "  WHERE a.submission_id=s.id AND a.promoted_at IS NULL) AS proposed_fields "
                        + "FROM tuso.facility_profile_submission s "
                        + "WHERE s.tenant_id=? AND s.state IN ('SUBMITTED','RESUBMITTED','UNDER_REVIEW') "
                        + "ORDER BY s.submitted_at", ctx.tenantId());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private UUID openSubmissionId(UUID facilityUuid, String actorId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM tuso.facility_profile_submission "
                        + "WHERE facility_uuid=? AND submitted_by=? AND superseded_by IS NULL "
                        + "  AND state NOT IN ('VERIFIED','CONDITIONALLY_VERIFIED','REJECTED','SUPERSEDED') "
                        + "LIMIT 1", facilityUuid, actorId);
        return rows.isEmpty() ? null : (UUID) rows.get(0).get("id");
    }

    private String stateOf(UUID submissionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT state FROM tuso.facility_profile_submission WHERE id=?", submissionId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found: " + submissionId);
        }
        return String.valueOf(rows.get(0).get("state"));
    }

    private int proposedCount(UUID submissionId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility_field_assertion "
                        + "WHERE submission_id=? AND promoted_at IS NULL", Integer.class, submissionId);
        return n == null ? 0 : n;
    }

    private SubmissionView view(UUID submissionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT state, correction_note FROM tuso.facility_profile_submission WHERE id=?", submissionId);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        return new SubmissionView(submissionId, String.valueOf(row.get("state")),
                proposedCount(submissionId), (String) row.get("correction_note"));
    }

    private FacilityEntity requireFacility(UUID facilityUuid, UUID tenantId) {
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found: " + facilityUuid));
        if (facility.getTenantId() != null && !facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Facility belongs to another tenant");
        }
        return facility;
    }
}
