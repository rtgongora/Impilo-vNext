package zw.gov.mohcc.impilo.rito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.CorrectiveActionEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiPlanEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiTaskEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.CorrectiveActionRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.QiPlanRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.QiTaskRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Owning service for the Rito improvement aggregates: CAPA (corrective/preventive actions,
 * {@code rit_corrective_action}) and quality-improvement / PDSA work ({@code rit_qi_plan},
 * {@code rit_qi_task}).
 *
 * <p>CAPA carries the "Act → Verify" closure of a finding: an action is opened, worked,
 * completed, then independently verified for effectiveness. A verification that finds the
 * action <em>was not effective</em> recommends a learning intervention back through Fundo
 * (Rito does not own the LMS — it only recommends).
 */
@Service
public class ImprovementService {

    private static final Set<String> VERIFICATION_OUTCOMES = Set.of("EFFECTIVE", "NOT_EFFECTIVE", "PARTIAL");

    private final CorrectiveActionRepository correctiveActionRepository;
    private final QiPlanRepository qiPlanRepository;
    private final QiTaskRepository qiTaskRepository;
    private final ReferenceGenerator referenceGenerator;
    private final RitoEventEmitter emitter;
    private final NotificationGateway notifications;

    public ImprovementService(CorrectiveActionRepository correctiveActionRepository,
                              QiPlanRepository qiPlanRepository,
                              QiTaskRepository qiTaskRepository,
                              ReferenceGenerator referenceGenerator,
                              RitoEventEmitter emitter,
                              NotificationGateway notifications) {
        this.correctiveActionRepository = correctiveActionRepository;
        this.qiPlanRepository = qiPlanRepository;
        this.qiTaskRepository = qiTaskRepository;
        this.referenceGenerator = referenceGenerator;
        this.emitter = emitter;
        this.notifications = notifications;
    }

    // ---- CAPA (rit_corrective_action) ----

    @Transactional
    public CorrectiveActionEntity createCorrectiveAction(UUID tenantId, String actionType, String title,
                                                         String description, String sourceType, String sourceRef,
                                                         UUID caseId, UUID auditFindingId, UUID facilityId,
                                                         String ownerActorId, String ownerActorType, String priority,
                                                         OffsetDateTime dueAt, String createdBy) {
        CorrectiveActionEntity ca = new CorrectiveActionEntity();
        ca.setTenantId(tenantId);
        ca.setCaReference(referenceGenerator.generate("RITO-CA",
                ref -> correctiveActionRepository.existsByTenantIdAndCaReference(tenantId, ref)));
        ca.setActionType(actionType != null ? actionType : "CORRECTIVE");
        ca.setTitle(require(title, "title"));
        ca.setDescription(description);
        ca.setSourceType(sourceType);
        ca.setSourceRef(sourceRef);
        ca.setCaseId(caseId);
        ca.setAuditFindingId(auditFindingId);
        ca.setFacilityId(facilityId);
        ca.setOwnerActorId(ownerActorId);
        ca.setOwnerActorType(ownerActorType);
        ca.setPriority(priority != null ? priority : "MEDIUM");
        ca.setStatus("OPEN");
        ca.setDueAt(dueAt);
        ca.setCreatedBy(createdBy);
        correctiveActionRepository.save(ca);

        emitCorrectiveActionEvent(ca, "rito.corrective_action.created");
        return ca;
    }

    @Transactional
    public CorrectiveActionEntity progress(UUID tenantId, UUID id) {
        CorrectiveActionEntity ca = loadCorrectiveAction(tenantId, id);
        ca.setStatus("IN_PROGRESS");
        return correctiveActionRepository.save(ca);
    }

    @Transactional
    public CorrectiveActionEntity complete(UUID tenantId, UUID id, String completedBy, String completionNotes) {
        CorrectiveActionEntity ca = loadCorrectiveAction(tenantId, id);
        ca.setStatus("COMPLETED");
        ca.setCompletedAt(OffsetDateTime.now());
        ca.setCompletedBy(completedBy);
        ca.setCompletionNotes(completionNotes);
        correctiveActionRepository.save(ca);

        emitCorrectiveActionEvent(ca, "rito.corrective_action.completed");
        return ca;
    }

