package zw.gov.mohcc.impilo.offlineedge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlement;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlementVerifier;
import zw.gov.mohcc.impilo.offlineedge.api.dto.SyncRequest;
import zw.gov.mohcc.impilo.offlineedge.api.dto.SyncResponse;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineSyncService;
import zw.gov.mohcc.impilo.offlineedge.domain.CapturedActionEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.ReconciliationBatchEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.CapturedActionRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.ReconciliationBatchRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the Wave 15 offline sync service.
 *
 * <p>Covers:
 * <ul>
 *   <li>Sync publishes outbox event with correlation_id preserved</li>
 *   <li>Batch tracking creates reconciliation entry</li>
 *   <li>Per-action acceptance/rejection results</li>
 * </ul>
 */
class OfflineSyncServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("cccc3333-3333-3333-3333-333333333333");
    private static final UUID FACILITY_ID = UUID.fromString("dddd4444-4444-4444-4444-444444444444");
    private static final String ACTOR_ID = "nurse-sync-001";
    private static final String CORRELATION_ID = "e2e-corr-12345";

    private OctetKeyPair signingKey;
    private OfflineEntitlementVerifier verifier;
    private CapturedActionRepository actionRepo;
    private OutboxEventRepository outboxRepo;
    private ReconciliationBatchRepository batchRepo;
    private OfflineSyncService syncService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID("offline-capability-signing-key")
                .generate();
        JWKSet jwkSet = new JWKSet(signingKey.toPublicJWK());
        verifier = new OfflineEntitlementVerifier(jwkSet);

        actionRepo = mock(CapturedActionRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);
        batchRepo = mock(ReconciliationBatchRepository.class);
        objectMapper = new ObjectMapper();

        when(actionRepo.save(any(CapturedActionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepo.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepo.save(any(ReconciliationBatchEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        syncService = new OfflineSyncService(actionRepo, outboxRepo, batchRepo, objectMapper);
    }

    @Nested
    @DisplayName("Sync batch processing")
    class SyncBatch {

        @Test
        @DisplayName("Sync publishes outbox events with correlation_id preserved")
        void syncPublishesWithCorrelationId() throws Exception {
            String token = buildToken(List.of("CAPTURE_VITALS", "READ_PATIENT"),
                    Instant.now().plus(8, ChronoUnit.HOURS));
            var verification = verifier.verify(token);
            assertThat(verification.valid()).isTrue();

            OfflineEntitlement entitlement = verification.entitlement();
            UUID batchId = UUID.randomUUID();
            UUID entryId = UUID.randomUUID();

            SyncRequest request = new SyncRequest(
                    batchId, "DEVICE-001",
                    List.of(new SyncRequest.SyncActionEntry(
                            entryId, "CPID-67890", "CAPTURE_VITALS",
                            Map.of("code", "8480-6", "value", 120, "unit", "mmHg",
                                    "display", "Systolic BP"),
                            OffsetDateTime.now().toString(),
                            "DEVICE-001", 1,
                            "idem-sync-001", CORRELATION_ID
                    ))
            );

            SyncResponse response = syncService.processSyncBatch(entitlement, request);

            assertThat(response.batchId()).isEqualTo(batchId);
            assertThat(response.status()).isEqualTo("SYNCED");
            assertThat(response.totalActions()).isEqualTo(1);
            assertThat(response.acceptedCount()).isEqualTo(1);
            assertThat(response.rejectedCount()).isEqualTo(0);
            assertThat(response.results()).hasSize(1);
            assertThat(response.results().get(0).status()).isEqualTo("QUEUED");

            // Verify outbox events: 1 per action + 1 batch event = 2
            ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
            verify(outboxRepo, times(2)).save(outboxCaptor.capture());

            List<OutboxEventEntity> outboxEvents = outboxCaptor.getAllValues();

            // Per-action event should preserve correlation_id
            OutboxEventEntity actionEvent = outboxEvents.stream()
                    .filter(e -> "impilo.offline.action.synced.v1".equals(e.getEventType()))
                    .findFirst().orElseThrow();
            assertThat(actionEvent.getCorrelationId()).isNotNull();
            assertThat(actionEvent.getIdempotencyKey()).isEqualTo("idem-sync-001");
            assertThat(actionEvent.getSubjectId()).isEqualTo("CPID-67890");

            // Batch event
            OutboxEventEntity batchEvent = outboxEvents.stream()
                    .filter(e -> "impilo.offline.sync.completed.v1".equals(e.getEventType()))
                    .findFirst().orElseThrow();
            assertThat(batchEvent.getAggregateId()).isEqualTo(batchId.toString());
        }

        @Test
        @DisplayName("Sync creates reconciliation batch entry")
        void syncCreatesBatchEntry() throws Exception {
            String token = buildToken(List.of("CAPTURE_VITALS"),
                    Instant.now().plus(8, ChronoUnit.HOURS));
            OfflineEntitlement entitlement = verifier.verify(token).entitlement();

            UUID batchId = UUID.randomUUID();
            SyncRequest request = new SyncRequest(
                    batchId, "DEVICE-002",
                    List.of(
                            new SyncRequest.SyncActionEntry(UUID.randomUUID(), "CPID-111",
                                    "CAPTURE_VITALS", Map.of("code", "8867-4", "value", 72),
                                    OffsetDateTime.now().toString(), "DEVICE-002", 1, "idem-1", null),
                            new SyncRequest.SyncActionEntry(UUID.randomUUID(), "CPID-222",
                                    "CAPTURE_VITALS", Map.of("code", "8310-5", "value", 37),
                                    OffsetDateTime.now().toString(), "DEVICE-002", 2, "idem-2", null)
                    )
            );

            SyncResponse response = syncService.processSyncBatch(entitlement, request);

            assertThat(response.totalActions()).isEqualTo(2);
            assertThat(response.acceptedCount()).isEqualTo(2);

            // Verify batch was saved twice (initial + update)
            verify(batchRepo, times(2)).save(argThat(batch ->
                    batch.getBatchId().equals(batchId)));
        }
    }

    @Nested
    @DisplayName("Entitlement verification for sync")
    class SyncEntitlement {

        @Test
        @DisplayName("DENY: expired token for sync")
        void denyExpiredForSync() throws Exception {
            String token = buildToken(List.of("CAPTURE_VITALS"),
                    Instant.now().minus(2, ChronoUnit.HOURS));

            var result = verifier.verify(token);
            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("expired");
        }
    }

    // ------------------------------------------------------------------
    // Token building
    // ------------------------------------------------------------------

    private String buildToken(List<String> capabilities, Instant expiresAt) throws Exception {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iss", "tshepo-offline-service");
        claims.put("sub", ACTOR_ID);
        claims.put("aud", "impilo-offline");
        claims.put("iat", Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("tenant_id", TENANT_ID.toString());
        claims.put("facility_id", FACILITY_ID.toString());
        claims.put("device_fingerprint", "device-fp-sync-test");
        claims.put("capabilities", capabilities);
        claims.put("max_offline_encounters", 50);

        String payloadJson = objectMapper.writeValueAsString(claims);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(signingKey.getKeyID())
                .build();
        JWSObject jws = new JWSObject(header, new Payload(payloadJson));
        jws.sign(new Ed25519Signer(signingKey));
        return jws.serialize();
    }
}
