package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.integration.ClinicalKnowledgeIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamVisitEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImamEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImamVisitRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * System of record for integrated management of acute malnutrition (IMAM) episodes.
 *
 * <p>This closes the gap the IMNCI classification engine had been living with since Wave 4. The
 * engine tells a clinician to enrol a severely malnourished child in outpatient therapeutic care
 * and review them weekly; until now there was no episode to enrol them into, no schedule to hold
 * them to and no discharge to reach. An instruction a system cannot carry out is worse than no
 * instruction, because every layer reports success and nothing lands.</p>
 *
 * <h2>Where the clinical judgement lives</h2>
 * <p>Not here. Which programme admits which child, what discharges them, how many missed reviews
 * make a defaulter and how much therapeutic food they are owed are all governed content in the
 * clinical knowledge platform. This service owns the record: the episode, the reviews, the outcome,
 * and the evidence that the criteria were or were not met when the episode closed.</p>
 *
 * <h2>Three properties held structurally</h2>
 * <ul>
 *   <li><strong>A cure is never certified on criteria that were not met.</strong> Closing an episode
 *       as CURED requires either an assessment saying the discharge criteria are met, or an explicit
 *       override reason from the clinician. A cure recorded without either is a false statistic and,
 *       worse, a false reassurance on the child's record.</li>
 *   <li><strong>A degraded assessment is never a negative finding.</strong> If the knowledge platform
 *       is unreachable the review is still recorded, with {@code discharge_eligible} left null and
 *       the reason stored. Null means nobody evaluated it; it must never read as "not ready".</li>
 *   <li><strong>One open episode per child.</strong> Two would split the review history, and the
 *       criterion that matters — sustained across two consecutive visits — would then be evaluable
 *       from neither.</li>
 * </ul>
 */
@Service
public class ImamService {

    private static final Logger log = LoggerFactory.getLogger(ImamService.class);

    /** Outcomes the programme recognises. Anything else would make the caseload unreconcilable. */
    private static final Set<String> OUTCOMES = Set.of(
            "CURED", "DEFAULTED", "DIED", "NON_RESPONDER", "TRANSFERRED_OUT", "MEDICAL_TRANSFER");

    private final ImamEpisodeRepository episodeRepository;
    private final ImamVisitRepository visitRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;
    private final ClinicalKnowledgeIntegration knowledge;

    public ImamService(ImamEpisodeRepository episodeRepository,
                       ImamVisitRepository visitRepository,
                       EventOutboxRepository outboxRepository,
                       ObjectMapper objectMapper,
                       ClinicalAccessGuard accessGuard,
                       ClinicalKnowledgeIntegration knowledge) {
        this.episodeRepository = episodeRepository;
        this.visitRepository = visitRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
        this.knowledge = knowledge;
    }