    @Transactional
    public CorrectiveActionEntity verify(UUID tenantId, UUID id, String verifiedBy, String outcome, String notes) {
        CorrectiveActionEntity ca = loadCorrectiveAction(tenantId, id);
        if (outcome == null || !VERIFICATION_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException(
                    "verification outcome must be one of EFFECTIVE/NOT_EFFECTIVE/PARTIAL");
        }
        ca.setStatus("VERIFIED");
        ca.setVerifiedAt(OffsetDateTime.now());
        ca.setVerifiedBy(verifiedBy);
        ca.setVerificationOutcome(outcome);
        ca.setVerificationNotes(notes);
        correctiveActionRepository.save(ca);

        emitCorrectiveActionEvent(ca, "rito.corrective_action.verified");
        if ("NOT_EFFECTIVE".equals(outcome)) {
            notifications.recommendLearning(tenantId, "CORRECTIVE_ACTION", id.toString(),
                    "quality-improvement", "CAPA verification found the action was not effective");
        }
        return ca;
    }

    @Transactional
    public CorrectiveActionEntity cancel(UUID tenantId, UUID id, String reason) {
        CorrectiveActionEntity ca = loadCorrectiveAction(tenantId, id);
        ca.setStatus("CANCELLED");
        return correctiveActionRepository.save(ca);
    }

