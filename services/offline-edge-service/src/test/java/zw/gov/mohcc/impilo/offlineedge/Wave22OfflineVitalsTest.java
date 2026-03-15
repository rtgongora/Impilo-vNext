package zw.gov.mohcc.impilo.offlineedge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlement;
import zw.gov.mohcc.impilo.offlineedge.api.dto.CaptureVitalsRequest;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineVitalsService;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineVitalsService.MaxEncountersExceededException;
import zw.gov.mohcc.impilo.offlineedge.domain.CapturedActionEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.CapturedActionRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Wave 22 tests: Offline vitals capture with hash chain, max encounters,
 * and break-glass integration.
 */
class Wave22OfflineVitalsTest {

    private CapturedActionRepository actionRepo;
    private OutboxEventRepository outboxRepo;
    private OfflineVitalsService vitalsService;

    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID FACILITY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        actionRepo = mock(CapturedActionRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        vitalsService = new OfflineVitalsService(actionRepo, outboxRepo, new ObjectMapper());
    }

    private OfflineEntitlement createEntitlement(int maxEncounters) {
        return new OfflineEntitlement(
                TOKEN_ID, "tshepo-offline-service", "nurse-001", "impilo-offline",
                TENANT_ID, FACILITY_ID, "device-fp-abc",
                List.of("CAPTURE_VITALS"), maxEncounters,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600)
        );
    }

    @Nested
    @DisplayName("Max encounters enforcement")
    class MaxEncounters {

        @Test
        @DisplayName("Capture succeeds when under limit")
        void captureUnderLimit() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(5L);
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, false, null);

            Map<String, Object> result = vitalsService.captureVital(ent, request);
            assertThat(result).containsEntry("status", "QUEUED");
        }

        @Test
        @DisplayName("Capture blocked when at limit")
        void captureAtLimit() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(50L);

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, false, null);

            assertThatThrownBy(() -> vitalsService.captureVital(ent, request))
                    .isInstanceOf(MaxEncountersExceededException.class)
                    .hasMessageContaining("50");
        }

        @Test
        @DisplayName("Zero max encounters means unlimited")
        void zeroMeansUnlimited() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(1000L);
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(0);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, false, null);

            Map<String, Object> result = vitalsService.captureVital(ent, request);
            assertThat(result).containsEntry("status", "QUEUED");
        }
    }

    @Nested
    @DisplayName("Hash chain tamper evidence")
    class HashChain {

        @Test
        @DisplayName("Hash chain prev stored on captured action")
        void hashChainPrevStored() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(0L);
            when(actionRepo.save(any(CapturedActionEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, "prev-hash-abc123", false, null);

            vitalsService.captureVital(ent, request);

            verify(actionRepo).save(argThat(action ->
                    "prev-hash-abc123".equals(action.getHashChainPrev())
            ));
        }

        @Test
        @DisplayName("Hash chain current included in response")
        void hashChainInResponse() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(0L);
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, false, null);

            Map<String, Object> result = vitalsService.captureVital(ent, request);
            assertThat(result).containsKey("hash_chain_current");
            assertThat(result.get("hash_chain_current").toString()).hasSize(64); // SHA-256 hex
        }

        @Test
        @DisplayName("Hash chain includes genesis when no previous hash")
        void hashChainGenesis() {
            CapturedActionEntity action = new CapturedActionEntity(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "actor-1", "CPID-123", "CAPTURE_VITALS", "{}",
                    OffsetDateTime.now(), "device-1", 1);
            // no hashChainPrev set — should use GENESIS

            String hash = action.computeHash();
            assertThat(hash).isNotBlank().hasSize(64);
        }
    }

    @Nested
    @DisplayName("Break-glass audit")
    class BreakGlass {

        @Test
        @DisplayName("Break-glass flag stored on action")
        void breakGlassFlagStored() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(0L);
            when(actionRepo.save(any(CapturedActionEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, true, "Emergency: patient transfer");

            vitalsService.captureVital(ent, request);

            verify(actionRepo).save(argThat(CapturedActionEntity::isBreakGlass));
        }

        @Test
        @DisplayName("Break-glass emits HIGH priority outbox event")
        void breakGlassEmitsEvent() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(0L);
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, true, "Emergency access");

            vitalsService.captureVital(ent, request);

            // Should save 2 outbox events: vital captured + break-glass activated
            verify(outboxRepo, times(2)).save(any());
            verify(outboxRepo).save(argThat(e ->
                    "impilo.offline.break_glass.activated.v1".equals(e.getEventType()) &&
                            e.getPayloadJson().contains("\"priority\":\"HIGH\"")
            ));
        }

        @Test
        @DisplayName("Non-break-glass capture does not emit break-glass event")
        void nonBreakGlassNoEvent() {
            when(actionRepo.countByEntitlementId(TOKEN_ID)).thenReturn(0L);
            when(actionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            OfflineEntitlement ent = createEntitlement(50);
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-123", Map.of("code", "8867-4", "value", 72),
                    OffsetDateTime.now().toString(), "device-fp-abc", 1,
                    "idem-1", null, null, false, null);

            vitalsService.captureVital(ent, request);

            // Only 1 outbox event: vital captured
            verify(outboxRepo, times(1)).save(any());
        }
    }
}
