package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.api.dto.ReconciliationDecisionRequest;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationCaseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ReconciliationActionRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ReconciliationCaseRepository;

import java.util.Map;

/**
 * Import reconciliation queue service.
 *
 * Manages reconciliation cases created when provider data is imported
 * from external council systems.
 *
 * Case lifecycle:
 *   OPEN -> RESOLVED (case matched and applied)
 *   OPEN -> REJECTED (case discarded)
 *   OPEN -> DEFERRED (requires further investigation)
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationCaseRepository caseRepository;
    private final ReconciliationActionRepository actionRepository;

    public ReconciliationService(ReconciliationCaseRepository caseRepository,
                                 ReconciliationActionRepository actionRepository) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
    }

    // ---- Public API ----

    @Transactional(readOnly = true)
    public Page<ReconciliationCaseEntity> getQueue(String status, Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Fetching reconciliation queue: tenantId={}, status={}", ctx.tenantId(), status);

        String effectiveStatus = (status != null && !status.isBlank()) ? status : "OPEN";
        return caseRepository.findByTenantIdAndStatus(ctx.tenantId(), effectiveStatus, pageable);
    }

    @Transactional
    public ReconciliationCaseEntity decideCase(Long caseId, ReconciliationDecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        String action = request.action().name();
        log.info("Deciding reconciliation case: caseId={}, action={}, actor={}",
                caseId, action, ctx.actorId());

        ReconciliationCaseEntity reconCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reconciliation case not found: " + caseId));

        if (!reconCase.isOpen()) {
            throw new IllegalStateException(
                    "Case is not OPEN: current=" + reconCase.getStatus());
        }

        ReconciliationActionEntity actionEntity = new ReconciliationActionEntity();
        actionEntity.setReconciliationCase(reconCase);
        actionEntity.setTenantId(ctx.tenantId());
        actionEntity.setAction(action);
        actionEntity.setActorId(ctx.actorId());
        actionEntity.setReason(request.reason());
        actionRepository.save(actionEntity);

        String newStatus = switch (action) {
            case "ACCEPT", "MERGE" -> "RESOLVED";
            case "REJECT" -> "REJECTED";
            case "DEFER" -> "DEFERRED";
            default -> throw new IllegalArgumentException(
                    "Invalid action: " + action);
        };

        reconCase.setStatus(newStatus);
        reconCase = caseRepository.save(reconCase);

        log.info("Reconciliation case decided: caseId={}, status={}", caseId, newStatus);

        return reconCase;
    }

    @Transactional
    public ReconciliationCaseEntity createCase(Long councilId, String sourceSystem,
                                                String sourceRef, String caseType,
                                                Map<String, Object> payload) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating reconciliation case: councilId={}, sourceSystem={}, caseType={}, actor={}",
                councilId, sourceSystem, caseType, ctx.actorId());

        ReconciliationCaseEntity reconCase = new ReconciliationCaseEntity();
        reconCase.setTenantId(ctx.tenantId());
        reconCase.setSourceSystem(sourceSystem);
        reconCase.setSourceRef(sourceRef);
        reconCase.setCaseType(caseType);
        reconCase.setStatus("OPEN");
        reconCase.setPayload(payload);

        if (councilId != null) {
            CouncilEntity councilRef = new CouncilEntity();
            councilRef.setId(councilId);
            reconCase.setCouncil(councilRef);
        }

        reconCase = caseRepository.save(reconCase);
        log.info("Reconciliation case created: caseId={}", reconCase.getId());

        return reconCase;
    }
}