    @Transactional(readOnly = true)
    public CorrectiveActionEntity getCorrectiveAction(UUID tenantId, UUID id) {
        return loadCorrectiveAction(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<CorrectiveActionEntity> listCorrectiveActions(UUID tenantId, String status) {
        if (status == null) {
            return correctiveActionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return correctiveActionRepository.findByTenantIdAndStatus(tenantId, status);
    }

    @Transactional(readOnly = true)
    public List<CorrectiveActionEntity> byCase(UUID tenantId, UUID caseId) {
        return correctiveActionRepository.findByCaseId(caseId);
    }

    // ---- QI / PDSA (rit_qi_plan, rit_qi_task) ----

    @Transactional
    public QiPlanEntity createQiPlan(UUID tenantId, String title, String aimStatement, String methodology,
                                     String problemStatement, BigDecimal baseline, BigDecimal target,
                                     String measureUnit, UUID facilityId, UUID districtId, String sponsorActorId,
                                     String leadActorId, UUID sourceCaseId, UUID sourceFindingId, String createdBy) {
        QiPlanEntity plan = new QiPlanEntity();
        plan.setTenantId(tenantId);
        plan.setQiReference(referenceGenerator.generate("RITO-QI",
                ref -> qiPlanRepository.existsByTenantIdAndQiReference(tenantId, ref)));
        plan.setTitle(require(title, "title"));
        plan.setAimStatement(aimStatement);
        plan.setMethodology(methodology != null ? methodology : "PDSA");
        plan.setProblemStatement(problemStatement);
        plan.setBaselineMeasure(baseline);
        plan.setTargetMeasure(target);
        plan.setMeasureUnit(measureUnit);
        plan.setFacilityId(facilityId);
        plan.setDistrictId(districtId);
        plan.setSponsorActorId(sponsorActorId);
        plan.setLeadActorId(leadActorId);
        plan.setSourceCaseId(sourceCaseId);
        plan.setSourceFindingId(sourceFindingId);
        plan.setStatus("DRAFT");
        plan.setCreatedBy(createdBy);
        qiPlanRepository.save(plan);

        emitQiPlanEvent(plan, "rito.qi_plan.created");
        return plan;
    }

    @Transactional
    public QiPlanEntity activateQiPlan(UUID tenantId, UUID id) {
        QiPlanEntity plan = loadQiPlan(tenantId, id);
        plan.setStatus("ACTIVE");
        plan.setStartedAt(OffsetDateTime.now());
        return qiPlanRepository.save(plan);
    }

    @Transactional
    public QiPlanEntity completeQiPlan(UUID tenantId, UUID id, String outcomeSummary) {
        QiPlanEntity plan = loadQiPlan(tenantId, id);
        plan.setStatus("COMPLETED");
        plan.setCompletedAt(OffsetDateTime.now());
        plan.setOutcomeSummary(outcomeSummary);
        qiPlanRepository.save(plan);

        emitQiPlanEvent(plan, "rito.qi_plan.completed");
        return plan;
    }

    @Transactional
    public QiTaskEntity addTask(UUID tenantId, UUID qiPlanId, String title, String description, String pdsaStage,
                                String ownerActorId, Integer ordinal, OffsetDateTime dueAt) {
        loadQiPlan(tenantId, qiPlanId);
        QiTaskEntity task = new QiTaskEntity();
        task.setQiPlanId(qiPlanId);
        task.setTenantId(tenantId);
        task.setTitle(require(title, "title"));
        task.setDescription(description);
        task.setPdsaStage(pdsaStage);
        task.setOwnerActorId(ownerActorId);
        task.setStatus("TODO");
        task.setOrdinal(ordinal != null ? ordinal : 0);
        task.setDueAt(dueAt);
        return qiTaskRepository.save(task);
    }

    @Transactional
    public QiTaskEntity updateTask(UUID tenantId, UUID taskId, String status, OffsetDateTime completedAt) {
        QiTaskEntity task = qiTaskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("QI task not found: " + taskId));
        if (!tenantId.equals(task.getTenantId())) {
            throw new NoSuchElementException("QI task not found: " + taskId);
        }
        task.setStatus(status);
        if ("DONE".equals(status)) {
            task.setCompletedAt(completedAt != null ? completedAt : OffsetDateTime.now());
        }
        return qiTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<QiTaskEntity> listTasks(UUID qiPlanId) {
        return qiTaskRepository.findByQiPlanIdOrderByOrdinalAsc(qiPlanId);
    }

    @Transactional(readOnly = true)
    public QiPlanEntity getQiPlan(UUID tenantId, UUID id) {
        return loadQiPlan(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<QiPlanEntity> listQiPlans(UUID tenantId, String status) {
        if (status == null) {
            return qiPlanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        return qiPlanRepository.findByTenantIdAndStatus(tenantId, status);
    }

    // ---- internal helpers ----

    private CorrectiveActionEntity loadCorrectiveAction(UUID tenantId, UUID id) {
        return correctiveActionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Corrective action not found: " + id));
    }

    private QiPlanEntity loadQiPlan(UUID tenantId, UUID id) {
        return qiPlanRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("QI plan not found: " + id));
    }

    private void emitCorrectiveActionEvent(CorrectiveActionEntity ca, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("correctiveActionId", ca.getId().toString());
        payload.put("caReference", ca.getCaReference());
        payload.put("actionType", ca.getActionType());
        payload.put("status", ca.getStatus());
        payload.put("priority", ca.getPriority());
        payload.put("sourceType", ca.getSourceType());
        payload.put("sourceRef", ca.getSourceRef());
        payload.put("caseId", ca.getCaseId() != null ? ca.getCaseId().toString() : null);
        payload.put("verificationOutcome", ca.getVerificationOutcome());
        emitter.emit("CORRECTIVE_ACTION", ca.getId().toString(), eventType, "CORRECTIVE_ACTION",
                ca.getId().toString(), payload, ca.getTenantId());
    }

    private void emitQiPlanEvent(QiPlanEntity plan, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("qiPlanId", plan.getId().toString());
        payload.put("qiReference", plan.getQiReference());
        payload.put("methodology", plan.getMethodology());
        payload.put("status", plan.getStatus());
        payload.put("facilityId", plan.getFacilityId() != null ? plan.getFacilityId().toString() : null);
        emitter.emit("QI_PLAN", plan.getId().toString(), eventType, "QI_PLAN",
                plan.getId().toString(), payload, plan.getTenantId());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }
}
