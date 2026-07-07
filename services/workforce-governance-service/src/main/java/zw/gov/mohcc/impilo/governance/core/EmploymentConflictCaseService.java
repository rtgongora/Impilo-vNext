package zw.gov.mohcc.impilo.governance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.WgvEmploymentConflictCaseEntity;
import zw.gov.mohcc.impilo.governance.persistence.WgvEmploymentConflictCaseRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Employment EC-number CONFLICT cases (IATG WS-D / Journey F). Opens a durable
 * case when a match returns CONFLICT and starts an identity-adjudication workflow
 * (best-effort, config-gated); an append-only adjudication decision resolves the
 * case in-process. Both entry points are non-blocking — a failure never breaks
 * the match response or the append-only decision write.
 */
@Service
public class EmploymentConflictCaseService {

    private static final Logger log = LoggerFactory.getLogger(EmploymentConflictCaseService.class);
    private static final List<String> OPEN_STATES = List.of("OPEN", "UNDER_REVIEW");

    private final WgvEmploymentConflictCaseRepository repository;
    private final WgvWorkflowServiceClient workflowServiceClient;

    public EmploymentConflictCaseService(WgvEmploymentConflictCaseRepository repository,
                                         WgvWorkflowServiceClient workflowServiceClient) {
        this.repository = repository;
        this.workflowServiceClient = workflowServiceClient;
    }

    /**
     * Open a conflict case from a CONFLICT match result and start adjudication.
     * Idempotent per (tenant, EC): an existing OPEN/UNDER_REVIEW case short-circuits.
     * Best-effort — swallows failures so the match response is unaffected.
     */
    @Transactional
    public Optional<WgvEmploymentConflictCaseEntity> openFromMatch(UUID tenantId, String ecNumber,
                                                                   String healthId, Map<String, Object> matchResult,
                                                                   String correlationId) {
        try {
            String canonicalEc = EcNumbers.canonicalise(ecNumber);
            if (canonicalEc == null) {
                return Optional.empty();
            }
            if (!repository.findByTenantIdAndEcNumberAndStatusIn(tenantId, canonicalEc, OPEN_STATES).isEmpty()) {
                log.info("Employment conflict for EC {} already has an open case — not reopening", EcNumbers.mask(canonicalEc));
                return Optional.empty();
            }
            String subjectRef = (healthId != null && !healthId.isBlank()) ? healthId : canonicalEc;
            int count = matchResult != null && matchResult.get("conflictingRecordCount") instanceof Number n
                    ? n.intValue() : 1;

            WgvEmploymentConflictCaseEntity entity = new WgvEmploymentConflictCaseEntity();
            entity.setTenantId(tenantId);
            entity.setEcNumber(canonicalEc);
            entity.setSuppliedHealthId(healthId);
            entity.setSubjectRef(subjectRef);
            entity.setConflictingRecordCount(count);
            entity.setStatus("OPEN");
            entity.setCreatedBy("wgv-employment-match");
            entity.setReason("EC-number match returned CONFLICT — routed to identity adjudication; "
                    + "no employment trust is asserted until resolved.");

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("ecNumberMasked", EcNumbers.mask(canonicalEc));
            context.put("suppliedHealthId", healthId);
            context.put("conflictingRecordCount", count);
            context.put("tenantId", tenantId.toString());

            Optional<UUID> instanceId = workflowServiceClient.startAdjudication(
                    tenantId, subjectRef, context, "wgv-employment-match", correlationId);
            instanceId.ifPresent(id -> {
                entity.setWorkflowInstanceId(id);
                entity.setStatus("UNDER_REVIEW");
            });

            WgvEmploymentConflictCaseEntity saved = repository.save(entity);
            log.info("Opened employment conflict case {} (EC {}, status {}, workflow {})",
                    saved.getId(), EcNumbers.mask(canonicalEc), saved.getStatus(), saved.getWorkflowInstanceId());
            return Optional.of(saved);
        } catch (Exception e) {
            log.warn("Failed to open employment conflict case ({}: {}) — match response unaffected",
                    e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve a conflict case from an append-only adjudication decision. Runs in a
     * SEPARATE transaction so a resolution failure never rolls back the decision
     * write (the decision is the system of record). APPROVED→RESOLVED_APPROVED,
     * DENIED→RESOLVED_REJECTED; CONDITIONAL is not auto-resolved.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveFromDecision(UUID tenantId, UUID workflowInstanceId, String subjectRef,
                                    String decision, String decisionId, String correlationId) {
        try {
            Optional<WgvEmploymentConflictCaseEntity> found = Optional.empty();
            if (workflowInstanceId != null) {
                found = repository.findByWorkflowInstanceId(workflowInstanceId);
            }
            if (found.isEmpty() && subjectRef != null) {
                found = repository.findByTenantIdAndSubjectRefAndStatusIn(tenantId, subjectRef, OPEN_STATES)
                        .stream().findFirst();
            }
            if (found.isEmpty()) {
                log.info("Adjudication decision {} (subject {}) matched no open employment conflict case — ignored",
                        decisionId, subjectRef);
                return;
            }
            WgvEmploymentConflictCaseEntity entity = found.get();
            if (entity.getStatus().startsWith("RESOLVED_")) {
                return; // idempotent — already terminal
            }
            String d = decision == null ? "" : decision.trim().toUpperCase();
            String terminal = switch (d) {
                case "APPROVED" -> "RESOLVED_APPROVED";
                case "DENIED" -> "RESOLVED_REJECTED";
                default -> null; // CONDITIONAL / unknown → not auto-resolved
            };
            if (terminal == null) {
                log.info("Adjudication decision {} for conflict case {} is {} — not auto-resolved",
                        decisionId, entity.getId(), d);
                return;
            }
            entity.setStatus(terminal);
            entity.setAdjudicationRef(decisionId);
            entity.setReason("Resolved by adjudication decision " + decisionId + " (" + d + ").");
            repository.save(entity);
            log.info("Resolved employment conflict case {} -> {} via decision {}",
                    entity.getId(), terminal, decisionId);
        } catch (Exception e) {
            log.warn("Failed to resolve employment conflict case from decision {} ({}: {}) — decision write preserved",
                    decisionId, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
