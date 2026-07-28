package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TreatmentRegimenEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProgrammeEnrolmentRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TreatmentRegimenRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HIV and TB as governed longitudinal programmes on the existing clinical spine.
 *
 * <p>PCT owns the Care Continuum, so programme membership and its lifecycle belong here. The five
 * safety properties of this pack are enforced structurally: an enrolment anchors to the one problem
 * row rather than forking a second diagnosis (one disease, one entry); an exit must name its reason
 * (unfinished never reads as clean); a duplicate active enrolment is refused (the partial-unique
 * constraint); a regimen change is a new row keeping the treatment history intact; and a stage that
 * does not belong to the programme is refused rather than silently stored.</p>
 *
 * <p><strong>Confidentiality.</strong> HIV is specially protected and TB is not. Whether an
 * enrolment is confidential is derived from its programme and, at the code level, from the anchor
 * problem's ICD code as classified by zibo's governed map — there is no second confidentiality flag
 * to drift. The SPECIALLY_PROTECTED enforcement seam ships in SHADOW, so this service does not stamp
 * a protection class it cannot honour; it exposes {@code confidential} so the read path can route
 * HIV data through the confidential lane, and the ENFORCE gap is stated in the pack deliverable.</p>
 */
@Service
public class ProgrammeEnrolmentService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeEnrolmentService.class);

    private final ProgrammeEnrolmentRepository enrolmentRepository;
    private final TreatmentRegimenRepository regimenRepository;
    private final ProblemRepository problemRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public ProgrammeEnrolmentService(ProgrammeEnrolmentRepository enrolmentRepository,
                                     TreatmentRegimenRepository regimenRepository,
                                     ProblemRepository problemRepository,
                                     EventOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper,
                                     ClinicalAccessGuard accessGuard) {
        this.enrolmentRepository = enrolmentRepository;
        this.regimenRepository = regimenRepository;
        this.problemRepository = problemRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    // ── Enrolment reads ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProgrammeEnrolmentEntity> list(String subjectCpid, boolean openOnly) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return openOnly
                ? enrolmentRepository.findByTenantIdAndSubjectCpidAndStatusInOrderByEnrolledOnDesc(
                        tenantId, subjectCpid, ProgrammeVocabulary.OPEN_ENROLMENT_STATUSES)
                : enrolmentRepository.findByTenantIdAndSubjectCpidOrderByEnrolledOnDesc(tenantId, subjectCpid);
    }

    @Transactional(readOnly = true)
    public ProgrammeEnrolmentEntity get(UUID enrolmentId) {
        return requireEnrolment(enrolmentId, TrustContextHolder.require());
    }

    /**
     * Programme cohort counts for the tenant, optionally scoped to a facility.
     *
     * <p>Counts ENROLMENTS, not people: someone on both HIV care and TB treatment appears in both
     * cohorts. That is the correct reading for programme reporting, and the response says so
     * explicitly, so nobody totals the rows into a patient count.</p>
     *
     * <p>Lives here rather than as a reporting-service report definition because reporting holds a
     * SEPARATE DATABASE and cannot read {@code pct.*} at all — a seeded SQL template naming these
     * tables would be registered, ACTIVE and unable to run.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> cohortCounts(String facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Object[]> rows = enrolmentRepository.cohortCounts(ctx.tenantId(), facilityId);
        List<Map<String, Object>> counts = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("programme", r[0]);
            row.put("status", r[1]);
            row.put("enrolments", ((Number) r[2]).longValue());
            counts.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("counts_enrolments_not_people",
                "A person enrolled in more than one programme is counted in each. Do not total "
                + "these rows into a patient count.");
        return out;
    }

    @Transactional(readOnly = true)
    public List<TreatmentRegimenEntity> regimensOf(UUID enrolmentId) {
        TrustContext ctx = TrustContextHolder.require();
        requireEnrolment(enrolmentId, ctx);
        return regimenRepository.findByTenantIdAndEnrolmentIdOrderByStartedOnDesc(ctx.tenantId(), enrolmentId);
    }

    // ── Enrolment writes ─────────────────────────────────────────────────────────

    /**
     * Enrols a patient in a programme, anchored to an existing problem.
     *
     * <p>The anchor problem must already exist and belong to this patient — creating one here would
     * be a second write path into the problem list, bypassing the W1 duplicate guard, which is the
     * exact fork "one disease, one entry" exists to prevent. A duplicate active enrolment in the same
     * programme is refused by the database (partial-unique) and reported as a clear conflict.</p>
     */
    @Transactional
    public ProgrammeEnrolmentEntity enrol(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();

        String subjectCpid = required(body, "subject_cpid", "subjectCpid");
        accessGuard.requireCareRelationship(ctx, subjectCpid,
                str(body.get("journey_id"), body.get("journeyId")),
                str(body.get("encounter_id"), body.get("encounterId")));

        // Idempotent replay of a field capture.
        String offlineId = str(body.get("client_offline_id"), body.get("clientOfflineId"));
        if (offlineId != null) {
            Optional<ProgrammeEnrolmentEntity> existing =
                    enrolmentRepository.findByTenantIdAndClientOfflineId(ctx.tenantId(), offlineId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String programme = ProblemVocabulary.require("programme",
                str(body.get("programme")), ProgrammeVocabulary.PROGRAMMES, true);

        UUID anchorProblemId = UUID.fromString(required(body, "anchor_problem_id", "anchorProblemId"));
        ProblemEntity anchor = problemRepository.findById(anchorProblemId)
                .orElseThrow(() -> new IllegalArgumentException("Anchor problem not found: " + anchorProblemId));
        if (!anchor.getTenantId().equals(ctx.tenantId())
                || !anchor.getSubjectCpid().equals(subjectCpid)) {
            // Anchoring a programme to another patient's diagnosis would silently attribute one
            // person's HIV or TB to another's record.
            throw new IllegalArgumentException(
                    "Anchor problem " + anchorProblemId + " does not belong to patient " + subjectCpid);
        }

        // One active enrolment per programme. Checked before the write for a readable conflict, and
        // still backstopped by the database's partial-unique index against a concurrent second write.
        enrolmentRepository.findFirstByTenantIdAndSubjectCpidAndProgrammeAndStatusNot(
                ctx.tenantId(), subjectCpid, programme, "EXITED").ifPresent(active -> {
            throw new DuplicateEnrolmentException(programme, active.getEnrolmentId());
        });

        ProgrammeEnrolmentEntity e = new ProgrammeEnrolmentEntity();
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setProgramme(programme);
        e.setAnchorProblemId(anchorProblemId);
        e.setStatus(ProblemVocabulary.require("status",
                body.getOrDefault("status", "SCREENING"), ProgrammeVocabulary.ENROLMENT_STATUSES, true));
        e.setProgrammeNumber(str(body.get("programme_number"), body.get("programmeNumber")));
        e.setManagingFacilityId(str(body.get("managing_facility_id"), body.get("managingFacilityId")));
        e.setResponsibleActor(str(body.get("responsible_actor"), body.get("responsibleActor")));
        e.setNotes(str(body.get("notes")));
        e.setClientOfflineId(offlineId);
        e.setCreatedBy(ctx.actorId());

        String episodeId = str(body.get("episode_id"), body.get("episodeId"));
        if (episodeId != null) {
            e.setEpisodeId(UUID.fromString(episodeId));
        }

        // PMTCT seam: a read-only soft link to the RMNP pregnancy episode this enrolment relates to.
        // We store the id only — this pack never writes back to the pregnancy episode.
        String pregnancyEpisodeId = str(body.get("pregnancy_episode_id"), body.get("pregnancyEpisodeId"));
        if (pregnancyEpisodeId != null) {
            e.setPregnancyEpisodeId(UUID.fromString(pregnancyEpisodeId));
        }

        String enrolledOn = str(body.get("enrolled_on"), body.get("enrolledOn"));
        e.setEnrolledOn(enrolledOn == null ? LocalDate.now() : LocalDate.parse(enrolledOn));

        // An enrolment cannot be opened already EXITED — that state needs a reason and a date and is
        // reached by exiting, not by enrolling.
        if (ProgrammeVocabulary.CLOSED_ENROLMENT_STATUSES.contains(e.getStatus())) {
            throw new IllegalArgumentException(
                    "An enrolment cannot start in status " + e.getStatus() + ". Enrol, then exit.");
        }

        e = enrolmentRepository.save(e);
        emitEnrolment("PROGRAMME_ENROLMENT_OPENED", e);
        log.info("pct.programme_enrolment.opened id={} subject={} programme={} status={} by={} correlationId={}",
                e.getEnrolmentId(), e.getSubjectCpid(), e.getProgramme(), e.getStatus(),
                ctx.actorId(), ctx.correlationId());
        return e;
    }

    /** Advances the programme lifecycle (SCREENING → ENROLLED → ON_TREATMENT → TREATMENT_INTERRUPTED). */
    @Transactional
    public ProgrammeEnrolmentEntity updateStatus(UUID enrolmentId, String requestedStatus) {
        TrustContext ctx = TrustContextHolder.require();
        ProgrammeEnrolmentEntity e = requireEnrolment(enrolmentId, ctx);

        String status = ProblemVocabulary.require("status", requestedStatus,
                ProgrammeVocabulary.ENROLMENT_STATUSES, true);
        if (ProgrammeVocabulary.CLOSED_ENROLMENT_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Use exit to close an enrolment; " + status + " requires a reason and date.");
        }
        if ("EXITED".equals(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Enrolment " + enrolmentId + " has exited; re-enrol rather than reviving it.");
        }
        e.setStatus(status);
        e = enrolmentRepository.save(e);
        emitEnrolment("PROGRAMME_ENROLMENT_STATUS_CHANGED", e);
        log.info("pct.programme_enrolment.status id={} subject={} status={} by={}",
                e.getEnrolmentId(), e.getSubjectCpid(), status, ctx.actorId());
        return e;
    }

    /**
     * Exits the programme.
     *
     * <p>The exit reason is required, never defaulted. "Cured", "completed", "transferred out",
     * "lost to follow-up" and "died" are entirely different outcomes that all leave status EXITED —
     * defaulting would report a patient who stopped attending as a treatment success, which is the
     * programme statistic this pack most needs to get right.</p>
     */
    @Transactional
    public ProgrammeEnrolmentEntity exit(UUID enrolmentId, String requestedReason, String exitOn) {
        TrustContext ctx = TrustContextHolder.require();
        ProgrammeEnrolmentEntity e = requireEnrolment(enrolmentId, ctx);

        if ("EXITED".equals(e.getStatus())) {
            throw new IllegalArgumentException("Enrolment " + enrolmentId + " has already exited.");
        }
        String reason = ProblemVocabulary.require("exit_reason", requestedReason,
                ProgrammeVocabulary.EXIT_REASONS, true);

        LocalDate exitDate = exitOn == null ? LocalDate.now() : LocalDate.parse(exitOn);
        if (exitDate.isBefore(e.getEnrolledOn())) {
            throw new IllegalArgumentException(
                    "An enrolment cannot exit (" + exitDate + ") before it began (" + e.getEnrolledOn() + ").");
        }

        // Close any open regimen alongside the enrolment, so a treatment history does not leave a
        // regimen looking current after the patient has left the programme.
        regimenRepository.findByTenantIdAndEnrolmentIdAndEndedOnIsNull(ctx.tenantId(), enrolmentId)
                .ifPresent(open -> {
                    open.setEndedOn(exitDate);
                    open.setChangeReason(regimenEndReasonForExit(reason));
                    regimenRepository.save(open);
                });

        e.setStatus("EXITED");
        e.setExitReason(reason);
        e.setExitOn(exitDate);
        e = enrolmentRepository.save(e);

        emitEnrolment("PROGRAMME_ENROLMENT_EXITED", e);
        log.info("pct.programme_enrolment.exited id={} subject={} reason={} by={} correlationId={}",
                e.getEnrolmentId(), e.getSubjectCpid(), reason, ctx.actorId(), ctx.correlationId());
        return e;
    }

    // ── Regimen writes ───────────────────────────────────────────────────────────

    /**
     * Starts a regimen on an enrolment, ending the current one if there is a switch.
     *
     * <p>A regimen change is a new row: the prior regimen is closed with a change reason and the new
     * one opened, so the treatment history is the sequence of what the patient was on and why each
     * ended. There is at most one open regimen per enrolment, enforced by the database.</p>
     */
    @Transactional
    public TreatmentRegimenEntity startRegimen(UUID enrolmentId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ProgrammeEnrolmentEntity enrolment = requireEnrolment(enrolmentId, ctx);
        if ("EXITED".equals(enrolment.getStatus())) {
            throw new IllegalArgumentException(
                    "Enrolment " + enrolmentId + " has exited; a regimen cannot be started on it.");
        }

        String stage = ProblemVocabulary.require("regimen_stage",
                str(body.get("regimen_stage"), body.get("regimenStage")),
                ProgrammeVocabulary.REGIMEN_STAGES, true);
        // An ART line on a TB enrolment (or a TB phase on HIV care) is a category error the shared
        // stage column cannot catch on its own.
        ProgrammeVocabulary.requireStageForProgramme(enrolment.getProgramme(), stage);

        LocalDate startedOn = parseDateOrToday(str(body.get("started_on"), body.get("startedOn")));

        // Close the current regimen if this is a switch. The reason the prior regimen ended is the
        // caller's supplied change reason; a first regimen simply has none to close.
        Optional<TreatmentRegimenEntity> current =
                regimenRepository.findByTenantIdAndEnrolmentIdAndEndedOnIsNull(ctx.tenantId(), enrolmentId);
        if (current.isPresent()) {
            String changeReason = ProblemVocabulary.require("change_reason",
                    str(body.get("change_reason"), body.get("changeReason")),
                    ProgrammeVocabulary.CHANGE_REASONS, true);
            TreatmentRegimenEntity prior = current.get();
            LocalDate priorEnd = startedOn.isBefore(prior.getStartedOn()) ? prior.getStartedOn() : startedOn;
            prior.setEndedOn(priorEnd);
            prior.setChangeReason(changeReason);
            regimenRepository.save(prior);
        }

        TreatmentRegimenEntity r = new TreatmentRegimenEntity();
        r.setTenantId(ctx.tenantId());
        r.setEnrolmentId(enrolmentId);
        r.setSubjectCpid(enrolment.getSubjectCpid());
        r.setRegimenCode(required(body, "regimen_code", "regimenCode"));
        r.setRegimenDisplay(str(body.get("regimen_display"), body.get("regimenDisplay")));
        r.setRegimenStage(stage);
        r.setStartedOn(startedOn);
        r.setNotes(str(body.get("notes")));
        r.setRecordedBy(ctx.actorId());
        r = regimenRepository.save(r);

        emitRegimen("PROGRAMME_REGIMEN_STARTED", enrolment, r);
        log.info("pct.programme_regimen.started id={} enrolment={} code={} stage={} by={}",
                r.getRegimenId(), enrolmentId, r.getRegimenCode(), stage, ctx.actorId());
        return r;
    }

    private ProgrammeEnrolmentEntity requireEnrolment(UUID enrolmentId, TrustContext ctx) {
        ProgrammeEnrolmentEntity e = enrolmentRepository.findById(enrolmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrolment not found: " + enrolmentId));
        if (!e.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Enrolment not found: " + enrolmentId);
        }
        return e;
    }

    /** Maps a programme exit to the reason a still-open regimen ended, so neither is left dangling. */
    private static String regimenEndReasonForExit(String exitReason) {
        return switch (exitReason) {
            case "TREATMENT_COMPLETED", "CURED" -> "COMPLETED";
            case "TREATMENT_FAILED" -> "TREATMENT_FAILURE";
            default -> "PATIENT_PREFERENCE"; // transferred out / LTFU / died / stopped — the drug simply stops
        };
    }

    private void emitEnrolment(String eventType, ProgrammeEnrolmentEntity e) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enrolmentId", e.getEnrolmentId().toString());
        payload.put("subjectCpid", e.getSubjectCpid());
        payload.put("programme", e.getProgramme());
        payload.put("status", e.getStatus());
        payload.put("exitReason", e.getExitReason());
        payload.put("anchorProblemId", e.getAnchorProblemId().toString());
        payload.put("confidential", ProgrammeVocabulary.isConfidential(e.getProgramme()));
        payload.put("actorId", ctx.actorId());
        outbox("PROGRAMME_ENROLMENT", e.getEnrolmentId().toString(), eventType, payload, ctx);
    }

    private void emitRegimen(String eventType, ProgrammeEnrolmentEntity enrolment, TreatmentRegimenEntity r) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("regimenId", r.getRegimenId().toString());
        payload.put("enrolmentId", r.getEnrolmentId().toString());
        payload.put("subjectCpid", r.getSubjectCpid());
        payload.put("programme", enrolment.getProgramme());
        payload.put("regimenCode", r.getRegimenCode());
        payload.put("regimenStage", r.getRegimenStage());
        payload.put("confidential", ProgrammeVocabulary.isConfidential(enrolment.getProgramme()));
        payload.put("actorId", ctx.actorId());
        outbox("PROGRAMME_REGIMEN", r.getRegimenId().toString(), eventType, payload, ctx);
    }

    private void outbox(String aggregateType, String aggregateId, String eventType,
                        Map<String, Object> payload, TrustContext ctx) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private static LocalDate parseDateOrToday(String value) {
        return value == null ? LocalDate.now() : LocalDate.parse(value);
    }

    private static String required(Map<String, Object> body, String snakeKey, String camelKey) {
        String v = str(body.get(snakeKey), body.get(camelKey));
        if (v == null) {
            throw new IllegalArgumentException("Missing field: " + snakeKey);
        }
        return v;
    }

    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise programme payload: {}", ex.getMessage());
            return "{}";
        }
    }
}
