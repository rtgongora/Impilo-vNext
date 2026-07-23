package zw.gov.mohcc.impilo.telemonitoring.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.events.TelemonitoringEventEmitter;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.ThresholdProfileEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringPlanRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.ThresholdProfileRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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

    public MonitoringPlanService(MonitoringPlanRepository planRepository,
                                 MonitoringProgrammeRepository programmeRepository,
                                 ThresholdProfileRepository thresholdRepository,
                                 TelemonitoringEventEmitter eventEmitter) {
        this.planRepository = planRepository;
        this.programmeRepository = programmeRepository;
        this.thresholdRepository = thresholdRepository;
        this.eventEmitter = eventEmitter;
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
            String createdBy) {
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
        eventEmitter.emitPlanEvent("activated", plan.getId(), plan.getPatientCpid(), payload, plan.getTenantId());
        return plan;
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
