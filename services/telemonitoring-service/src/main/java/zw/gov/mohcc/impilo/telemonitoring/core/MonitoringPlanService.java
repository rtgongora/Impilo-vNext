package zw.gov.mohcc.impilo.telemonitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.telemonitoring.domain.ConsentStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctProblemContributionClient;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctProblemContributionClient.ContributionResult;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctTaskClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.ThresholdProfileEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.ThresholdProfileRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The monitoring-plan engine (OF-B22, Vol II §14.2/§14.3).
 *
 * <p>Doctrine enforced here:</p>
 * <ul>
 *   <li><b>Approval gate</b> — a plan becomes ACTIVE only through {@link #approve} with an
 *       identified clinician approver. Plans carrying automated-suggestion provenance
 *       ({@code suggestedBy}) can never be approved by the suggesting system itself, and
 *       creation never yields any status other than DRAFT. No patient-facing notification
 *       is emitted pre-approval: the only pre-activation event is
 *       {@code telemonitoring.plan.created.v1} (an internal, clinician-facing signal).</li>
 *   <li><b>Reason-bound transitions</b> — suspend/resume/complete/cancel demand a reason.</li>
 *   <li><b>Immutable threshold versions</b> — an amendment INSERTs version N+1 with a
 *       supersedes pointer; existing rows are never mutated. The DB UNIQUE(plan_id,version)
 *       constraint is the concurrency race guard.</li>
 * </ul>
 */
@Service
public class MonitoringPlanService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringPlanService.class);

    private final MonitoringPlanRepository planRepository;
    private final MonitoringProgrammeRepository programmeRepository;
    private final ThresholdProfileRepository thresholdRepository;
    private final TelemonitoringEventEmitter eventEmitter;
    private final PctTaskClient pctTaskClient;
    private final PctProblemContributionClient pctProblemClient;
    private final ObjectMapper objectMapper;

    public MonitoringPlanService(MonitoringPlanRepository planRepository,
                                 MonitoringProgrammeRepository programmeRepository,
                                 ThresholdProfileRepository thresholdRepository,
                                 TelemonitoringEventEmitter eventEmitter,
                                 PctTaskClient pctTaskClient,
                                 PctProblemContributionClient pctProblemClient,
                                 ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.programmeRepository = programmeRepository;
        this.thresholdRepository = thresholdRepository;
        this.eventEmitter = eventEmitter;
        this.pctTaskClient = pctTaskClient;
        this.pctProblemClient = pctProblemClient;
        this.objectMapper = objectMapper;
    }

    // ── Commands ──

    public record CreatePlanCommand(
            UUID tenantId,
            String patientCpid,
            String programmeCode,
            String orosOrderId,
            String clinicalIndication,
            String monitoringSetting,
            String reviewCadence,
            String careTeamJson,
            String initialThresholdsJson,
            String suggestedBy,
            String suggestedSystemVersion,
            String createdBy,
            String consentReference,
            String consentStatus) {

        /** Backwards-compatible constructor (pre-OF-B21 callers — consent pointers absent → UNVERIFIED). */
        public CreatePlanCommand(UUID tenantId, String patientCpid, String programmeCode, String orosOrderId,
                                 String clinicalIndication, String monitoringSetting, String reviewCadence,
                                 String careTeamJson, String initialThresholdsJson, String suggestedBy,
                                 String suggestedSystemVersion, String createdBy) {
            this(tenantId, patientCpid, programmeCode, orosOrderId, clinicalIndication, monitoringSetting,
                    reviewCadence, careTeamJson, initialThresholdsJson, suggestedBy, suggestedSystemVersion,
                    createdBy, null, null);
        }
    }

    public record AmendThresholdsCommand(
            String parametersJson,
            String reason,
            String amendedBy,
            Integer expectedCurrentVersion) {
    }

    // ── Creation (API or OROS enrolment consumer) ──

    /**
     * Create a DRAFT plan plus its immutable threshold-profile v1. {@code strictProgramme}
     * requires the programme to exist and be active (API lane); the enrolment consumer uses
     * the lenient lane so an order referencing an as-yet-ungoverned programme still lands as
     * a reviewable draft instead of being silently dropped.
     */
    @Transactional
    public MonitoringPlanEntity createDraft(CreatePlanCommand cmd, boolean strictProgramme) {
        require(cmd.tenantId() != null, "tenantId is required");
        requireText(cmd.patientCpid(), "patientCpid is required");
        requireText(cmd.programmeCode(), "programmeCode is required");

        Optional<MonitoringProgrammeEntity> programme = programmeRepository.findByCode(cmd.programmeCode());
        if (strictProgramme) {
            if (programme.isEmpty() || !programme.get().isActive()) {
                throw new TelemonitoringDomainException("TM_UNKNOWN_PROGRAMME", 400,
                        "Unknown or inactive monitoring programme: " + cmd.programmeCode());
            }
        } else if (programme.isEmpty()) {
            log.warn("Enrolment references programme '{}' not (yet) in the governed catalogue — draft created for clinician review",
                    cmd.programmeCode());
        }

        if (cmd.orosOrderId() != null && planRepository.findByOrosOrderId(cmd.orosOrderId()).isPresent()) {
            throw new TelemonitoringDomainException("TM_DUPLICATE_ENROLMENT_ORDER", 409,
                    "A monitoring plan already exists for OROS order " + cmd.orosOrderId());
        }

        MonitoringPlanEntity plan = new MonitoringPlanEntity();
        plan.setTenantId(cmd.tenantId());
        plan.setPatientCpid(cmd.patientCpid());
        plan.setProgrammeCode(cmd.programmeCode());
        plan.setStatus(PlanStatus.DRAFT); // creation NEVER yields any other status (§14.2)
        plan.setOrosOrderId(cmd.orosOrderId());
        plan.setClinicalIndication(cmd.clinicalIndication());
        plan.setMonitoringSetting(cmd.monitoringSetting());
        plan.setReviewCadence(cmd.reviewCadence());
        if (cmd.careTeamJson() != null && !cmd.careTeamJson().isBlank()) {
            plan.setCareTeam(cmd.careTeamJson());
        }
        if (cmd.suggestedBy() != null && !cmd.suggestedBy().isBlank()) {
            plan.setSuggestedBy(cmd.suggestedBy());
            plan.setSuggestedSystemVersion(cmd.suggestedSystemVersion());
            plan.setSuggestedAt(OffsetDateTime.now());
        }
        // MVUMO consent POINTERS only (§14.2 item 9) — absent stays honest UNVERIFIED.
        plan.setConsentReference(cmd.consentReference());
        try {
            plan.setConsentStatus(ConsentStatus.parse(cmd.consentStatus()));
        } catch (IllegalArgumentException e) {
            throw new TelemonitoringDomainException("TM_INVALID_CONSENT_STATUS", 400,
                    "Unknown consent status: " + cmd.consentStatus());
        }
        if (cmd.consentReference() != null || (cmd.consentStatus() != null && !cmd.consentStatus().isBlank())) {
            plan.setConsentUpdatedAt(OffsetDateTime.now());
        }
        plan.setCreatedBy(cmd.createdBy());
        plan = planRepository.save(plan);

        // Threshold profile v1: request-supplied personalisation, else programme defaults.
        String parameters = cmd.initialThresholdsJson() != null && !cmd.initialThresholdsJson().isBlank()
                ? cmd.initialThresholdsJson()
                : programme.map(MonitoringProgrammeEntity::getDefaultThresholds).orElse("{}");
        ThresholdProfileEntity v1 = new ThresholdProfileEntity();
        v1.setPlanId(plan.getId());
        v1.setTenantId(cmd.tenantId());
        v1.setVersion(1);
        v1.setParameters(parameters);
        v1.setReason("Initial profile at plan creation");
        v1.setCreatedBy(cmd.createdBy() != null ? cmd.createdBy() : "system");
        thresholdRepository.save(v1);

        Map<String, Object> payload = basePayload(plan);
        payload.put("suggestedBy", plan.getSuggestedBy());
        eventEmitter.emitPlanEvent("created", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return plan;
    }

    // ── Lifecycle ──

    /**
     * Clinician approval → ACTIVE (§14.2). A DRAFT plan passes through PENDING_APPROVAL
     * as two audited guarded transitions in one call. Automated suggestions MUST NOT
     * self-activate: the approver must be an identified actor distinct from the
     * suggesting system.
     *
     * <p>OF-B21 activation side-effects:</p>
     * <ul>
     *   <li><b>Consent gate (fail-closed on refusal only)</b> — activation refuses when the
     *       MVUMO consent pointer is an explicit REFUSED/REVOKED. Absent consent is honest
     *       UNVERIFIED: activation proceeds with that state recorded on the event.</li>
     *   <li><b>CHW/care-team task</b> — when the plan's care_team carries a CHW binding
     *       ({@code chwId}), a first-monitoring-visit task is requested: the durable
     *       {@code telemonitoring.plan.task_requested.v1} outbox event is written in this
     *       transaction and PCT's generic task lane ({@code POST /v1/tasks}) is pushed
     *       best-effort — a degraded PCT never rolls back a clinician's activation.</li>
     *   <li><b>Problem-list anchor</b> — when a clinical indication or programme is present,
     *       a best-effort contribution into {@code pct_problems} is attempted and
     *       {@code pct_problem_ref} stores the pointer when PCT accepts it.</li>
     * </ul>
     */
    @Transactional
    public MonitoringPlanEntity approve(UUID planId, String approvedBy) {
        MonitoringPlanEntity plan = load(planId);
        requireText(approvedBy, "approvedBy (clinician identity) is required to activate a plan");
        if (plan.getSuggestedBy() != null && approvedBy.trim().equalsIgnoreCase(plan.getSuggestedBy().trim())) {
            throw new TelemonitoringDomainException("TM_SELF_APPROVAL_FORBIDDEN", 422,
                    "Automated suggestions must not self-activate: approver '" + approvedBy
                            + "' is the suggesting system. Clinician approval is mandatory (§14.2).");
        }
        // Consent gate — fail closed ONLY on explicit refusal (§14.2 posture).
        ConsentStatus consent = plan.getConsentStatus() != null ? plan.getConsentStatus() : ConsentStatus.UNVERIFIED;
        if (consent.blocksActivation()) {
            throw new TelemonitoringDomainException("TM_CONSENT_REFUSED", 422,
                    "Monitoring consent is " + consent + " (MVUMO ref: "
                            + (plan.getConsentReference() != null ? plan.getConsentReference() : "none")
                            + ") — activation refused. Resolve the consent journey in MVUMO first.");
        }
        if (consent == ConsentStatus.UNVERIFIED || consent == ConsentStatus.PENDING) {
            log.warn("Plan {} activating with consent status {} — recorded honestly, not upgraded (§14.2)",
                    plan.getId(), consent);
        }
        if (plan.getStatus() == PlanStatus.DRAFT) {
            transition(plan, PlanStatus.PENDING_APPROVAL, "Submitted for approval by " + approvedBy);
        }
        transition(plan, PlanStatus.ACTIVE, "Approved by " + approvedBy);
        plan.setApprovedBy(approvedBy);
        plan.setApprovedAt(OffsetDateTime.now());
        if (plan.getStartAt() == null) {
            plan.setStartAt(plan.getApprovedAt());
        }
        plan = planRepository.save(plan);

        Map<String, Object> payload = basePayload(plan);
        payload.put("approvedBy", approvedBy);
        payload.put("consentStatus", consent.name());
        payload.put("consentReference", plan.getConsentReference());
        eventEmitter.emitPlanEvent("activated", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());

        contributePctProblemIfNeeded(plan);
        requestChwTaskIfBound(plan, approvedBy);
        return plan;
    }

    /**
     * Side-effect (b): contribute the monitoring indication into pct_problems when the plan
     * carries a clinical indication or programme code. Fail-open — activation is never rolled
     * back when PCT is unavailable; {@code idx_tm_plans_uncontributed} reconciles later.
     */
    private void contributePctProblemIfNeeded(MonitoringPlanEntity plan) {
        if (plan.getPctProblemRef() != null) {
            return;
        }
        String display = resolveProblemDisplay(plan);
        if (display == null) {
            return;
        }
        String evidence = "Telemonitoring plan " + plan.getId() + " (programme " + plan.getProgrammeCode() + ")";
        ContributionResult result = pctProblemClient.contributeCondition(
                plan.getPatientCpid(), null, null, display, "PROVISIONAL", evidence);
        if (result.contributed()) {
            plan.setPctProblemRef(result.problemId());
            plan.setPctProblemContributedAt(OffsetDateTime.now());
            planRepository.save(plan);
        }
    }

    private static String resolveProblemDisplay(MonitoringPlanEntity plan) {
        if (plan.getClinicalIndication() != null && !plan.getClinicalIndication().isBlank()) {
            return plan.getClinicalIndication();
        }
        if (plan.getProgrammeCode() != null && !plan.getProgrammeCode().isBlank()) {
            return plan.getProgrammeCode();
        }
        return null;
    }

    /**
     * Side-effect (a): materialise the §14.2 "initial PCT task set" for the assigned CHW
     * when the care team carries a CHW binding. The outbox event is the durable seam; the
     * synchronous PCT push is the immediate delivery attempt whose outcome is recorded on
     * the event ({@code dispatchedToPct}).
     */
    private void requestChwTaskIfBound(MonitoringPlanEntity plan, String requestedBy) {
        JsonNode careTeam = parseCareTeam(plan);
        if (careTeam == null) {
            return;
        }
        String chwId = firstText(careTeam, "chwId", "chw_id");
        if (chwId == null && careTeam.hasNonNull("chw") && careTeam.get("chw").isObject()) {
            chwId = firstText(careTeam.get("chw"), "id", "chwId");
        }
        if (chwId == null || chwId.isBlank()) {
            return; // no CHW binding — no CHW task (facility-linked / self-monitoring settings)
        }
        UUID workspaceId = null;
        String workspaceRaw = firstText(careTeam, "chwWorkspaceId", "workspaceId");
        if (workspaceRaw != null) {
            try {
                workspaceId = UUID.fromString(workspaceRaw);
            } catch (IllegalArgumentException e) {
                log.warn("Plan {} care_team workspace id '{}' is not a UUID — task raised without workspace",
                        plan.getId(), workspaceRaw);
            }
        }
        String taskType = "MONITORING_ONBOARDING_VISIT";
        String notes = "First monitoring visit for plan " + plan.getId()
                + " (programme " + plan.getProgrammeCode() + ", patient " + plan.getPatientCpid() + ")";

        boolean dispatched = pctTaskClient.createTask(
                plan.getTenantId(), taskType, chwId, "CHW", workspaceId, null, notes);

        Map<String, Object> payload = basePayload(plan);
        payload.put("taskType", taskType);
        payload.put("assigneeId", chwId);
        payload.put("assigneeRole", "CHW");
        payload.put("workspaceId", workspaceId != null ? workspaceId.toString() : null);
        payload.put("requestedBy", requestedBy);
        payload.put("dispatchedToPct", dispatched);
        eventEmitter.emitPlanEvent("task_requested", plan.getId(), plan.getPatientCpid(),
                payload, plan.getTenantId());
    }

    private JsonNode parseCareTeam(MonitoringPlanEntity plan) {
        String json = plan.getCareTeam();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Plan {} care_team is not parseable JSON — CHW task seam skipped: {}",
                    plan.getId(), e.getMessage());
            return null;
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    // ── Consent pointer sync (MVUMO owns the journey; we hold pointers only) ──

    /**
     * Record/refresh the MVUMO consent pointer on a plan (§14.2 item 9). Pointers only —
     * never consent content. A pointer moving to REFUSED/REVOKED does not auto-suspend an
     * ACTIVE plan (that is a clinician's reason-bound decision) but is recorded and
     * emitted for the responsible team to act on.
     */
    @Transactional
    public MonitoringPlanEntity recordConsentPointer(UUID planId, String consentReference,
                                                     String consentStatus, String actor) {
        MonitoringPlanEntity plan = load(planId);
        if (plan.getStatus().isTerminal()) {
            throw new TelemonitoringDomainException("TM_PLAN_TERMINAL", 409,
                    "Consent pointers cannot be updated on a " + plan.getStatus() + " plan");
        }
        ConsentStatus status;
        try {
            status = ConsentStatus.parse(consentStatus);
        } catch (IllegalArgumentException e) {
            throw new TelemonitoringDomainException("TM_INVALID_CONSENT_STATUS", 400,
                    "Unknown consent status: " + consentStatus);
        }
        plan.setConsentReference(consentReference);
        plan.setConsentStatus(status);
        plan.setConsentUpdatedAt(OffsetDateTime.now());
        plan = planRepository.save(plan);
        if (status.blocksActivation() && plan.getStatus() == PlanStatus.ACTIVE) {
            log.warn("ACTIVE plan {} consent pointer moved to {} — clinician review required (no auto-suspend)",
                    plan.getId(), status);
        }
        Map<String, Object> payload = basePayload(plan);
        payload.put("consentReference", consentReference);
        payload.put("consentStatus", status.name());
        payload.put("actor", actor);
        eventEmitter.emitPlanEvent("consent_updated", plan.getId(), plan.getPatientCpid(),
                payload, plan.getTenantId());
        return plan;
    }

    // ── Review cadence (OF-B21 side-effect (c): timer groundwork) ──

    /**
     * Record a completed clinical review: re-arms the cadence timer
     * ({@code last_review_at = now}, due-notification cleared).
     */
    @Transactional
    public MonitoringPlanEntity recordReview(UUID planId, String reviewedBy) {
        MonitoringPlanEntity plan = load(planId);
        requireText(reviewedBy, "reviewedBy (clinician identity) is required to record a review");
        if (plan.getStatus() != PlanStatus.ACTIVE && plan.getStatus() != PlanStatus.SUSPENDED) {
            throw new TelemonitoringDomainException("TM_INVALID_TRANSITION", 409,
                    "Reviews can only be recorded on ACTIVE or SUSPENDED plans (current: " + plan.getStatus() + ")");
        }
        plan.setLastReviewAt(OffsetDateTime.now());
        plan.setReviewDueNotifiedAt(null);
        plan = planRepository.save(plan);
        Map<String, Object> payload = basePayload(plan);
        payload.put("reviewedBy", reviewedBy);
        payload.put("lastReviewAt", plan.getLastReviewAt().toString());
        eventEmitter.emitPlanEvent("review_recorded", plan.getId(), plan.getPatientCpid(),
                payload, plan.getTenantId());
        return plan;
    }

    /**
     * Cadence sweep: emit {@code telemonitoring.plan.review_due.v1} for every ACTIVE plan
     * whose review is overdue — {@code now > (last_review_at ?? start_at) + cadence}.
     * One signal per overdue period: {@code review_due_notified_at} de-duplicates until a
     * review is recorded (which clears it). Returns the number of events emitted.
     */
    @Transactional
    public int sweepReviewsDue(OffsetDateTime now) {
        int emitted = 0;
        for (MonitoringPlanEntity plan : planRepository.findByStatusAndReviewCadenceIsNotNull(PlanStatus.ACTIVE)) {
            Duration cadence = parseCadence(plan.getReviewCadence());
            if (cadence == null) {
                log.debug("Plan {} cadence '{}' not parseable — skipped by review sweep",
                        plan.getId(), plan.getReviewCadence());
                continue;
            }
            OffsetDateTime lastReview = plan.getLastReviewAt() != null ? plan.getLastReviewAt()
                    : plan.getStartAt() != null ? plan.getStartAt()
                    : plan.getApprovedAt() != null ? plan.getApprovedAt()
                    : plan.getCreatedAt();
            if (lastReview == null || !now.isAfter(lastReview.plus(cadence))) {
                continue;
            }
            if (plan.getReviewDueNotifiedAt() != null && plan.getReviewDueNotifiedAt().isAfter(lastReview)) {
                continue; // already signalled for this overdue period
            }
            plan.setReviewDueNotifiedAt(now);
            planRepository.save(plan);
            Map<String, Object> payload = basePayload(plan);
            payload.put("reviewCadence", plan.getReviewCadence());
            payload.put("lastReviewAt", lastReview.toString());
            payload.put("dueSince", lastReview.plus(cadence).toString());
            eventEmitter.emitPlanEvent("review_due", plan.getId(), plan.getPatientCpid(),
                    payload, plan.getTenantId());
            emitted++;
        }
        return emitted;
    }

    /**
     * Cadence vocabulary (V001: "e.g. DAILY / WEEKLY / MONTHLY") plus ISO-8601 durations
     * ({@code P7D}). Unknown cadences return null and are skipped — never guessed.
     */
    static Duration parseCadence(String cadence) {
        if (cadence == null || cadence.isBlank()) {
            return null;
        }
        String value = cadence.trim().toUpperCase(Locale.ROOT);
        switch (value) {
            case "DAILY": return Duration.ofDays(1);
            case "WEEKLY": return Duration.ofDays(7);
            case "FORTNIGHTLY": return Duration.ofDays(14);
            case "MONTHLY": return Duration.ofDays(30);
            case "QUARTERLY": return Duration.ofDays(90);
            default:
                if (value.startsWith("P")) {
                    try {
                        return Duration.parse(value); // Duration.parse handles PnD / PTnH forms
                    } catch (java.time.format.DateTimeParseException e) {
                        return null;
                    }
                }
                return null;
        }
    }

    @Transactional
    public MonitoringPlanEntity suspend(UUID planId, String reason, String actor) {
        MonitoringPlanEntity plan = load(planId);
        requireText(reason, "A reason is required to suspend a monitoring plan");
        transition(plan, PlanStatus.SUSPENDED, reason);
        plan = planRepository.save(plan);
        Map<String, Object> payload = basePayload(plan);
        payload.put("reason", reason);
        payload.put("actor", actor);
        eventEmitter.emitPlanEvent("suspended", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return plan;
    }

    /** Resume = SUSPENDED → ACTIVE; re-emits {@code activated} (there is no separate resumed event). */
    @Transactional
    public MonitoringPlanEntity resume(UUID planId, String reason, String actor) {
        MonitoringPlanEntity plan = load(planId);
        requireText(reason, "A reason is required to resume a monitoring plan");
        if (plan.getStatus() != PlanStatus.SUSPENDED) {
            throw new TelemonitoringDomainException("TM_INVALID_TRANSITION", 409,
                    "Only a SUSPENDED plan can be resumed (current: " + plan.getStatus() + ")");
        }
        transition(plan, PlanStatus.ACTIVE, reason);
        plan = planRepository.save(plan);
        Map<String, Object> payload = basePayload(plan);
        payload.put("reason", reason);
        payload.put("actor", actor);
        payload.put("resumed", true);
        eventEmitter.emitPlanEvent("activated", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return plan;
    }

    @Transactional
    public MonitoringPlanEntity complete(UUID planId, String reason, String actor) {
        MonitoringPlanEntity plan = load(planId);
        requireText(reason, "A reason is required to complete a monitoring plan");
        transition(plan, PlanStatus.COMPLETED, reason);
        plan.setEndAt(OffsetDateTime.now());
        plan = planRepository.save(plan);
        Map<String, Object> payload = basePayload(plan);
        payload.put("reason", reason);
        payload.put("actor", actor);
        eventEmitter.emitPlanEvent("completed", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return plan;
    }

    @Transactional
    public MonitoringPlanEntity cancel(UUID planId, String reason, String actor) {
        MonitoringPlanEntity plan = load(planId);
        requireText(reason, "A reason is required to cancel a monitoring plan");
        transition(plan, PlanStatus.CANCELLED, reason);
        plan.setEndAt(OffsetDateTime.now());
        plan = planRepository.save(plan);
        Map<String, Object> payload = basePayload(plan);
        payload.put("reason", reason);
        payload.put("actor", actor);
        eventEmitter.emitPlanEvent("cancelled", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return plan;
    }

    // ── Threshold profile amendments (immutable version chain) ──

    /**
     * Clinician-approved amendment = new immutable version. Optional
     * {@code expectedCurrentVersion} gives callers an optimistic-concurrency check; the
     * DB UNIQUE(plan_id, version) constraint backstops the race either way.
     */
    @Transactional
    public ThresholdProfileEntity amendThresholds(UUID planId, AmendThresholdsCommand cmd) {
        MonitoringPlanEntity plan = load(planId);
        if (plan.getStatus().isTerminal()) {
            throw new TelemonitoringDomainException("TM_PLAN_TERMINAL", 409,
                    "Thresholds cannot be amended on a " + plan.getStatus() + " plan");
        }
        requireText(cmd.amendedBy(), "amendedBy (clinician identity) is required for a threshold amendment");
        requireText(cmd.parametersJson(), "parameters are required for a threshold amendment");
        requireText(cmd.reason(), "A reason is required for a threshold amendment");

        ThresholdProfileEntity current = thresholdRepository.findFirstByPlanIdOrderByVersionDesc(planId)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_NO_THRESHOLD_PROFILE", 500,
                        "Plan " + planId + " has no threshold profile — data integrity violation"));

        if (cmd.expectedCurrentVersion() != null && cmd.expectedCurrentVersion() != current.getVersion()) {
            throw new TelemonitoringDomainException("TM_THRESHOLD_VERSION_CONFLICT", 409,
                    "Threshold profile has moved on: expected current version " + cmd.expectedCurrentVersion()
                            + " but latest is " + current.getVersion());
        }

        ThresholdProfileEntity next = new ThresholdProfileEntity();
        next.setPlanId(planId);
        next.setTenantId(plan.getTenantId());
        next.setVersion(current.getVersion() + 1);
        next.setSupersedes(current.getId());
        next.setParameters(cmd.parametersJson());
        next.setReason(cmd.reason());
        next.setCreatedBy(cmd.amendedBy());
        next.setApprovedBy(cmd.amendedBy());
        ThresholdProfileEntity saved;
        try {
            saved = thresholdRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException e) {
            throw new TelemonitoringDomainException("TM_THRESHOLD_VERSION_CONFLICT", 409,
                    "Concurrent threshold amendment detected for plan " + planId
                            + " (version " + next.getVersion() + " already exists)");
        }

        Map<String, Object> payload = basePayload(plan);
        payload.put("thresholdProfileId", saved.getId().toString());
        payload.put("thresholdVersion", saved.getVersion());
        payload.put("supersedes", current.getId().toString());
        payload.put("amendedBy", cmd.amendedBy());
        payload.put("reason", cmd.reason());
        eventEmitter.emitPlanEvent("amended", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return saved;
    }

    // ── Reads ──

    @Transactional(readOnly = true)
    public MonitoringPlanEntity getPlan(UUID planId) {
        return load(planId);
    }

    @Transactional(readOnly = true)
    public List<MonitoringPlanEntity> listByPatient(UUID tenantId, String patientCpid) {
        require(tenantId != null, "tenantId is required");
        requireText(patientCpid, "patientCpid is required");
        return planRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(tenantId, patientCpid);
    }

    @Transactional(readOnly = true)
    public List<ThresholdProfileEntity> listThresholdVersions(UUID planId) {
        load(planId);
        return thresholdRepository.findByPlanIdOrderByVersionDesc(planId);
    }

    // ── Internals ──

    private MonitoringPlanEntity load(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new TelemonitoringDomainException("TM_PLAN_NOT_FOUND", 404,
                        "Monitoring plan not found: " + planId));
    }

    private void transition(MonitoringPlanEntity plan, PlanStatus to, String reason) {
        PlanStatus from = plan.getStatus();
        if (!PlanStatus.canTransition(from, to)) {
            throw new TelemonitoringDomainException("TM_INVALID_TRANSITION", 409,
                    "Illegal monitoring-plan transition " + from + " -> " + to);
        }
        plan.setStatus(to);
        plan.setLifecycleReason(reason);
        log.info("Monitoring plan {} transitioned {} -> {} ({})", plan.getId(), from, to, reason);
    }

    private Map<String, Object> basePayload(MonitoringPlanEntity plan) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", plan.getId().toString());
        payload.put("patientCpid", plan.getPatientCpid());
        payload.put("programmeCode", plan.getProgrammeCode());
        payload.put("status", plan.getStatus().name());
        payload.put("orosOrderId", plan.getOrosOrderId());
        return payload;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400, message);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new TelemonitoringDomainException("TM_INVALID_REQUEST", 400, message);
        }
    }
}
