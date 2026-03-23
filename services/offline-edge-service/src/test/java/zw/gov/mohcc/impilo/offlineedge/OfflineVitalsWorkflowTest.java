package zw.gov.mohcc.impilo.offlineedge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.offlineedge.api.dto.CaptureActionRequest;
import zw.gov.mohcc.impilo.offlineedge.api.dto.IssueEntitlementRequest;
import zw.gov.mohcc.impilo.offlineedge.client.ButanoFhirClient;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineEdgeService;
import zw.gov.mohcc.impilo.offlineedge.domain.*;
import zw.gov.mohcc.impilo.offlineedge.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the offline vitals end-to-end workflow:
 * 1. Entitlement issuance required
 * 2. Local capture store
 * 3. Replay marks actions as sent
 * 4. Conflict detection and review queue
 * 5. Correct audit events emitted
 */
class OfflineVitalsWorkflowTest {

    private EntitlementRepository entitlementRepo;
    private CapturedActionRepository actionRepo;
    private ReconciliationBatchRepository batchRepo;
    private OutboxEventRepository outboxRepo;
    private ConflictReviewRepository conflictRepo;
    private ButanoFhirClient butanoClient;
    private OfflineEdgeService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        entitlementRepo = mock(EntitlementRepository.class);
        actionRepo = mock(CapturedActionRepository.class);
        batchRepo = mock(ReconciliationBatchRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        conflictRepo = mock(ConflictReviewRepository.class);
        butanoClient = mock(ButanoFhirClient.class);
        objectMapper = new ObjectMapper();

        service = new OfflineEdgeService(
                entitlementRepo, actionRepo, batchRepo, outboxRepo, conflictRepo,
                butanoClient, objectMapper, "test-hmac-secret", 24
        );
    }

    @Nested
    @DisplayName("Entitlement issuance")
    class EntitlementTests {

