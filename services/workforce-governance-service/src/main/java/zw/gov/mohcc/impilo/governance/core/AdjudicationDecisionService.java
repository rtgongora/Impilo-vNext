package zw.gov.mohcc.impilo.governance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.AdjudicationDecisionEntity;
import zw.gov.mohcc.impilo.governance.persistence.AdjudicationDecisionRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Records and resolves IATG adjudication decisions (WGV SoR for adjudication
 * outcomes). Decisions are produced by the workflow-service adjudication
 * workflows (identity/provider/facility-claim-adjudication, V003 seed) or by
 * out-of-band authority action, and are stored APPEND-ONLY in
 * {@code wgv_adjudication_decision}.
 *
 * <p>Doctrine:</p>
 * <ul>
 *   <li><b>Reason is mandatory</b> — a decision without an auditable rationale
 *       is rejected.</li>
 *   <li><b>Append-only</b> — there is no update or delete path. A decision is
 *       superseded by recording a new row for the same
 *       (subjectType, subjectRef).</li>
 *   <li><b>Latest-effective resolution</b> — the effective decision for a
 *       subject is the newest row with {@code effectiveAt <= now} and
 *       ({@code expiresAt} null or {@code > now}).</li>
 * </ul>
 */
@Service
public class AdjudicationDecisionService {

    private static final Logger log = LoggerFactory.getLogger(AdjudicationDecisionService.class);

    /** Adjudicable subject classes (kept in sync with V008 check constraint). */
    public static final Set<String> SUBJECT_TYPES = Set.of("IDENTITY", "PROVIDER", "FACILITY", "ORGANIZATION");
    /** Legal decision outcomes (kept in sync with V008 check constraint). */
    public static final Set<String> DECISIONS = Set.of("APPROVED", "DENIED", "CONDITIONAL");

    public static final String EVENT_DECISION_RECORDED = "impilo.governance.adjudication.decision.recorded";

    private final AdjudicationDecisionRepository repository;
    private final GovernanceEventService eventService;
    private final EmploymentConflictCaseService employmentConflictCaseService;

    public AdjudicationDecisionService(AdjudicationDecisionRepository repository,
                                       GovernanceEventService eventService,
                                       EmploymentConflictCaseService employmentConflictCaseService) {
        this.repository = repository;
        this.eventService = eventService;
        this.employmentConflictCaseService = employmentConflictCaseService;
    }

    /** Immutable command describing a decision to record. */
    public record RecordDecisionCommand(
            UUID workflowInstanceId,
            String subjectType,
            String subjectRef,
            String decision,
            String reason,
            String authorityUserId,
            String authorityRole,
            Instant effectiveAt,
            Instant expiresAt,
            Map<String, Object> permissions,
            Map<String, Object> restrictions,
            List<String> evidenceRefs,
            UUID accessRequestId) {}

    /**
     * Records a decision as a NEW append-only row. Never mutates an existing
     * row — recording a decision for a subject that already has one is the
     * supersede path.
     */
    @Transactional
    public AdjudicationDecisionEntity record(UUID tenantId, String actor,
                                             RecordDecisionCommand cmd, String correlationId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        String subjectType = normalized(cmd.subjectType());
        if (subjectType == null || !SUBJECT_TYPES.contains(subjectType)) {
            throw new IllegalArgumentException(
                    "subjectType is required and must be one of " + SUBJECT_TYPES + ": " + cmd.subjectType());
        }
        if (cmd.subjectRef() == null || cmd.subjectRef().isBlank()) {
            throw new IllegalArgumentException("subjectRef is required");
        }
        String decision = normalized(cmd.decision());
        if (decision == null || !DECISIONS.contains(decision)) {
            throw new IllegalArgumentException(
                    "decision is required and must be one of " + DECISIONS + ": " + cmd.decision());
        }
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new IllegalArgumentException("reason is mandatory for every adjudication decision");
        }
        Instant effectiveAt = cmd.effectiveAt() != null ? cmd.effectiveAt() : Instant.now();
        if (cmd.expiresAt() != null && !cmd.expiresAt().isAfter(effectiveAt)) {
            throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        }

