package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConsultationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtDecisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtDecisionProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ConsultationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MdtDecisionProblemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MdtDecisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultation and multidisciplinary decision (brief.md §14).
 *
 * <p><strong>The one property this service exists to hold: asking a question never moves the
 * patient.</strong> {@code owning_service} is set once, at request time, to the requesting service,
 * and there is no code path here that changes it. A responder may recommend a takeover; performing
 * one is a different act with a different record. If an opinion could move ownership, the column
 * would be decorative and §23.10's failure — a patient silently becoming surgical because nobody
 * wrote down who still held them — would be back.</p>
 *
 * <p>The database enforces the same invariants (V114). This layer exists so a clinician gets a 400
 * naming the clinical problem rather than a constraint violation naming a constraint.</p>
 */
@Service
public class ConsultationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationService.class);

    private final ConsultationRepository consultationRepository;
    private final MdtDecisionRepository mdtRepository;
    private final MdtDecisionProblemRepository mdtProblemRepository;
    private final ProblemRepository problemRepository;
    private final ClinicalAccessGuard accessGuard;

    public ConsultationService(ConsultationRepository consultationRepository,
                               MdtDecisionRepository mdtRepository,
                               MdtDecisionProblemRepository mdtProblemRepository,
                               ProblemRepository problemRepository,
                               ClinicalAccessGuard accessGuard) {
        this.consultationRepository = consultationRepository;
        this.mdtRepository = mdtRepository;
        this.mdtProblemRepository = mdtProblemRepository;
        this.problemRepository = problemRepository;
        this.accessGuard = accessGuard;
    }

    // ── Consultation ─────────────────────────────────────────────────────────────

    public static final List<String> OPEN_STATUSES = List.of("REQUESTED", "ACCEPTED");
    private static final List<String> URGENCIES = List.of("ROUTINE", "URGENT", "EMERGENCY");

    @Transactional
    public ConsultationEntity request(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subjectCpid = required(body, "subject_cpid", "subjectCpid");
        String journeyRaw = str(body.get("journey_id"), body.get("journeyId"));
        String encounterRaw = str(body.get("encounter_id"), body.get("encounterId"));
        accessGuard.requireCareRelationship(ctx, subjectCpid, journeyRaw, encounterRaw);

        if (journeyRaw == null && encounterRaw == null) {
            throw new IllegalArgumentException(
                    "A consultation must carry a journey_id or an encounter_id (care-continuum-doctrine CC-5).");
        }

        String question = str(body.get("question"));
        if (question == null || question.isBlank()) {
            // The shape this refuses is the one that loses the patient: a consultation with no
            // question is a referral of the PERSON rather than of a question, and the receiving team
            // reasonably reads it as a handover.
            throw new IllegalArgumentException(
                    "A consultation must ask a question. A consultation with no question is a "
                    + "referral of the patient rather than of a question, and that is the shape that "
                    + "loses ownership.");
        }

        String requestingService = required(body, "requesting_service", "requestingService");
        String askedOf = required(body, "asked_of_service", "askedOfService");
        if (requestingService.equalsIgnoreCase(askedOf)) {
            throw new IllegalArgumentException(
                    "A service cannot consult itself: requesting and asked-of are both " + askedOf + ".");
        }

        ConsultationEntity e = new ConsultationEntity();
        e.setConsultationId(UUID.randomUUID());
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setJourneyId(uuid(journeyRaw));
        e.setEncounterId(uuid(encounterRaw));
        e.setRequestingService(requestingService);
        e.setRequestingActor(ctx.actorId());
        e.setAskedOfService(askedOf);
        e.setQuestion(question);
        e.setClinicalSummary(str(body.get("clinical_summary"), body.get("clinicalSummary")));
        e.setUrgency(ProblemVocabulary.require("urgency",
                str(body.get("urgency")) == null ? "ROUTINE" : str(body.get("urgency")),
                new java.util.LinkedHashSet<>(URGENCIES), true));
        e.setStatus("REQUESTED");
        // Set here and nowhere else. There is deliberately no setter path in this service that
        // changes it after the request.
        e.setOwningService(requestingService);

        consultationRepository.save(e);
        log.info("pct.consultation.requested id={} subject={} from={} to={} owner={} by={} correlationId={}",
                e.getConsultationId(), subjectCpid, requestingService, askedOf, requestingService,
                ctx.actorId(), ctx.correlationId());
        return e;
    }

    /**
     * Answer a consultation. Advice is required; ownership is untouched.
     *
     * <p>An ANSWERED consultation with no advice closes the loop in the responder's worklist while
     * leaving the question unanswered in the record — worse than an open one, because the requester
     * stops waiting.</p>
     */
    @Transactional
    public ConsultationEntity answer(UUID consultationId, Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ConsultationEntity e = require(consultationId, ctx);

        String advice = str(body.get("advice"));
        if (advice == null || advice.isBlank()) {
            throw new IllegalArgumentException(
                    "Answering a consultation requires the advice. An answered consultation with no "
                    + "advice closes the loop in the worklist while leaving the question unanswered "
                    + "in the record.");
        }
        if (!OPEN_STATUSES.contains(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Consultation " + consultationId + " is " + e.getStatus() + " and cannot be answered.");
        }

        String ownerBefore = e.getOwningService();

        e.setStatus("ANSWERED");
        e.setAdvice(advice);
        e.setRespondedBy(ctx.actorId());
        e.setRespondedAt(OffsetDateTime.now());
        e.setTakeoverRecommended(bool(body.get("takeover_recommended"), body.get("takeoverRecommended")));
        consultationRepository.save(e);

        // Asserted rather than assumed. If a future edit adds an ownership write to this method, this
        // throws instead of silently transferring a patient.
        if (!ownerBefore.equals(e.getOwningService())) {
            throw new IllegalStateException(
                    "Answering a consultation changed ownership from " + ownerBefore + " to "
                    + e.getOwningService() + ". A consultation may recommend a takeover; it cannot "
                    + "perform one.");
        }

        log.info("pct.consultation.answered id={} by={} owner={} takeoverRecommended={} correlationId={}",
                consultationId, ctx.actorId(), e.getOwningService(), e.isTakeoverRecommended(),
                ctx.correlationId());
        return e;
    }

    /** Declining is a legitimate answer, and it must say why so the requester knows what to do next. */
    @Transactional
    public ConsultationEntity decline(UUID consultationId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        ConsultationEntity e = require(consultationId, ctx);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Declining a consultation requires a reason, so the requester knows whether to "
                    + "ask again, ask someone else, or escalate.");
        }
        if (!OPEN_STATUSES.contains(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Consultation " + consultationId + " is " + e.getStatus() + " and cannot be declined.");
        }
        e.setStatus("DECLINED");
        e.setDeclineReason(reason);
        e.setRespondedBy(ctx.actorId());
        e.setRespondedAt(OffsetDateTime.now());
        return consultationRepository.save(e);
    }

    @Transactional
    public ConsultationEntity accept(UUID consultationId) {
        TrustContext ctx = TrustContextHolder.require();
        ConsultationEntity e = require(consultationId, ctx);
        if (!"REQUESTED".equals(e.getStatus())) {
            throw new IllegalArgumentException(
                    "Consultation " + consultationId + " is " + e.getStatus() + " and cannot be accepted.");
        }
        e.setStatus("ACCEPTED");
        return consultationRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<ConsultationEntity> listForSubject(String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return consultationRepository.findByTenantIdAndSubjectCpidOrderByRequestedAtDesc(
                ctx.tenantId(), subjectCpid);
    }

    /** The receiving team's worklist — what has been asked of them and is still open. */
    @Transactional(readOnly = true)
    public List<ConsultationEntity> worklist(String service) {
        TrustContext ctx = TrustContextHolder.require();
        return consultationRepository.findByTenantIdAndAskedOfServiceAndStatusInOrderByRequestedAtAsc(
                ctx.tenantId(), service, OPEN_STATUSES);
    }

    private ConsultationEntity require(UUID id, TrustContext ctx) {
        return consultationRepository.findByTenantIdAndConsultationId(ctx.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found: " + id));
    }

    // ── MDT decision ─────────────────────────────────────────────────────────────

    private static final java.util.Set<String> MEETING_TYPES = new java.util.LinkedHashSet<>(List.of(
            "ONCOLOGY", "CANCER_SITE_SPECIFIC", "COMPLEX_MEDICINE", "MORBIDITY_AND_MORTALITY",
            "PALLIATIVE", "TRANSPLANT", "INFECTION", "OTHER"));

    private static final java.util.Set<String> TREATMENT_INTENTS = new java.util.LinkedHashSet<>(List.of(
            "CURATIVE", "LIFE_PROLONGING", "SYMPTOM_DIRECTED", "DIAGNOSTIC", "NOT_SET"));

    /**
     * Record a multidisciplinary decision.
     *
     * <p>Participants, chair and decision are required because an unattributed decision that sets
     * treatment intent is not a governed decision. The problems discussed are linked by id rather
     * than described, so the decision is reachable from the problem list.</p>
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public MdtDecisionEntity recordDecision(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String subjectCpid = required(body, "subject_cpid", "subjectCpid");
        String journeyRaw = str(body.get("journey_id"), body.get("journeyId"));
        String encounterRaw = str(body.get("encounter_id"), body.get("encounterId"));
        String episodeRaw = str(body.get("episode_id"), body.get("episodeId"));
        accessGuard.requireCareRelationship(ctx, subjectCpid, journeyRaw, encounterRaw);

        if (journeyRaw == null && encounterRaw == null && episodeRaw == null) {
            throw new IllegalArgumentException(
                    "An MDT decision must carry a journey_id, encounter_id or episode_id "
                    + "(care-continuum-doctrine CC-5).");
        }

        String participants = str(body.get("participants"));
        String chairedBy = str(body.get("chaired_by"), body.get("chairedBy"));
        String decision = str(body.get("decision"));
        if (participants == null || participants.isBlank()
            || chairedBy == null || chairedBy.isBlank()
            || decision == null || decision.isBlank()) {
            throw new IllegalArgumentException(
                    "An MDT decision must name its participants, its chair and the decision itself. "
                    + "A decision that sets treatment intent with no author is not a governed "
                    + "decision — it is a rumour with clinical consequences.");
        }

        MdtDecisionEntity e = new MdtDecisionEntity();
        e.setDecisionId(UUID.randomUUID());
        e.setTenantId(ctx.tenantId());
        e.setSubjectCpid(subjectCpid);
        e.setJourneyId(uuid(journeyRaw));
        e.setEncounterId(uuid(encounterRaw));
        e.setEpisodeId(uuid(episodeRaw));
        e.setMeetingType(ProblemVocabulary.require("meeting_type",
                str(body.get("meeting_type"), body.get("meetingType")), MEETING_TYPES, true));
        String metOn = str(body.get("met_on"), body.get("metOn"));
        e.setMetOn(metOn == null ? LocalDate.now() : LocalDate.parse(metOn));
        e.setParticipants(participants);
        e.setChairedBy(chairedBy);
        e.setDecision(decision);
        String intent = str(body.get("treatment_intent"), body.get("treatmentIntent"));
        // Nullable and never defaulted: "the meeting did not set an intent" and "an intent was set
        // and not recorded" are different, and defaulting would erase the second.
        e.setTreatmentIntent(intent == null ? null
                : ProblemVocabulary.require("treatment_intent", intent, TREATMENT_INTENTS, true));
        e.setRationale(str(body.get("rationale")));
        e.setNextAction(str(body.get("next_action"), body.get("nextAction")));
        e.setResponsibleService(str(body.get("responsible_service"), body.get("responsibleService")));
        e.setRecordedBy(ctx.actorId());
        mdtRepository.save(e);

        Object rawProblems = body.get("problem_ids") == null ? body.get("problemIds") : body.get("problem_ids");
        if (rawProblems instanceof List<?> ids) {
            for (Object id : ids) {
                UUID problemId = UUID.fromString(String.valueOf(id));
                var problem = problemRepository.findById(problemId)
                        .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + problemId));
                if (!problem.getTenantId().equals(ctx.tenantId())
                    || !problem.getSubjectCpid().equals(subjectCpid)) {
                    // Linking another patient's diagnosis to this meeting would attribute one
                    // person's cancer to another's record.
                    throw new IllegalArgumentException(
                            "Problem " + problemId + " does not belong to patient " + subjectCpid);
                }
                MdtDecisionProblemEntity link = new MdtDecisionProblemEntity();
                link.setDecisionId(e.getDecisionId());
                link.setProblemId(problemId);
                link.setTenantId(ctx.tenantId());
                mdtProblemRepository.save(link);
            }
        }

        log.info("pct.mdt.decision id={} subject={} type={} intent={} by={} correlationId={}",
                e.getDecisionId(), subjectCpid, e.getMeetingType(), e.getTreatmentIntent(),
                ctx.actorId(), ctx.correlationId());
        return e;
    }

    @Transactional(readOnly = true)
    public List<MdtDecisionEntity> listDecisions(String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        return mdtRepository.findByTenantIdAndSubjectCpidOrderByMetOnDesc(ctx.tenantId(), subjectCpid);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> decision(UUID decisionId) {
        TrustContext ctx = TrustContextHolder.require();
        MdtDecisionEntity e = mdtRepository.findByTenantIdAndDecisionId(ctx.tenantId(), decisionId)
                .orElseThrow(() -> new IllegalArgumentException("Decision not found: " + decisionId));
        List<String> problems = new ArrayList<>();
        for (MdtDecisionProblemEntity p : mdtProblemRepository.findByTenantIdAndDecisionId(
                ctx.tenantId(), decisionId)) {
            problems.add(p.getProblemId().toString());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decision_id", e.getDecisionId());
        out.put("subject_cpid", e.getSubjectCpid());
        out.put("meeting_type", e.getMeetingType());
        out.put("met_on", e.getMetOn());
        out.put("participants", e.getParticipants());
        out.put("chaired_by", e.getChairedBy());
        out.put("decision", e.getDecision());
        out.put("treatment_intent", e.getTreatmentIntent());
        out.put("rationale", e.getRationale());
        out.put("next_action", e.getNextAction());
        out.put("responsible_service", e.getResponsibleService());
        out.put("problem_ids", problems);
        if (e.getTreatmentIntent() == null) {
            out.put("treatment_intent_note",
                    "No treatment intent was recorded for this meeting. That is not the same as an "
                    + "intent of NOT_SET, and it must not be read as one.");
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static String required(Map<String, Object> body, String snake, String camel) {
        String v = str(body.get(snake), body.get(camel));
        if (v == null) {
            throw new IllegalArgumentException("Missing required field: " + snake);
        }
        return v;
    }

    private static String str(Object... candidates) {
        for (Object o : candidates) {
            if (o != null && !o.toString().isBlank()) {
                return o.toString().trim();
            }
        }
        return null;
    }

    private static boolean bool(Object... candidates) {
        for (Object o : candidates) {
            if (o instanceof Boolean b) return b;
            if (o != null) return Boolean.parseBoolean(o.toString());
        }
        return false;
    }

    private static UUID uuid(String raw) {
        return raw == null ? null : UUID.fromString(raw);
    }
}