        @Test
        @DisplayName("Issues entitlement with correct expiry and emits audit event")
        void issueEntitlement() {
            when(entitlementRepo.save(any(EntitlementEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IssueEntitlementRequest request = new IssueEntitlementRequest(
                    "actor-1", "facility-abc", "CAPTURE_VITALS", null, null, null);

            UUID tenantId = UUID.randomUUID();
            EntitlementEntity result = service.issueEntitlement(
                    tenantId, "pod-1", "corr-1", "idem-1", request);

            assertThat(result).isNotNull();
            assertThat(result.getActorId()).isEqualTo("actor-1");
            assertThat(result.getWorkflowType()).isEqualTo("CAPTURE_VITALS");
            assertThat(result.getExpiresAt()).isAfter(OffsetDateTime.now());
            assertThat(result.isRevoked()).isFalse();

            // Verify outbox event was created
            verify(outboxRepo).save(argThat(e ->
                    "impilo.offline.entitlement.issued.v1".equals(e.getEventType())
            ));
        }
    }

    @Nested
    @DisplayName("Capture action")
    class CaptureTests {

        @Test
        @DisplayName("Capture requires valid entitlement")
        void captureRequiresEntitlement() {
            when(entitlementRepo.findById(any(UUID.class))).thenReturn(Optional.empty());

            UUID entitlementId = UUID.randomUUID();
            CaptureActionRequest request = new CaptureActionRequest(
                    entitlementId, "cpid-123", "VITAL_SIGN",
                    Map.of("code", "8867-4", "value", 72, "unit", "bpm"),
                    OffsetDateTime.now().toString(), "device-1", 1);

            assertThatThrownBy(() -> service.captureAction(
                    UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request))
                    .isInstanceOf(OfflineEdgeService.InvalidEntitlementException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Capture rejects expired entitlement")
        void captureRejectsExpired() {
            UUID entitlementId = UUID.randomUUID();
            EntitlementEntity expired = new EntitlementEntity(
                    entitlementId, UUID.randomUUID(), "actor-1", "facility-1",
                    "CAPTURE_VITALS", "{}", "hash",
                    OffsetDateTime.now().minusHours(1) // already expired
            );
            when(entitlementRepo.findById(entitlementId)).thenReturn(Optional.of(expired));

            CaptureActionRequest request = new CaptureActionRequest(
                    entitlementId, "cpid-123", "VITAL_SIGN",
                    Map.of("code", "8867-4"), OffsetDateTime.now().toString(), "device-1", 1);

            assertThatThrownBy(() -> service.captureAction(
                    UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request))
                    .isInstanceOf(OfflineEdgeService.InvalidEntitlementException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Capture stores action locally with QUEUED status")
        void captureStoresLocally() {
            UUID entitlementId = UUID.randomUUID();
            EntitlementEntity valid = new EntitlementEntity(
                    entitlementId, UUID.randomUUID(), "actor-1", "facility-1",
                    "CAPTURE_VITALS", "{}", "hash",
                    OffsetDateTime.now().plusHours(12)
            );
            when(entitlementRepo.findById(entitlementId)).thenReturn(Optional.of(valid));
            when(actionRepo.save(any(CapturedActionEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CaptureActionRequest request = new CaptureActionRequest(
                    entitlementId, "cpid-123", "VITAL_SIGN",
                    Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-1", 1);

            CapturedActionEntity result = service.captureAction(
                    UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request);

            assertThat(result.getStatus()).isEqualTo("QUEUED");
            assertThat(result.getPatientRef()).isEqualTo("cpid-123");
            assertThat(result.getActionType()).isEqualTo("VITAL_SIGN");

            // Verify audit event emitted
            verify(outboxRepo).save(argThat(e ->
                    "impilo.offline.action.recorded.v1".equals(e.getEventType())
            ));
        }
    }

    @Nested
    @DisplayName("Replay")
    class ReplayTests {

        @Test
        @DisplayName("Replay marks actions as REPLAYED and calls BUTANO")
        void replayMarksAsSent() {
            UUID entitlementId = UUID.randomUUID();
            CapturedActionEntity action = createQueuedAction(entitlementId, "CAPTURE_VITALS");

            when(actionRepo.findByEntitlementIdAndStatusOrderBySequenceNumAsc(entitlementId, "QUEUED"))
                    .thenReturn(List.of(action));
            when(batchRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(butanoClient.postObservation(anyString(), anyMap(), anyString(), anyString()))
                    .thenReturn(ButanoFhirClient.FhirReplayResult.success("created"));

            UUID tenantId = UUID.randomUUID();
            ReconciliationBatchEntity result = service.replayActions(
                    entitlementId, tenantId, "pod-1", "corr-1");

            assertThat(result.getStatus()).isEqualTo("COMPLETED");
            assertThat(result.getReplayedCount()).isEqualTo(1);

            // Verify BUTANO was called
            verify(butanoClient).postObservation(eq("cpid-123"), anyMap(), anyString(), anyString());

            // Verify replay audit event emitted
            verify(outboxRepo, atLeastOnce()).save(argThat(e ->
                    "impilo.offline.action.replayed.v1".equals(e.getEventType())
            ));
        }

        @Test
        @DisplayName("Replay creates conflict entry when BUTANO returns 409")
        void replayCreatesConflict() {
            UUID entitlementId = UUID.randomUUID();
            CapturedActionEntity action = createQueuedAction(entitlementId, "CAPTURE_VITALS");

            when(actionRepo.findByEntitlementIdAndStatusOrderBySequenceNumAsc(entitlementId, "QUEUED"))
                    .thenReturn(List.of(action));
            when(batchRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(butanoClient.postObservation(anyString(), anyMap(), anyString(), anyString()))
                    .thenReturn(ButanoFhirClient.FhirReplayResult.conflict("Duplicate observation"));

            UUID tenantId = UUID.randomUUID();
            ReconciliationBatchEntity result = service.replayActions(
                    entitlementId, tenantId, "pod-1", "corr-1");

            assertThat(result.getStatus()).isEqualTo("CONFLICTS_PENDING");

            // Verify conflict was saved
            verify(conflictRepo).save(argThat(c ->
                    "cpid-123".equals(c.getPatientRef()) &&
                            c.getConflictReason().contains("Duplicate")
            ));

            // Verify conflict audit event emitted
            verify(outboxRepo, atLeastOnce()).save(argThat(e ->
                    "impilo.offline.action.conflict.v1".equals(e.getEventType())
            ));
        }

        private CapturedActionEntity createQueuedAction(UUID entitlementId, String actionType) {
            UUID actionId = UUID.randomUUID();
            CapturedActionEntity action = new CapturedActionEntity(
                    actionId, entitlementId, UUID.randomUUID(), "actor-1",
                    "cpid-123", actionType,
                    "{\"code\":\"8867-4\",\"value\":72,\"unit\":\"bpm\",\"display\":\"Heart rate\",\"effective_date_time\":\"2026-03-14T10:00:00Z\"}",
                    OffsetDateTime.now().minusHours(2), "device-1", 1
            );
            action.setStatus("QUEUED");
            return action;
        }
    }
}