        AdjudicationDecisionEntity entity = new AdjudicationDecisionEntity();
        entity.setTenantId(tenantId);
        entity.setWorkflowInstanceId(cmd.workflowInstanceId());
        entity.setSubjectType(subjectType);
        entity.setSubjectRef(cmd.subjectRef().trim());
        entity.setDecision(decision);
        entity.setReason(cmd.reason().trim());
        entity.setAuthorityUserId(cmd.authorityUserId() != null ? cmd.authorityUserId() : actor);
        entity.setAuthorityRole(cmd.authorityRole());
        entity.setEffectiveAt(effectiveAt);
        entity.setExpiresAt(cmd.expiresAt());
        entity.setPermissions(cmd.permissions());
        entity.setRestrictions(cmd.restrictions());
        entity.setEvidenceRefs(cmd.evidenceRefs());
        entity.setAccessRequestId(cmd.accessRequestId());
        entity.setCreatedBy(actor);
        AdjudicationDecisionEntity saved = repository.save(entity);

        // WS-D: an IDENTITY decision resolves an open employment-conflict case in-process.
        // Runs in its own transaction and is best-effort, so it can never roll back this
        // append-only decision (the system of record).
        if ("IDENTITY".equals(subjectType)) {
            employmentConflictCaseService.resolveFromDecision(
                    tenantId, saved.getWorkflowInstanceId(), saved.getSubjectRef(),
                    decision, saved.getId().toString(), correlationId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decisionId", saved.getId().toString());
        payload.put("subjectType", subjectType);
        payload.put("subjectRef", saved.getSubjectRef());
        payload.put("decision", decision);
        payload.put("effectiveAt", effectiveAt.toString());
        if (cmd.workflowInstanceId() != null) payload.put("workflowInstanceId", cmd.workflowInstanceId().toString());
        if (cmd.accessRequestId() != null) payload.put("accessRequestId", cmd.accessRequestId().toString());
        eventService.enqueue("ADJUDICATION_DECISION", saved.getId().toString(),
                EVENT_DECISION_RECORDED, payload, tenantId, correlationId);

        log.info("Recorded adjudication decision [id={}, subject={}:{}, decision={}] tenant {}",
                saved.getId(), subjectType, saved.getSubjectRef(), decision, tenantId);
        return saved;
    }

    /** Full decision history for a subject, newest-effective first. */
    @Transactional(readOnly = true)
    public List<AdjudicationDecisionEntity> history(UUID tenantId, String subjectType, String subjectRef) {
        requireSubject(subjectType, subjectRef);
        return repository.findByTenantIdAndSubjectTypeAndSubjectRefOrderByEffectiveAtDescCreatedAtDesc(
                tenantId, normalized(subjectType), subjectRef.trim());
    }

    /**
     * Latest-effective decision for a subject as of {@code asOf}: the newest
     * row (by effectiveAt, then createdAt) with {@code effectiveAt <= asOf}
     * and ({@code expiresAt} null or {@code > asOf}).
     */
    @Transactional(readOnly = true)
    public Optional<AdjudicationDecisionEntity> latestEffective(UUID tenantId, String subjectType,
                                                                String subjectRef, Instant asOf) {
        Instant at = asOf != null ? asOf : Instant.now();
        return history(tenantId, subjectType, subjectRef).stream()
                .filter(d -> !d.getEffectiveAt().isAfter(at))
                .filter(d -> d.getExpiresAt() == null || d.getExpiresAt().isAfter(at))
                .findFirst();
    }

    private static void requireSubject(String subjectType, String subjectRef) {
        String normalized = normalized(subjectType);
        if (normalized == null || !SUBJECT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "subjectType is required and must be one of " + SUBJECT_TYPES + ": " + subjectType);
        }
        if (subjectRef == null || subjectRef.isBlank()) {
            throw new IllegalArgumentException("subjectRef is required");
        }
    }

    private static String normalized(String value) {
        return value != null && !value.isBlank() ? value.trim().toUpperCase() : null;
    }
}