    // ── reads ───────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ImamEpisodeEntity> list(String subjectCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (subjectCpid == null || subjectCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        return episodeRepository.findByTenantIdAndSubjectCpidOrderByEnrolledAtDesc(tenantId, subjectCpid.trim());
    }

    @Transactional(readOnly = true)
    public ImamEpisodeEntity get(UUID episodeId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return episodeRepository.findByTenantIdAndImamEpisodeId(tenantId, episodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IMAM episode not found"));
    }

    @Transactional(readOnly = true)
    public List<ImamVisitEntity> visits(UUID episodeId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return visitRepository.findByTenantIdAndImamEpisodeIdOrderByVisitNumberAsc(tenantId, episodeId);
    }

    /**
     * The live assessment for one episode: discharge readiness, attendance, response and the ration
     * due. Read-time rather than stored, because attendance changes with the calendar even when
     * nothing about the record has changed — a child becomes a defaulter by the passage of time, not
     * by anyone writing something down.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> assess(UUID episodeId, LocalDate asOf) {
        ImamEpisodeEntity episode = get(episodeId);
        List<ImamVisitEntity> visits = visits(episodeId);
        Map<String, Object> assessment = evaluate(episode, visits, asOf);
        if (assessment == null) {
            return unavailableAssessment();
        }
        return assessment;
    }

    // ── enrolment ────────────────────────────────────────────────────────────────────────────────

    /**
     * Opens an episode. The programme is resolved from the governed content — either from the IMNCI
     * classification the assessment produced, or from the anthropometry — so the same rules that
     * classified the child admit them.
     */
    @Transactional
    public ImamEpisodeEntity enrol(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "an enrolment body is required");
        }

        String subject = str(body.get("subject_cpid"), body.get("subjectCpid"),
                body.get("patient_id"), body.get("patientId"));
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subject, journeyId, encounterId);

        episodeRepository.findByTenantIdAndSubjectCpidAndStatus(ctx.tenantId(), subject, "ACTIVE")
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This child is already enrolled in an open IMAM episode (" + existing.getImamEpisodeId()
                                    + ", programme " + existing.getProgramme() + "). Record the review against it, "
                                    + "or close it with an outcome before opening another — two open episodes split "
                                    + "the review history and make the discharge criteria unevaluable.");
                });

        String classification = str(body.get("source_classification"), body.get("sourceClassification"),
                body.get("classification"));
        BigDecimal muac = decimal(body.get("admission_muac_cm"), body.get("muac_cm"), body.get("muacCm"));
        String oedema = upperOr(str(body.get("admission_oedema"), body.get("oedema")), "NOT_ASSESSED");
        String appetite = upper(str(body.get("admission_appetite_test"), body.get("appetite_test")));
        BigDecimal wfhZ = decimal(body.get("admission_weight_for_height_z"), body.get("weight_for_height_z"));
        Integer ageDays = integer(body.get("admission_age_days"), body.get("age_days"), body.get("ageDays"));
        Boolean complication = bool(body.get("medical_complication"), body.get("medicalComplication"));

        Map<String, Object> eligibility = knowledge.imamEligibility(ctx.tenantId(),
                ClinicalKnowledgeIntegration.eligibilityFacts(
                        ageDays, muac, oedema, appetite, wfhZ, complication, classification));

        String requestedProgramme = upper(str(body.get("programme")));
        String programme = requestedProgramme;
        if (programme == null) {
            if (eligibility == null) {
                // Guessing a programme would guess a ration, a review interval and a discharge
                // criterion. Saying so is the only honest answer.
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The clinical knowledge platform is unreachable, so the treatment programme cannot be "
                                + "resolved from the child's measurements. Retry, or enrol with an explicit "
                                + "programme chosen by the clinician.");
            }
            if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        Boolean.FALSE.equals(eligibility.get("assessable"))
                                ? "Nutrition status could not be assessed: " + eligibility.get("not_assessable_reason")
                                : "No IMAM admission criterion is met on the measurements supplied: "
                                  + eligibility.get("rationale"));
            }
            programme = String.valueOf(eligibility.get("programme"));
        }

        OffsetDateTime enrolledAt = timestamp(str(body.get("enrolled_at"), body.get("enrolledAt")));

        ImamEpisodeEntity episode = new ImamEpisodeEntity();
        episode.setImamEpisodeId(UUID.randomUUID());
        episode.setTenantId(ctx.tenantId());
        episode.setSubjectCpid(subject);
        episode.setJourneyId(journeyId);
        episode.setEncounterId(encounterId);
        episode.setFacilityId(uuid(body.get("facility_id"), body.get("facilityId")));
        episode.setProgramme(programme);
        episode.setStatus("ACTIVE");
        episode.setEnrolledAt(enrolledAt);
        episode.setEnrolledBy(requireAuthor(ctx, str(body.get("enrolled_by"), body.get("recorded_by"))));
        episode.setEnrolmentSource(upperOr(str(body.get("enrolment_source"), body.get("enrolmentSource")),
                classification != null ? "IMNCI_CLASSIFICATION" : "DIRECT"));
        episode.setSourceClassification(classification);
        episode.setSourceRef(str(body.get("source_ref"), body.get("sourceRef")));

        episode.setAdmissionAgeDays(ageDays);
        episode.setAdmissionMuacCm(muac);
        episode.setAdmissionWeightKg(decimal(body.get("admission_weight_kg"), body.get("weight_kg")));
        episode.setAdmissionHeightCm(decimal(body.get("admission_height_cm"), body.get("height_cm")));
        episode.setAdmissionWeightForHeightZ(wfhZ);
        episode.setAdmissionOedema(oedema);
        episode.setAdmissionAppetiteTest(appetite);
        episode.setAdmissionComplications(str(body.get("admission_complications"), body.get("complications")));

        if (eligibility != null) {
            episode.setAdmissionCriterion(str(eligibility.get("admission_criterion")));
            episode.setContentPackId(str(eligibility.get("content_pack_id")));
            episode.setContentVersion(str(eligibility.get("content_version")));
            episode.setApprovalStatus(str(eligibility.get("approval_status")));
            episode.setReviewIntervalDays(intOr(eligibility.get("review_interval_days"), 7));
            episode.setAttendanceGraceDays(intOr(eligibility.get("attendance_grace_days"), 0));
            episode.setTraceAfterMissedVisits(intOr(eligibility.get("trace_after_missed_visits"), 1));
            episode.setDefaulterMissedVisits(intOr(eligibility.get("defaulter_missed_visits"), 2));
        } else {
            // A clinician-chosen programme with the platform down. The schedule falls back to the
            // column defaults and the episode says so, rather than pretending the pack was consulted.
            episode.setAdmissionCriterion("CLINICIAN_DECISION");
            episode.setApprovalStatus("UNVERIFIED_CONTENT_UNAVAILABLE");
        }

        episode.setLastContactOn(enrolledAt.toLocalDate());
        episode.setNextReviewDue(enrolledAt.toLocalDate().plusDays(episode.getReviewIntervalDays()));

        episode = episodeRepository.save(episode);
        emit(episode, "IMAM_EPISODE_ENROLLED", Map.of(
                "programme", episode.getProgramme(),
                "enrolmentSource", episode.getEnrolmentSource(),
                "sourceClassification", nullSafe(episode.getSourceClassification()),
                "admissionCriterion", nullSafe(episode.getAdmissionCriterion())));

        log.info("pct.imam.enrolled id={} subject={} programme={} criterion={} source={} by={} correlationId={}",
                episode.getImamEpisodeId(), subject, episode.getProgramme(), episode.getAdmissionCriterion(),
                episode.getEnrolmentSource(), ctx.actorId(), ctx.correlationId());
        return episode;
    }

    // ── reviews ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Records a scheduled review — attended or missed. A missed review is a row, because absence
     * recorded as the absence of a row is indistinguishable from a review that is not yet due.
     */
    @Transactional
    public ImamVisitEntity recordVisit(UUID episodeId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ImamEpisodeEntity episode = get(episodeId);
        if (!"ACTIVE".equals(episode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This IMAM episode is closed (" + episode.getOutcome() + "). Reopen it or enrol a new "
                            + "episode before recording a review.");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        accessGuard.requireCareRelationship(ctx, episode.getSubjectCpid(),
                firstNonBlank(str(in.get("journey_id")), episode.getJourneyId()),
                firstNonBlank(str(in.get("encounter_id")), episode.getEncounterId()));

        Boolean attendedRaw = bool(in.get("attended"));
        boolean attended = attendedRaw == null || attendedRaw;

        List<ImamVisitEntity> existing = visits(episodeId);
        int nextNumber = existing.stream()
                .map(ImamVisitEntity::getVisitNumber)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;

        ImamVisitEntity visit = new ImamVisitEntity();
        visit.setImamVisitId(UUID.randomUUID());
        visit.setImamEpisodeId(episodeId);
        visit.setTenantId(ctx.tenantId());
        visit.setSubjectCpid(episode.getSubjectCpid());
        visit.setEncounterId(firstNonBlank(str(in.get("encounter_id")), episode.getEncounterId()));
        visit.setFacilityId(firstNonNull(uuid(in.get("facility_id")), episode.getFacilityId()));
        visit.setVisitNumber(nextNumber);
        visit.setScheduledFor(date(str(in.get("scheduled_for"), in.get("scheduledFor"))));
        visit.setAttended(attended);
        visit.setRecordedBy(requireAuthor(ctx, str(in.get("recorded_by"))));

        if (attended) {
            visit.setVisitDate(timestamp(str(in.get("visit_date"), in.get("visitDate"))));
            visit.setMuacCm(decimal(in.get("muac_cm"), in.get("muacCm")));
            visit.setWeightKg(decimal(in.get("weight_kg"), in.get("weightKg")));
            visit.setHeightCm(decimal(in.get("height_cm"), in.get("heightCm")));
            visit.setWeightForHeightZ(decimal(in.get("weight_for_height_z")));
            visit.setOedema(upperOr(str(in.get("oedema")), "NOT_ASSESSED"));
            visit.setAppetiteTest(upper(str(in.get("appetite_test"), in.get("appetiteTest"))));
            visit.setDangerSignsPresent(bool(in.get("danger_signs_present"), in.get("dangerSignsPresent")));
            visit.setRutfProductCode(upper(str(in.get("rutf_product_code"), in.get("product_code"))));
            visit.setRutfSachetsIssued(decimal(in.get("rutf_sachets_issued"), in.get("sachets_issued")));
        } else {
            // The database CHECK enforces this too, but rejecting it here says why: a visit that did
            // not happen must not carry a date that makes the child look seen.
            if (str(in.get("visit_date"), in.get("visitDate")) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A missed review cannot carry a visit date.");
            }
            visit.setOedema("NOT_ASSESSED");
        }
        visit.setClinicalNote(str(in.get("clinical_note"), in.get("note")));

        // Evaluate against the governed protocol with this review included, so what is stamped on
        // the row is the state of the episode as of this contact.
        List<ImamVisitEntity> withNew = new ArrayList<>(existing);
        withNew.add(visit);
        LocalDate asOf = attended && visit.getVisitDate() != null
                ? visit.getVisitDate().toLocalDate() : LocalDate.now();
        Map<String, Object> assessment = evaluate(episode, withNew, asOf);

        if (assessment != null) {
            visit.setDischargeEligible(bool(assessment.get("discharge_eligible")));
            visit.setAttendanceStatus(str(assessment.get("attendance_status")));
            visit.setResponseStatus(str(assessment.get("response_status")));
            visit.setAssessmentContentVersion(str(assessment.get("content_version")));
            visit.setAssessmentNote(joinActions(assessment.get("actions")));
            visit.setRutfSachetsRecommended(decimal(assessment.get("recommended_sachets_per_week")));
            if (visit.getRutfProductCode() == null) {
                visit.setRutfProductCode(str(assessment.get("therapeutic_food")));
            }
            LocalDate due = date(str(assessment.get("next_review_due")));
            visit.setNextReviewDue(due);
        } else {
            // Never store the unavailable verdict as a negative. discharge_eligible stays null.
            visit.setAssessmentNote("The clinical knowledge platform could not be reached, so discharge "
                    + "readiness, attendance status and the ration were not evaluated at this review. This is "
                    + "an absence of assessment, not a finding about the child.");
        }
        if (visit.getNextReviewDue() == null) {
            LocalDate base = attended && visit.getVisitDate() != null
                    ? visit.getVisitDate().toLocalDate() : episode.getNextReviewDue();
            if (base != null) {
                visit.setNextReviewDue(base.plusDays(episode.getReviewIntervalDays()));
            }
        }

        visit = visitRepository.save(visit);

        if (attended && visit.getVisitDate() != null) {
            episode.setLastContactOn(visit.getVisitDate().toLocalDate());
        }
        episode.setNextReviewDue(visit.getNextReviewDue());
        episode.setUpdatedAt(OffsetDateTime.now());
        episodeRepository.save(episode);

        emit(episode, "IMAM_VISIT_RECORDED", Map.of(
                "imamVisitId", visit.getImamVisitId().toString(),
                "visitNumber", visit.getVisitNumber(),
                "attended", visit.isAttended(),
                "muacCm", nullSafe(visit.getMuacCm()),
                "dischargeEligible", nullSafe(visit.getDischargeEligible()),
                "attendanceStatus", nullSafe(visit.getAttendanceStatus())));

        // Tracing is a separate event because it is acted on by a different team from the one that
        // recorded the review, and often on a different day.
        if (assessment != null && Boolean.TRUE.equals(assessment.get("tracing_required"))) {
            emit(episode, "IMAM_TRACING_REQUIRED", Map.of(
                    "attendanceStatus", nullSafe(assessment.get("attendance_status")),
                    "consecutiveMissedVisits", nullSafe(assessment.get("consecutive_missed_visits")),
                    "daysOverdue", nullSafe(assessment.get("days_overdue")),
                    "nextReviewDue", nullSafe(assessment.get("next_review_due"))));
        }

        log.info("pct.imam.visit id={} episode={} n={} attended={} muac={} dischargeEligible={} by={} "
                        + "correlationId={}",
                visit.getImamVisitId(), episodeId, visit.getVisitNumber(), attended, visit.getMuacCm(),
                visit.getDischargeEligible(), ctx.actorId(), ctx.correlationId());
        return visit;
    }

    // ── closing the episode ──────────────────────────────────────────────────────────────────────

    /**
     * Closes the episode with an outcome.
     *
     * <p>CURED is the only outcome the system holds to evidence. A clinician may still discharge a
     * child who has not met the criteria — there are good reasons to, and blocking it would be
     * blocking care — but they must say why, and the reason is stored on the episode. What the
     * system will not do is quietly certify a cure it cannot justify.</p>
     */
    @Transactional
    public ImamEpisodeEntity closeOutcome(UUID episodeId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ImamEpisodeEntity episode = get(episodeId);
        if (!"ACTIVE".equals(episode.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This IMAM episode is already closed with outcome " + episode.getOutcome() + ".");
        }
        Map<String, Object> in = body == null ? Map.of() : body;
        accessGuard.requireCareRelationship(ctx, episode.getSubjectCpid(),
                firstNonBlank(str(in.get("journey_id")), episode.getJourneyId()),
                firstNonBlank(str(in.get("encounter_id")), episode.getEncounterId()));

        String outcome = upper(str(in.get("outcome")));
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "outcome must be one of " + OUTCOMES);
        }
        String overrideReason = str(in.get("discharge_override_reason"), in.get("overrideReason"));

        // Evaluate as of the discharge date the clinician states, not as of the moment the record is
        // typed. A discharge backfilled on Monday from Friday's register must be judged against the
        // child as they were on Friday; judging it against today would count the intervening days as
        // a gap in observation and refuse a cure that was properly earned.
        OffsetDateTime outcomeAt = timestamp(str(in.get("outcome_at"), in.get("outcomeAt")));
        List<ImamVisitEntity> visits = visits(episodeId);
        Map<String, Object> assessment = evaluate(episode, visits, outcomeAt.toLocalDate());
        Boolean criteriaMet = assessment == null ? null : bool(assessment.get("discharge_eligible"));
        String criteriaDetail = assessment == null
                ? "The clinical knowledge platform could not be reached, so the discharge criteria were not "
                  + "evaluated at the time this episode was closed."
                : describeCriteria(assessment);

        if ("CURED".equals(outcome) && !Boolean.TRUE.equals(criteriaMet) && isBlank(overrideReason)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    (criteriaMet == null
                            ? "The discharge criteria could not be evaluated"
                            : "The discharge criteria are not met (" + assessment.get("unmet_criteria") + ")")
                            + ", so this episode cannot be recorded as CURED without a stated reason. Supply "
                            + "discharge_override_reason, or record the outcome the child actually had. A cure "
                            + "certified on criteria that were not met is a false statistic and a false "
                            + "reassurance on this child's record.");
        }

        ImamVisitEntity lastAttended = visits.stream().filter(ImamVisitEntity::isAttended)
                .reduce((a, b) -> b).orElse(null);

        episode.setStatus("CLOSED");
        episode.setOutcome(outcome);
        episode.setOutcomeAt(outcomeAt);
        episode.setOutcomeBy(requireAuthor(ctx, str(in.get("outcome_by"), in.get("recorded_by"))));
        episode.setOutcomeNote(str(in.get("outcome_note"), in.get("note")));
        episode.setDischargeMuacCm(firstNonNull(decimal(in.get("discharge_muac_cm"), in.get("muac_cm")),
                lastAttended == null ? null : lastAttended.getMuacCm()));
        episode.setDischargeWeightKg(firstNonNull(decimal(in.get("discharge_weight_kg"), in.get("weight_kg")),
                lastAttended == null ? null : lastAttended.getWeightKg()));
        episode.setDischargeOedema(firstNonBlank(upper(str(in.get("discharge_oedema"), in.get("oedema"))),
                lastAttended == null ? null : lastAttended.getOedema()));
        episode.setDischargeCriteriaMet(criteriaMet);
        episode.setDischargeCriteriaDetail(criteriaDetail);
        episode.setDischargeOverrideReason(overrideReason);
        episode.setUpdatedAt(OffsetDateTime.now());

        episode = episodeRepository.save(episode);

        emit(episode, "IMAM_EPISODE_CLOSED", Map.of(
                "outcome", outcome,
                "dischargeCriteriaMet", nullSafe(criteriaMet),
                "overridden", overrideReason != null,
                "reviewsRecorded", visits.size(),
                "daysInProgramme", ChronoUnit.DAYS.between(episode.getEnrolledAt(), episode.getOutcomeAt())));

        log.info("pct.imam.closed id={} subject={} outcome={} criteriaMet={} overridden={} by={} correlationId={}",
                episodeId, episode.getSubjectCpid(), outcome, criteriaMet, overrideReason != null,
                ctx.actorId(), ctx.correlationId());
        return episode;
    }

    // ── the tracing worklist ─────────────────────────────────────────────────────────────────────

    /**
     * Open episodes whose review has come and gone, banded into those needing a home visit and those
     * meeting the defaulter definition.
     *
     * <p>Evaluated from the schedule stored on the episode rather than by asking the knowledge
     * platform once per child: a worklist that takes a network round-trip per row is a worklist
     * nobody opens. The definition itself is still governed content — it was copied onto the episode
     * from the pack at enrolment, so each child is banded by the rules they were admitted under.</p>
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tracingQueue(UUID facilityId, LocalDate asOf) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        LocalDate today = asOf == null ? LocalDate.now() : asOf;

        List<Map<String, Object>> out = new ArrayList<>();
        for (ImamEpisodeEntity e : episodeRepository.findOverdue(tenantId, facilityId, today)) {
            LocalDate lastContact = e.getLastContactOn() != null
                    ? e.getLastContactOn()
                    : e.getEnrolledAt().toLocalDate();
            int interval = e.getReviewIntervalDays() == null || e.getReviewIntervalDays() < 1
                    ? 7 : e.getReviewIntervalDays();
            int grace = e.getAttendanceGraceDays() == null ? 0 : e.getAttendanceGraceDays();
            int daysSinceLastContact = (int) ChronoUnit.DAYS.between(lastContact, today);
            int missed = Math.max(0, (daysSinceLastContact - grace) / interval);
            if (missed < e.getTraceAfterMissedVisits()) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("imam_episode_id", e.getImamEpisodeId().toString());
            row.put("patient_id", e.getSubjectCpid());
            row.put("subject_cpid", e.getSubjectCpid());
            row.put("programme", e.getProgramme());
            row.put("facility_id", e.getFacilityId() == null ? null : e.getFacilityId().toString());
            row.put("enrolled_at", e.getEnrolledAt().toString());
            row.put("last_contact_on", lastContact.toString());
            row.put("next_review_due", e.getNextReviewDue() == null ? null : e.getNextReviewDue().toString());
            row.put("days_since_last_contact", daysSinceLastContact);
            row.put("consecutive_missed_visits", missed);
            row.put("attendance_status", missed >= e.getDefaulterMissedVisits() ? "DEFAULTER" : "TRACE_REQUIRED");
            row.put("admission_muac_cm", e.getAdmissionMuacCm());
            row.put("admission_oedema", e.getAdmissionOedema());
            // The most urgent row on this list is the child nobody has ever reviewed.
            row.put("never_reviewed", e.getLastContactOn() == null
                    || e.getLastContactOn().equals(e.getEnrolledAt().toLocalDate()));
            out.add(row);
        }
        out.sort((a, b) -> Integer.compare((int) b.get("consecutive_missed_visits"),
                (int) a.get("consecutive_missed_visits")));
        return out;
    }

    // ── knowledge-platform call ──────────────────────────────────────────────────────────────────

    /** @return the assessment, or {@code null} when the knowledge platform could not be reached */
    private Map<String, Object> evaluate(ImamEpisodeEntity episode, List<ImamVisitEntity> visits, LocalDate asOf) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ImamVisitEntity v : visits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("visitNumber", v.getVisitNumber());
            m.put("visitDate", v.getVisitDate() == null ? null : v.getVisitDate().toLocalDate().toString());
            m.put("attended", v.isAttended());
            m.put("muacCm", v.getMuacCm());
            m.put("weightKg", v.getWeightKg());
            m.put("oedema", v.getOedema());
            m.put("appetiteTest", v.getAppetiteTest());
            m.put("dangerSignsPresent", v.getDangerSignsPresent());
            payload.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("programme", episode.getProgramme());
        body.put("admitted_on", episode.getEnrolledAt().toLocalDate().toString());
        body.put("admission_oedema", episode.getAdmissionOedema());
        body.put("as_of", (asOf == null ? LocalDate.now() : asOf).toString());
        body.put("visits", payload);
        return knowledge.imamProgress(episode.getTenantId(), body);
    }

    private static Map<String, Object> unavailableAssessment() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessable", false);
        m.put("not_assessable_reason", "The clinical knowledge platform is unreachable, so discharge "
                + "readiness and attendance could not be evaluated. This is an absence of assessment, not a "
                + "finding about the child.");
        m.put("discharge_eligible", null);
        m.put("attendance_status", "UNKNOWN");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static String describeCriteria(Map<String, Object> assessment) {
        Object criteria = assessment.get("discharge_criteria");
        if (!(criteria instanceof List<?> list) || list.isEmpty()) {
            return String.valueOf(assessment.get("not_assessable_reason"));
        }
        List<String> lines = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> c) {
                Map<String, Object> m = (Map<String, Object>) c;
                lines.add((Boolean.TRUE.equals(m.get("met")) ? "MET " : "NOT MET ")
                        + m.get("code") + ": " + m.get("detail"));
            }
        }
        return String.join("\n", lines);
    }

    private static String joinActions(Object actions) {
        if (actions instanceof List<?> list && !list.isEmpty()) {
            List<String> lines = new ArrayList<>();
            list.forEach(a -> lines.add(String.valueOf(a)));
            return String.join("\n", lines);
        }
        return null;
    }

    // ── events ───────────────────────────────────────────────────────────────────────────────────

    private void emit(ImamEpisodeEntity episode, String eventType, Map<String, Object> extra) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("imamEpisodeId", episode.getImamEpisodeId().toString());
        payload.put("subjectCpid", episode.getSubjectCpid());
        payload.put("journeyId", episode.getJourneyId());
        payload.put("encounterId", episode.getEncounterId());
        payload.put("facilityId", episode.getFacilityId() == null ? null : episode.getFacilityId().toString());
        payload.put("programme", episode.getProgramme());
        payload.put("status", episode.getStatus());
        payload.putAll(extra);
        payload.put("actorId", ctx.actorId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("IMAM_EPISODE");
        outbox.setAggregateId(episode.getImamEpisodeId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialise IMAM event payload: {}", e.getMessage());
            return "{}";
        }
    }

    // ── coercion ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Who is writing this record.
     *
     * <p>A clinical record with no author is not a record — you cannot ask the person who wrote it
     * what they saw, and you cannot tell whether the child was reviewed by a nurse or by a data
     * clerk copying a register. The columns are NOT NULL for that reason, and this resolves the
     * author before the insert so the caller is told what is missing rather than receiving a
     * constraint violation as a 500, which says nothing they can act on.</p>
     *
     * <p>Found by deploying: the trust context carries no actor on an estate that does not enforce
     * bearer authentication, and the first review recorded through the BFF died on the constraint.</p>
     */
    private static String requireAuthor(TrustContext ctx, String... supplied) {
        String author = firstNonBlank(supplied);
        if (author == null) {
            author = ctx.actorId();
        }
        if (author == null || author.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This write has no identifiable author: neither the request nor the trust context "
                            + "names who is recording it. A clinical record must carry the person who "
                            + "made it — supply recorded_by, or call with an actor context.");
        }
        return author;
    }

    private static Object nullSafe(Object value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static OffsetDateTime timestamp(String value) {
        if (value == null) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid timestamp: " + value);
        }
    }

    private static LocalDate date(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof BigDecimal d) {
                return d;
            }
            if (value instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                continue;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid measurement: " + text);
            }
        }
        return null;
    }

    private static Integer integer(Object... values) {
        BigDecimal d = decimal(values);
        return d == null ? null : d.intValue();
    }

    private static int intOr(Object value, int fallback) {
        Integer i = integer(value);
        return i == null ? fallback : i;
    }

    private static Boolean bool(Object... values) {
        for (Object value : values) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value).trim();
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes")) {
                return Boolean.TRUE;
            }
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no")) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static UUID uuid(Object... values) {
        String text = str(values);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String str(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String upperOr(String value, String fallback) {
        String text = upper(value);
        return text != null ? text : fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
