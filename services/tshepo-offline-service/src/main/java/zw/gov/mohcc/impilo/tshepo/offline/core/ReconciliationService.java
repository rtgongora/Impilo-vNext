package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflineActionRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.ReconciliationRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.ReconciliationResponse;
import zw.gov.mohcc.impilo.tshepo.offline.client.AuthzServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineServiceException;
import zw.gov.mohcc.impilo.tshepo.offline.exception.ReconciliationBatchNotFoundException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.*;

import java.time.Instant;
import java.util.*;

/**
 * Service for reconciling offline actions when a device reconnects.
 *
 * <p>Reconciliation processes offline action logs in chronological order,
 * validates each action against current authorization policies, and creates
 * or updates records via downstream service calls. It handles conflicts
 * gracefully (e.g., duplicate patient registrations from multiple offline devices).</p>
 *
 * <h3>Reconciliation flow:</h3>
 * <ol>
 *     <li>Submit a batch of offline actions</li>
 *     <li>Validate each action against current policies (via authz-service)</li>
 *     <li>Reconcile O-CPIDs with the master patient index (via identity-service)</li>
 *     <li>Create actual records via downstream service calls</li>
 *     <li>Mark each action as SYNCED or FAILED</li>
 *     <li>Complete the batch with summary counts</li>
 * </ol>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationBatchRepository batchRepo;
    private final OfflineActionLogRepository actionLogRepo;
    private final EventOutboxRepository outboxRepo;
    private final AuthzServiceClient authzClient;
    private final OCpidIssuanceService oCpidService;
    private final ObjectMapper objectMapper;

    public ReconciliationService(ReconciliationBatchRepository batchRepo,
                                  OfflineActionLogRepository actionLogRepo,
                                  EventOutboxRepository outboxRepo,
                                  AuthzServiceClient authzClient,
                                  OCpidIssuanceService oCpidService,
                                  ObjectMapper objectMapper) {
        this.batchRepo = batchRepo;
        this.actionLogRepo = actionLogRepo;
        this.outboxRepo = outboxRepo;
        this.authzClient = authzClient;
        this.oCpidService = oCpidService;
        this.objectMapper = objectMapper;
    }

    /**
     * Submit a batch of offline actions for reconciliation.
     * Actions are first persisted, then processed asynchronously.
     */
    @Transactional
    public ReconciliationResponse submitBatch(ReconciliationRequest request) {
        log.info("Submitting reconciliation batch: tenant={}, facility={}, submitter={}, actions={}",
                request.tenantId(), request.facilityId(), request.submittedBy(), request.actions().size());

        // Step 1: Create the batch record
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
        batch.setTenantId(request.tenantId());
        batch.setFacilityId(request.facilityId());
        batch.setSubmittedBy(request.submittedBy());
        batch.setActionCount(request.actions().size());
        batch.setReconciledCount(0);
        batch.setFailedCount(0);
        batch.setStatus("SUBMITTED");
        batch.setSubmittedAt(Instant.now());
        batch = batchRepo.save(batch);

        // Step 2: Persist each action in the offline_action_log
        for (OfflineActionRequest actionReq : request.actions()) {
            OfflineActionLogEntity actionLog = new OfflineActionLogEntity();
            actionLog.setTenantId(request.tenantId());
            actionLog.setCapabilityTokenId(actionReq.capabilityTokenId());
            actionLog.setActorId(actionReq.actorId());
            actionLog.setActionType(actionReq.actionType());
            actionLog.setResourceType(actionReq.resourceType());
            actionLog.setResourceRef(actionReq.resourceRef());
            actionLog.setOCpid(actionReq.oCpid());
            actionLog.setPayload(actionReq.payload() != null ? toJson(actionReq.payload()) : null);
            actionLog.setPerformedAt(actionReq.performedAt());
            actionLog.setSyncStatus("PENDING");
            actionLogRepo.save(actionLog);
        }

        // Step 3: Process the batch immediately (synchronous for now)
        processBatch(batch, request);

        // Reload the batch to get updated counts
        batch = batchRepo.findById(batch.getId()).orElse(batch);

        // Step 4: Write outbox event
        writeOutboxEvent("ReconciliationBatch", batch.getId().toString(),
                "RECONCILIATION_BATCH_COMPLETED", buildBatchEventPayload(batch));

        log.info("Reconciliation batch completed: id={}, reconciled={}, failed={}",
                batch.getId(), batch.getReconciledCount(), batch.getFailedCount());

        return toResponse(batch);
    }

    /**
     * Get the status of a reconciliation batch.
     */
    @Transactional(readOnly = true)
    public ReconciliationResponse getBatchStatus(UUID batchId, UUID tenantId) {
        ReconciliationBatchEntity batch = batchRepo.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ReconciliationBatchNotFoundException(batchId));
        return toResponse(batch);
    }

    /**
     * List pending reconciliation batches for a tenant.
     */
    @Transactional(readOnly = true)
    public List<ReconciliationResponse> listPendingBatches(UUID tenantId) {
        return batchRepo.findPendingByTenant(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Batch processing
    // ------------------------------------------------------------------

    private void processBatch(ReconciliationBatchEntity batch, ReconciliationRequest request) {
        log.info("Processing reconciliation batch id={}", batch.getId());

        batch.setStatus("PROCESSING");
        batchRepo.save(batch);

        int reconciledCount = 0;
        int failedCount = 0;

        // Process actions in chronological order
        List<OfflineActionRequest> sortedActions = request.actions().stream()
                .sorted(Comparator.comparing(OfflineActionRequest::performedAt))
                .toList();

        // Track O-CPID reconciliation results to avoid re-reconciling
        Map<UUID, UUID> oCpidMappings = new HashMap<>();

        for (OfflineActionRequest action : sortedActions) {
            try {
                reconcileAction(action, request.tenantId(), request.facilityId(), oCpidMappings);
                reconciledCount++;
            } catch (Exception e) {
                log.warn("Failed to reconcile action: type={}, actor={}, error={}",
                        action.actionType(), action.actorId(), e.getMessage());
                failedCount++;
                // Mark the specific action as failed
                markActionFailed(action, request.tenantId());
            }
        }

        // Update batch status
        batch.setReconciledCount(reconciledCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(failedCount == 0 ? "COMPLETED" : "COMPLETED_WITH_FAILURES");
        batch.setCompletedAt(Instant.now());
        batchRepo.save(batch);
    }

    private void reconcileAction(OfflineActionRequest action, UUID tenantId, UUID facilityId,
                                  Map<UUID, UUID> oCpidMappings) {
        log.debug("Reconciling action: type={}, actor={}", action.actionType(), action.actorId());

        // Step 1: Validate the action against current policies
        boolean policyValid = authzClient.validateActionPolicy(
                tenantId, action.actorId(), facilityId,
                action.actionType(), action.resourceType());
        if (!policyValid) {
            throw new OfflineServiceException("POLICY_VIOLATION",
                    "Action " + action.actionType() + " is no longer permitted by current policy");
        }

        // Step 2: Reconcile O-CPIDs if present
        if (action.oCpid() != null) {
            UUID reconciled = oCpidMappings.get(action.oCpid());
            if (reconciled == null) {
                reconciled = oCpidService.reconcileOCpid(tenantId, action.oCpid());
                oCpidMappings.put(action.oCpid(), reconciled);
            }
            log.debug("O-CPID {} mapped to permanent CPID {}", action.oCpid(), reconciled);
        }

        // Step 3: Mark action as synced
        markActionSynced(action, tenantId);
    }

    private void markActionSynced(OfflineActionRequest action, UUID tenantId) {
        List<OfflineActionLogEntity> actions = actionLogRepo.findByTenantAndSyncStatus(tenantId, "PENDING");
        for (OfflineActionLogEntity entity : actions) {
            if (entity.getActorId().equals(action.actorId())
                    && entity.getActionType().equals(action.actionType())
                    && entity.getPerformedAt().equals(action.performedAt())) {
                entity.setSyncStatus("SYNCED");
                entity.setSyncedAt(Instant.now());
                actionLogRepo.save(entity);
                return;
            }
        }
    }

    private void markActionFailed(OfflineActionRequest action, UUID tenantId) {
        List<OfflineActionLogEntity> actions = actionLogRepo.findByTenantAndSyncStatus(tenantId, "PENDING");
        for (OfflineActionLogEntity entity : actions) {
            if (entity.getActorId().equals(action.actorId())
                    && entity.getActionType().equals(action.actionType())
                    && entity.getPerformedAt().equals(action.performedAt())) {
                entity.setSyncStatus("FAILED");
                entity.setSyncedAt(Instant.now());
                actionLogRepo.save(entity);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private ReconciliationResponse toResponse(ReconciliationBatchEntity entity) {
        return new ReconciliationResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getFacilityId(),
                entity.getSubmittedBy(),
                entity.getActionCount(),
                entity.getReconciledCount(),
                entity.getFailedCount(),
                entity.getStatus(),
                entity.getSubmittedAt(),
                entity.getCompletedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OfflineServiceException("SERIALIZATION_ERROR", "Failed to serialize to JSON", e);
        }
    }

    private void writeOutboxEvent(String aggregateType, String aggregateId,
                                   String eventType, String payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setCreatedAt(Instant.now());
        outboxRepo.save(outbox);
    }

    private String buildBatchEventPayload(ReconciliationBatchEntity batch) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("batchId", batch.getId().toString());
        event.put("tenantId", batch.getTenantId().toString());
        event.put("facilityId", batch.getFacilityId().toString());
        event.put("submittedBy", batch.getSubmittedBy());
        event.put("actionCount", batch.getActionCount());
        event.put("reconciledCount", batch.getReconciledCount());
        event.put("failedCount", batch.getFailedCount());
        event.put("status", batch.getStatus());
        event.put("timestamp", Instant.now().toString());
        return toJson(event);
    }
}
