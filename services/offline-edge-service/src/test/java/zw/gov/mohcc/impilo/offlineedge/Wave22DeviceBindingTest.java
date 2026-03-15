package zw.gov.mohcc.impilo.offlineedge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.offlineedge.api.dto.IssueEntitlementRequest;
import zw.gov.mohcc.impilo.offlineedge.client.ButanoFhirClient;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineEdgeService;
import zw.gov.mohcc.impilo.offlineedge.domain.*;
import zw.gov.mohcc.impilo.offlineedge.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave 22 tests: Device binding on entitlements.
 */
class Wave22DeviceBindingTest {

    private EntitlementRepository entitlementRepo;
    private CapturedActionRepository actionRepo;
    private ReconciliationBatchRepository batchRepo;
    private OutboxEventRepository outboxRepo;
    private ConflictReviewRepository conflictRepo;
    private OfflineEdgeService service;

    @BeforeEach
    void setUp() {
        entitlementRepo = mock(EntitlementRepository.class);
        actionRepo = mock(CapturedActionRepository.class);
        batchRepo = mock(ReconciliationBatchRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        conflictRepo = mock(ConflictReviewRepository.class);
        ButanoFhirClient butanoClient = mock(ButanoFhirClient.class);

        service = new OfflineEdgeService(
                entitlementRepo, actionRepo, batchRepo, outboxRepo, conflictRepo,
                butanoClient, new ObjectMapper(), "test-hmac-secret", 24);
    }

    @Nested
    @DisplayName("Entitlement device binding")
    class DeviceBinding {

        @Test
        @DisplayName("Entitlement stores device fingerprint")
        void entitlementStoresDeviceFingerprint() {
            when(entitlementRepo.save(any(EntitlementEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IssueEntitlementRequest request = new IssueEntitlementRequest(
                    "actor-1", "facility-abc", "CAPTURE_VITALS", null,
                    "device-fp-sha256-abc123", 30);

            EntitlementEntity result = service.issueEntitlement(
                    UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request);

            assertThat(result.getDeviceFingerprint()).isEqualTo("device-fp-sha256-abc123");
            assertThat(result.getMaxOfflineEncounters()).isEqualTo(30);
        }

        @Test
        @DisplayName("Entitlement defaults max encounters to 50")
        void defaultMaxEncounters() {
            when(entitlementRepo.save(any(EntitlementEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IssueEntitlementRequest request = new IssueEntitlementRequest(
                    "actor-1", "facility-abc", "CAPTURE_VITALS", null,
                    "device-fp", null);

            EntitlementEntity result = service.issueEntitlement(
                    UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request);

            assertThat(result.getMaxOfflineEncounters()).isEqualTo(50);
        }

        @Test
        @DisplayName("Entitlement without device fingerprint is allowed")
        void noDeviceFingerprintAllowed() {
            when(entitlementRepo.save(any(EntitlementEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IssueEntitlementRequest request = new IssueEntitlementRequest(
                    "actor-1", "facility-abc", "CAPTURE_VITALS", null, null, null);

            EntitlementEntity result = service.issueEntitlement(
                    UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request);

            assertThat(result.getDeviceFingerprint()).isNull();
        }

        @Test
        @DisplayName("Device fingerprint included in audit event payload")
        void deviceFingerprintInAuditEvent() {
            when(entitlementRepo.save(any(EntitlementEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            IssueEntitlementRequest request = new IssueEntitlementRequest(
                    "actor-1", "facility-abc", "CAPTURE_VITALS", null,
                    "device-fp-xyz", 20);

            service.issueEntitlement(UUID.randomUUID(), "pod-1", "corr-1", "idem-1", request);

            verify(outboxRepo).save(argThat(e -> {
                if (!"impilo.offline.entitlement.issued.v1".equals(e.getEventType())) return false;
                String payload = e.getPayloadJson();
                return payload.contains("device-fp-xyz") && payload.contains("\"max_offline_encounters\":20");
            }));
        }
    }
}
