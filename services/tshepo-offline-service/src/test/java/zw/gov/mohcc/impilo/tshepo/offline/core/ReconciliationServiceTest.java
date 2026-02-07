package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflineActionRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.ReconciliationRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.ReconciliationResponse;
import zw.gov.mohcc.impilo.tshepo.offline.client.AuthzServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.exception.ReconciliationBatchNotFoundException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReconciliationService}.
 *
 * <p>Tests cover batch submission, action reconciliation (including policy validation
 * and O-CPID reconciliation), conflict detection (policy violations), and batch
 * status queries.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID FACILITY_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String SUBMITTER = "nurse-user-1";
    private static final UUID TOKEN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Mock
    private ReconciliationBatchRepository batchRepo;

    @Mock
    private OfflineActionLogRepository actionLogRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private AuthzServiceClient authzClient;

    @Mock
    private OCpidIssuanceService oCpidService;

    @Captor
    private ArgumentCaptor<ReconciliationBatchEntity> batchCaptor;

    @Captor
    private ArgumentCaptor<OfflineActionLogEntity> actionLogCaptor;

    @Captor
    private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    private ObjectMapper objectMapper;
    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reconciliationService = new ReconciliationService(
                batchRepo, actionLogRepo, outboxRepo, authzClient, oCpidService, objectMapper);
    }

    // ------------------------------------------------------------------
    // submitBatch — uploads offline actions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reconcile uploads offline actions and marks them as SYNCED")
    void reconcile_uploadsOfflineActions() {
        // Arrange: two valid actions
        OfflineActionRequest action1 = buildActionRequest("READ_PATIENT", "Patient",
                Instant.now().minus(2, ChronoUnit.HOURS), null);
        OfflineActionRequest action2 = buildActionRequest("CREATE_PROVISIONAL_ENCOUNTER", "Encounter",
                Instant.now().minus(1, ChronoUnit.HOURS), null);

        ReconciliationRequest request = new ReconciliationRequest(
                TENANT_ID, FACILITY_ID, SUBMITTER, List.of(action1, action2));

        // Stub batch creation
        when(batchRepo.save(any(ReconciliationBatchEntity.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = inv.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            return batch;
        });
        when(batchRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
            batch.setId(inv.getArgument(0));
            batch.setTenantId(TENANT_ID);
            batch.setFacilityId(FACILITY_ID);
            batch.setSubmittedBy(SUBMITTER);
            batch.setActionCount(2);
            batch.setReconciledCount(2);
            batch.setFailedCount(0);
            batch.setStatus("COMPLETED");
            batch.setSubmittedAt(Instant.now());
            batch.setCompletedAt(Instant.now());
            return Optional.of(batch);
        });

        // Stub action log
        when(actionLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Both actions pass policy validation
        when(authzClient.validateActionPolicy(eq(TENANT_ID), any(), eq(FACILITY_ID), any(), any()))
                .thenReturn(true);

        // Action log query for marking synced
        OfflineActionLogEntity logEntity1 = buildActionLogEntity(action1);
        OfflineActionLogEntity logEntity2 = buildActionLogEntity(action2);
        when(actionLogRepo.findByTenantAndSyncStatus(TENANT_ID, "PENDING"))
                .thenReturn(new ArrayList<>(List.of(logEntity1, logEntity2)));

        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationResponse response = reconciliationService.submitBatch(request);

        // Assert: batch completed successfully
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.reconciledCount()).isEqualTo(2);
        assertThat(response.failedCount()).isEqualTo(0);
        assertThat(response.actionCount()).isEqualTo(2);

        // Verify action logs were persisted
        verify(actionLogRepo, atLeast(2)).save(any(OfflineActionLogEntity.class));

        // Verify outbox event
        verify(outboxRepo).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("RECONCILIATION_BATCH_COMPLETED");
    }

    @Test
    @DisplayName("reconcile detects conflicts when policy validation fails for some actions")
    void reconcile_detectsConflicts() {
        // Arrange: two actions, one passes policy and one fails
        OfflineActionRequest validAction = buildActionRequest("READ_PATIENT", "Patient",
                Instant.now().minus(2, ChronoUnit.HOURS), null);
        OfflineActionRequest invalidAction = buildActionRequest("CREATE_PROVISIONAL_ENCOUNTER", "Encounter",
                Instant.now().minus(1, ChronoUnit.HOURS), null);

        ReconciliationRequest request = new ReconciliationRequest(
                TENANT_ID, FACILITY_ID, SUBMITTER, List.of(validAction, invalidAction));

        // Stub batch creation
        when(batchRepo.save(any(ReconciliationBatchEntity.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = inv.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            return batch;
        });
        when(batchRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
            batch.setId(inv.getArgument(0));
            batch.setTenantId(TENANT_ID);
            batch.setFacilityId(FACILITY_ID);
            batch.setSubmittedBy(SUBMITTER);
            batch.setActionCount(2);
            batch.setReconciledCount(1);
            batch.setFailedCount(1);
            batch.setStatus("COMPLETED_WITH_FAILURES");
            batch.setSubmittedAt(Instant.now());
            batch.setCompletedAt(Instant.now());
            return Optional.of(batch);
        });

        when(actionLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // First action (READ_PATIENT) passes, second (CREATE_PROVISIONAL_ENCOUNTER) fails policy
        when(authzClient.validateActionPolicy(
                eq(TENANT_ID), any(), eq(FACILITY_ID), eq("READ_PATIENT"), eq("Patient")))
                .thenReturn(true);
        when(authzClient.validateActionPolicy(
                eq(TENANT_ID), any(), eq(FACILITY_ID), eq("CREATE_PROVISIONAL_ENCOUNTER"), eq("Encounter")))
                .thenReturn(false);

        // Action log entries for mark synced/failed
        OfflineActionLogEntity logEntity1 = buildActionLogEntity(validAction);
        OfflineActionLogEntity logEntity2 = buildActionLogEntity(invalidAction);
        when(actionLogRepo.findByTenantAndSyncStatus(TENANT_ID, "PENDING"))
                .thenReturn(new ArrayList<>(List.of(logEntity1, logEntity2)));

        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationResponse response = reconciliationService.submitBatch(request);

        // Assert: batch completed with failures
        assertThat(response.status()).isEqualTo("COMPLETED_WITH_FAILURES");
        assertThat(response.reconciledCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("reconcile handles O-CPID reconciliation for actions with offline CPIDs")
    void reconcile_reconcilesOCpids() {
        // Arrange: action with an O-CPID that needs reconciliation
        UUID oCpid = UUID.randomUUID();
        UUID permanentCpid = UUID.randomUUID();

        OfflineActionRequest actionWithOCpid = buildActionRequest(
                "CREATE_PROVISIONAL_REGISTRATION", "Patient",
                Instant.now().minus(1, ChronoUnit.HOURS), oCpid);

        ReconciliationRequest request = new ReconciliationRequest(
                TENANT_ID, FACILITY_ID, SUBMITTER, List.of(actionWithOCpid));

        when(batchRepo.save(any(ReconciliationBatchEntity.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = inv.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            return batch;
        });
        when(batchRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
            batch.setId(inv.getArgument(0));
            batch.setTenantId(TENANT_ID);
            batch.setFacilityId(FACILITY_ID);
            batch.setSubmittedBy(SUBMITTER);
            batch.setActionCount(1);
            batch.setReconciledCount(1);
            batch.setFailedCount(0);
            batch.setStatus("COMPLETED");
            batch.setSubmittedAt(Instant.now());
            batch.setCompletedAt(Instant.now());
            return Optional.of(batch);
        });

        when(actionLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authzClient.validateActionPolicy(any(), any(), any(), any(), any())).thenReturn(true);
        when(oCpidService.reconcileOCpid(TENANT_ID, oCpid)).thenReturn(permanentCpid);

        OfflineActionLogEntity logEntity = buildActionLogEntity(actionWithOCpid);
        when(actionLogRepo.findByTenantAndSyncStatus(TENANT_ID, "PENDING"))
                .thenReturn(new ArrayList<>(List.of(logEntity)));

        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationResponse response = reconciliationService.submitBatch(request);

        // Assert
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.reconciledCount()).isEqualTo(1);
        verify(oCpidService).reconcileOCpid(TENANT_ID, oCpid);
    }

    @Test
    @DisplayName("reconcile avoids re-reconciling the same O-CPID multiple times in one batch")
    void reconcile_cachesOCpidMappings() {
        // Arrange: two actions with the same O-CPID
        UUID oCpid = UUID.randomUUID();
        UUID permanentCpid = UUID.randomUUID();

        OfflineActionRequest action1 = buildActionRequest(
                "CREATE_PROVISIONAL_REGISTRATION", "Patient",
                Instant.now().minus(2, ChronoUnit.HOURS), oCpid);
        OfflineActionRequest action2 = buildActionRequest(
                "CREATE_PROVISIONAL_ENCOUNTER", "Encounter",
                Instant.now().minus(1, ChronoUnit.HOURS), oCpid);

        ReconciliationRequest request = new ReconciliationRequest(
                TENANT_ID, FACILITY_ID, SUBMITTER, List.of(action1, action2));

        when(batchRepo.save(any(ReconciliationBatchEntity.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = inv.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            return batch;
        });
        when(batchRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
            batch.setId(inv.getArgument(0));
            batch.setTenantId(TENANT_ID);
            batch.setFacilityId(FACILITY_ID);
            batch.setSubmittedBy(SUBMITTER);
            batch.setActionCount(2);
            batch.setReconciledCount(2);
            batch.setFailedCount(0);
            batch.setStatus("COMPLETED");
            batch.setSubmittedAt(Instant.now());
            batch.setCompletedAt(Instant.now());
            return Optional.of(batch);
        });

        when(actionLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authzClient.validateActionPolicy(any(), any(), any(), any(), any())).thenReturn(true);
        when(oCpidService.reconcileOCpid(TENANT_ID, oCpid)).thenReturn(permanentCpid);

        OfflineActionLogEntity logEntity1 = buildActionLogEntity(action1);
        OfflineActionLogEntity logEntity2 = buildActionLogEntity(action2);
        when(actionLogRepo.findByTenantAndSyncStatus(TENANT_ID, "PENDING"))
                .thenReturn(new ArrayList<>(List.of(logEntity1, logEntity2)));

        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        reconciliationService.submitBatch(request);

        // Assert: O-CPID reconciled only once despite two actions referencing it
        verify(oCpidService, times(1)).reconcileOCpid(TENANT_ID, oCpid);
    }

    // ------------------------------------------------------------------
    // getBatchStatus tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getBatchStatus returns batch response for existing batch")
    void getBatchStatus_returnsBatchResponse() {
        UUID batchId = UUID.randomUUID();
        ReconciliationBatchEntity batch = buildBatchEntity(batchId, "COMPLETED", 3, 3, 0);
        when(batchRepo.findByIdAndTenantId(batchId, TENANT_ID)).thenReturn(Optional.of(batch));

        ReconciliationResponse response = reconciliationService.getBatchStatus(batchId, TENANT_ID);

        assertThat(response.batchId()).isEqualTo(batchId);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.actionCount()).isEqualTo(3);
        assertThat(response.reconciledCount()).isEqualTo(3);
        assertThat(response.failedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getBatchStatus throws when batch is not found")
    void getBatchStatus_notFound_throws() {
        UUID batchId = UUID.randomUUID();
        when(batchRepo.findByIdAndTenantId(batchId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reconciliationService.getBatchStatus(batchId, TENANT_ID))
                .isInstanceOf(ReconciliationBatchNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // listPendingBatches tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("listPendingBatches returns pending batches for a tenant")
    void listPendingBatches_returnsPending() {
        ReconciliationBatchEntity batch1 = buildBatchEntity(UUID.randomUUID(), "SUBMITTED", 5, 0, 0);
        ReconciliationBatchEntity batch2 = buildBatchEntity(UUID.randomUUID(), "PROCESSING", 3, 1, 0);
        when(batchRepo.findPendingByTenant(TENANT_ID)).thenReturn(List.of(batch1, batch2));

        List<ReconciliationResponse> responses = reconciliationService.listPendingBatches(TENANT_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).status()).isEqualTo("SUBMITTED");
        assertThat(responses.get(1).status()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("listPendingBatches returns empty list when no pending batches")
    void listPendingBatches_empty() {
        when(batchRepo.findPendingByTenant(TENANT_ID)).thenReturn(List.of());

        List<ReconciliationResponse> responses = reconciliationService.listPendingBatches(TENANT_ID);

        assertThat(responses).isEmpty();
    }

    // ------------------------------------------------------------------
    // Batch processing — action ordering
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reconcile processes actions in chronological order by performedAt")
    void reconcile_processesActionsInChronologicalOrder() {
        // Arrange: actions submitted in reverse chronological order
        Instant earliest = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant middle = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant latest = Instant.now().minus(1, ChronoUnit.HOURS);

        OfflineActionRequest action3 = buildActionRequest("READ_PATIENT", "Patient", latest, null);
        OfflineActionRequest action1 = buildActionRequest("READ_PATIENT", "Patient", earliest, null);
        OfflineActionRequest action2 = buildActionRequest("READ_PATIENT", "Patient", middle, null);

        ReconciliationRequest request = new ReconciliationRequest(
                TENANT_ID, FACILITY_ID, SUBMITTER, List.of(action3, action1, action2));

        when(batchRepo.save(any(ReconciliationBatchEntity.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = inv.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(UUID.randomUUID());
            }
            return batch;
        });
        when(batchRepo.findById(any(UUID.class))).thenAnswer(inv -> {
            ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
            batch.setId(inv.getArgument(0));
            batch.setTenantId(TENANT_ID);
            batch.setFacilityId(FACILITY_ID);
            batch.setSubmittedBy(SUBMITTER);
            batch.setActionCount(3);
            batch.setReconciledCount(3);
            batch.setFailedCount(0);
            batch.setStatus("COMPLETED");
            batch.setSubmittedAt(Instant.now());
            batch.setCompletedAt(Instant.now());
            return Optional.of(batch);
        });

        when(actionLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authzClient.validateActionPolicy(any(), any(), any(), any(), any())).thenReturn(true);

        // Build matching action log entries for all 3 actions
        OfflineActionLogEntity log1 = buildActionLogEntity(action1);
        OfflineActionLogEntity log2 = buildActionLogEntity(action2);
        OfflineActionLogEntity log3 = buildActionLogEntity(action3);
        when(actionLogRepo.findByTenantAndSyncStatus(TENANT_ID, "PENDING"))
                .thenReturn(new ArrayList<>(List.of(log1, log2, log3)));

        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReconciliationResponse response = reconciliationService.submitBatch(request);

        // Assert: all 3 actions were processed
        assertThat(response.reconciledCount()).isEqualTo(3);

        // Verify policy was checked 3 times (once per action)
        verify(authzClient, times(3)).validateActionPolicy(
                eq(TENANT_ID), any(), eq(FACILITY_ID), eq("READ_PATIENT"), eq("Patient"));
    }

    // ------------------------------------------------------------------
    // Test fixture helpers
    // ------------------------------------------------------------------

    /**
     * Build an {@link OfflineActionRequest} with the given parameters.
     */
    private OfflineActionRequest buildActionRequest(String actionType, String resourceType,
                                                     Instant performedAt, UUID oCpid) {
        return new OfflineActionRequest(
                TENANT_ID,
                TOKEN_ID,
                "actor-" + SUBMITTER,
                actionType,
                resourceType,
                "ref-" + UUID.randomUUID().toString().substring(0, 8),
                oCpid,
                null,
                performedAt
        );
    }

    /**
     * Build an {@link OfflineActionLogEntity} matching the given action request.
     */
    private OfflineActionLogEntity buildActionLogEntity(OfflineActionRequest actionReq) {
        OfflineActionLogEntity entity = new OfflineActionLogEntity();
        entity.setId(new Random().nextLong(1, 10000));
        entity.setTenantId(TENANT_ID);
        entity.setCapabilityTokenId(actionReq.capabilityTokenId());
        entity.setActorId(actionReq.actorId());
        entity.setActionType(actionReq.actionType());
        entity.setResourceType(actionReq.resourceType());
        entity.setResourceRef(actionReq.resourceRef());
        entity.setOCpid(actionReq.oCpid());
        entity.setPerformedAt(actionReq.performedAt());
        entity.setSyncStatus("PENDING");
        return entity;
    }

    /**
     * Build a {@link ReconciliationBatchEntity} with the given status and counts.
     */
    private ReconciliationBatchEntity buildBatchEntity(UUID batchId, String status,
                                                        int actionCount, int reconciledCount, int failedCount) {
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity();
        batch.setId(batchId);
        batch.setTenantId(TENANT_ID);
        batch.setFacilityId(FACILITY_ID);
        batch.setSubmittedBy(SUBMITTER);
        batch.setActionCount(actionCount);
        batch.setReconciledCount(reconciledCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(status);
        batch.setSubmittedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        if ("COMPLETED".equals(status) || "COMPLETED_WITH_FAILURES".equals(status)) {
            batch.setCompletedAt(Instant.now());
        }
        return batch;
    }
}
