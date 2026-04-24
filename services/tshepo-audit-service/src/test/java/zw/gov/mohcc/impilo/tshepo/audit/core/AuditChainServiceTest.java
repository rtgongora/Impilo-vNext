package zw.gov.mohcc.impilo.tshepo.audit.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ChainVerificationResponse;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditChainHeadEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditChainHeadRepository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditChainService}.
 *
 * Validates SHA-256 hash chaining, genesis chain creation, event persistence,
 * chain-head updates, multi-tenant isolation, and chain integrity verification.
 */
@ExtendWith(MockitoExtension.class)
class AuditChainServiceTest {

    private static final String GENESIS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CORRELATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FACILITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock
    private AuditEventRepository eventRepository;

    @Mock
    private AuditChainHeadRepository chainHeadRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditChainService auditChainService;

    @Captor
    private ArgumentCaptor<AuditEventEntity> eventCaptor;

    /**
     * Copies persisted chain-head state at each save invocation. Production code reuses the same
     * {@link AuditChainHeadEntity} for genesis create and head update, so {@link ArgumentCaptor} values
     * would otherwise both reflect the post-update hash.
     */
    private final List<AuditChainHeadEntity> chainHeadSaveSnapshots = new ArrayList<>();

    @BeforeEach
    void setUp() throws JsonProcessingException {
        reset(eventRepository, chainHeadRepository, objectMapper);
        chainHeadSaveSnapshots.clear();

        lenient().when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"key\":\"value\"}");

        lenient().when(eventRepository.save(any(AuditEventEntity.class)))
                .thenAnswer(invocation -> {
                    AuditEventEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(UUID.randomUUID());
                    }
                    return entity;
                });

        lenient().when(chainHeadRepository.save(any(AuditChainHeadEntity.class)))
                .thenAnswer(invocation -> {
                    AuditChainHeadEntity entity = invocation.getArgument(0);
                    chainHeadSaveSnapshots.add(copyChainHeadState(entity));
                    return entity;
                });
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private AuditEventRequest buildRequest(UUID tenantId) {
        return new AuditEventRequest(
                tenantId,
                "AUTH_DECISION",
                "user-123",
                "PRACTITIONER",
                "Patient/CPID-456",
                "Encounter",
                "enc-789",
                "READ",
                "SUCCESS",
                "TREATMENT",
                FACILITY_ID,
                CORRELATION_ID,
                Map.of("key", "value")
        );
    }

    private AuditEventRequest buildRequest(UUID tenantId, String actorId, String eventType) {
        return new AuditEventRequest(
                tenantId,
                eventType,
                actorId,
                "PRACTITIONER",
                "Patient/CPID-456",
                "Encounter",
                "enc-789",
                "READ",
                "SUCCESS",
                "TREATMENT",
                FACILITY_ID,
                CORRELATION_ID,
                Map.of("key", "value")
        );
    }

    private AuditChainHeadEntity buildChainHead(UUID tenantId, String currentHash, long sequenceNumber) {
        AuditChainHeadEntity head = new AuditChainHeadEntity();
        head.setId(1L);
        head.setTenantId(tenantId);
        head.setCurrentHash(currentHash);
        head.setSequenceNumber(sequenceNumber);
        head.setUpdatedAt(Instant.now());
        return head;
    }

    private static AuditChainHeadEntity copyChainHeadState(AuditChainHeadEntity src) {
        AuditChainHeadEntity copy = new AuditChainHeadEntity();
        copy.setId(src.getId());
        copy.setTenantId(src.getTenantId());
        copy.setCurrentHash(src.getCurrentHash());
        copy.setSequenceNumber(src.getSequenceNumber());
        copy.setUpdatedAt(src.getUpdatedAt());
        return copy;
    }

    /**
     * Replicates the SHA-256 computation performed by AuditChainService.computeHash
     * so that tests can independently verify the expected entry hash.
     */
    private String computeExpectedHash(String previousHash, String tenantId, String eventType,
                                       String actorId, String action, String outcome,
                                       String sequenceNumber, String timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = previousHash + tenantId + eventType + actorId
                    + action + outcome + sequenceNumber + timestamp;
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Build a complete AuditEventEntity with pre-computed hash fields,
     * suitable for chain-verification test scenarios.
     */
    private AuditEventEntity buildVerifiableEvent(UUID tenantId, long seq,
                                                   String previousHash, Instant createdAt) {
        String eventType = "AUTH_DECISION";
        String actorId = "user-" + seq;
        String action = "READ";
        String outcome = "SUCCESS";

        String entryHash = computeExpectedHash(
                previousHash, tenantId.toString(), eventType, actorId,
                action, outcome, String.valueOf(seq), createdAt.toString()
        );

        AuditEventEntity event = new AuditEventEntity();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setActorId(actorId);
        event.setActorType("PRACTITIONER");
        event.setAction(action);
        event.setOutcome(outcome);
        event.setPreviousHash(previousHash);
        event.setEntryHash(entryHash);
        event.setSequenceNumber(seq);
        event.setCreatedAt(createdAt);
        return event;
    }

    // -----------------------------------------------------------------------
    // appendEvent tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("First event in chain uses genesis (zero) hash as previousHash")
    void ingest_firstEventInChain_usesZeroHash() {
        // Given: no chain head exists for this tenant
        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_A))
                .thenReturn(Optional.empty());

        AuditEventRequest request = buildRequest(TENANT_A);

        // When
        AuditEventEntity result = auditChainService.appendEvent(request);

        // Then: previousHash must be the 64-character zero genesis hash
        assertThat(result.getPreviousHash()).isEqualTo(GENESIS_HASH);
        assertThat(result.getSequenceNumber()).isEqualTo(1L);
        assertThat(result.getEntryHash()).isNotBlank();
        assertThat(result.getEntryHash()).hasSize(64); // SHA-256 hex = 64 chars

        // Verify the hash is reproducible from genesis
        String expectedHash = computeExpectedHash(
                GENESIS_HASH,
                TENANT_A.toString(),
                request.eventType(),
                request.actorId(),
                request.action(),
                request.outcome(),
                "1",
                result.getCreatedAt().toString()
        );
        assertThat(result.getEntryHash()).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("Subsequent event chains from the previous chain-head hash")
    void ingest_subsequentEvent_chainsFromPreviousHash() {
        // Given: chain head exists at sequence 5 with a known hash
        String existingHash = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234";
        AuditChainHeadEntity existingHead = buildChainHead(TENANT_A, existingHash, 5L);

        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_A))
                .thenReturn(Optional.of(existingHead));

        AuditEventRequest request = buildRequest(TENANT_A);

        // When
        AuditEventEntity result = auditChainService.appendEvent(request);

        // Then: previousHash is the chain head's current hash, sequence is incremented
        assertThat(result.getPreviousHash()).isEqualTo(existingHash);
        assertThat(result.getSequenceNumber()).isEqualTo(6L);
        assertThat(result.getEntryHash()).isNotBlank();
        assertThat(result.getEntryHash()).isNotEqualTo(existingHash);

        // Independently recompute and verify the entry hash
        String expectedHash = computeExpectedHash(
                existingHash,
                TENANT_A.toString(),
                request.eventType(),
                request.actorId(),
                request.action(),
                request.outcome(),
                "6",
                result.getCreatedAt().toString()
        );
        assertThat(result.getEntryHash()).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("appendEvent persists the event entity and updates the chain head")
    void ingest_persistsEventAndUpdatesChainHead() {
        // Given: no chain head exists (genesis scenario)
        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_A))
                .thenReturn(Optional.empty());

        AuditEventRequest request = buildRequest(TENANT_A);

        // When
        auditChainService.appendEvent(request);

        // Then: verify the event entity was saved with all fields populated
        verify(eventRepository).save(eventCaptor.capture());
        AuditEventEntity savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getTenantId()).isEqualTo(TENANT_A);
        assertThat(savedEvent.getEventType()).isEqualTo("AUTH_DECISION");
        assertThat(savedEvent.getActorId()).isEqualTo("user-123");
        assertThat(savedEvent.getActorType()).isEqualTo("PRACTITIONER");
        assertThat(savedEvent.getSubjectRef()).isEqualTo("Patient/CPID-456");
        assertThat(savedEvent.getResourceType()).isEqualTo("Encounter");
        assertThat(savedEvent.getResourceId()).isEqualTo("enc-789");
        assertThat(savedEvent.getAction()).isEqualTo("READ");
        assertThat(savedEvent.getOutcome()).isEqualTo("SUCCESS");
        assertThat(savedEvent.getPurposeOfUse()).isEqualTo("TREATMENT");
        assertThat(savedEvent.getFacilityId()).isEqualTo(FACILITY_ID);
        assertThat(savedEvent.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(savedEvent.getDetail()).isEqualTo("{\"key\":\"value\"}");
        assertThat(savedEvent.getPreviousHash()).isEqualTo(GENESIS_HASH);
        assertThat(savedEvent.getSequenceNumber()).isEqualTo(1L);
        assertThat(savedEvent.getCreatedAt()).isNotNull();

        // Then: chain head is saved twice — once for genesis creation, once for update
        verify(chainHeadRepository, times(2)).save(any(AuditChainHeadEntity.class));
        assertThat(chainHeadSaveSnapshots).hasSize(2);
        List<AuditChainHeadEntity> savedHeads = chainHeadSaveSnapshots;

        // First save: genesis creation with zero hash and sequence 0
        AuditChainHeadEntity genesisHead = savedHeads.get(0);
        assertThat(genesisHead.getTenantId()).isEqualTo(TENANT_A);
        assertThat(genesisHead.getCurrentHash()).isEqualTo(GENESIS_HASH);
        assertThat(genesisHead.getSequenceNumber()).isEqualTo(0L);

        // Second save: updated to the new entry hash and sequence 1
        AuditChainHeadEntity updatedHead = savedHeads.get(1);
        assertThat(updatedHead.getCurrentHash()).isEqualTo(savedEvent.getEntryHash());
        assertThat(updatedHead.getSequenceNumber()).isEqualTo(1L);
        assertThat(updatedHead.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("appendEvent persists audit event to the repository (outbox-equivalent publication)")
    void ingest_publishesToOutbox() {
        // Given: an existing chain at sequence 3
        String existingHash = "1111111111111111111111111111111111111111111111111111111111111111";
        AuditChainHeadEntity head = buildChainHead(TENANT_A, existingHash, 3L);

        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_A))
                .thenReturn(Optional.of(head));

        AuditEventRequest request = buildRequest(TENANT_A);

        // When
        AuditEventEntity result = auditChainService.appendEvent(request);

        // Then: event is persisted exactly once (acts as outbox entry for downstream consumers)
        verify(eventRepository, times(1)).save(any(AuditEventEntity.class));
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getEntryHash()).isNotBlank();
        assertThat(result.getEntryHash()).hasSize(64);
        assertThat(result.getSequenceNumber()).isEqualTo(4L);
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getEventType()).isEqualTo("AUTH_DECISION");
        assertThat(result.getActorId()).isEqualTo("user-123");

        // Chain head is also updated (single save since head already existed)
        verify(chainHeadRepository, times(1)).save(any(AuditChainHeadEntity.class));
    }

    @Test
    @DisplayName("Multiple tenants maintain independent, isolated hash chains")
    void ingest_withMultipleTenantsIsolatesChains() {
        // Given: neither tenant has a chain head yet
        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_A))
                .thenReturn(Optional.empty());
        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_B))
                .thenReturn(Optional.empty());

        AuditEventRequest requestA = buildRequest(TENANT_A, "doctor-1", "AUTH_DECISION");
        AuditEventRequest requestB = buildRequest(TENANT_B, "nurse-2", "CONSENT_CHANGE");

        // When
        AuditEventEntity resultA = auditChainService.appendEvent(requestA);
        AuditEventEntity resultB = auditChainService.appendEvent(requestB);

        // Then: each tenant starts from genesis hash independently
        assertThat(resultA.getPreviousHash()).isEqualTo(GENESIS_HASH);
        assertThat(resultB.getPreviousHash()).isEqualTo(GENESIS_HASH);

        // Both start at sequence 1 independently
        assertThat(resultA.getSequenceNumber()).isEqualTo(1L);
        assertThat(resultB.getSequenceNumber()).isEqualTo(1L);

        // Different tenants produce different entry hashes (tenantId is part of hash input)
        assertThat(resultA.getEntryHash()).isNotEqualTo(resultB.getEntryHash());

        // Verify chain head creation for both tenants
        ArgumentCaptor<AuditChainHeadEntity> headCaptor =
                ArgumentCaptor.forClass(AuditChainHeadEntity.class);
        verify(chainHeadRepository, atLeast(4)).save(headCaptor.capture());

        List<UUID> tenantIds = headCaptor.getAllValues().stream()
                .map(AuditChainHeadEntity::getTenantId)
                .distinct()
                .toList();
        assertThat(tenantIds).containsExactlyInAnyOrder(TENANT_A, TENANT_B);
    }

    // -----------------------------------------------------------------------
    // verifyChain tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("verifyChain returns empty/intact when no chain head exists for tenant")
    void verifyChain_noChainHead_returnsEmpty() {
        when(chainHeadRepository.findByTenantId(TENANT_A))
                .thenReturn(Optional.empty());

        ChainVerificationResponse result = auditChainService.verifyChain(TENANT_A);

        assertThat(result.intact()).isTrue();
        assertThat(result.eventsVerified()).isEqualTo(0);
        assertThat(result.tenantId()).isEqualTo(TENANT_A);
        assertThat(result.brokenAtSequence()).isNull();
        assertThat(result.message()).contains("No audit events found");
    }

    @Test
    @DisplayName("verifyChain succeeds when all hashes are correct")
    void verifyChain_validChain_returnsSuccess() {
        Instant t1 = Instant.parse("2025-01-15T10:00:00Z");
        Instant t2 = Instant.parse("2025-01-15T10:01:00Z");
        Instant t3 = Instant.parse("2025-01-15T10:02:00Z");

        // Build a valid 3-event chain
        AuditEventEntity event1 = buildVerifiableEvent(TENANT_A, 1, GENESIS_HASH, t1);
        AuditEventEntity event2 = buildVerifiableEvent(TENANT_A, 2, event1.getEntryHash(), t2);
        AuditEventEntity event3 = buildVerifiableEvent(TENANT_A, 3, event2.getEntryHash(), t3);

        AuditChainHeadEntity head = buildChainHead(TENANT_A, event3.getEntryHash(), 3L);
        when(chainHeadRepository.findByTenantId(TENANT_A))
                .thenReturn(Optional.of(head));
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_A, 1L, 3L))
                .thenReturn(List.of(event1, event2, event3));

        ChainVerificationResponse result = auditChainService.verifyChain(TENANT_A);

        assertThat(result.intact()).isTrue();
        assertThat(result.eventsVerified()).isEqualTo(3);
        assertThat(result.brokenAtSequence()).isNull();
        assertThat(result.message()).contains("3 events checked");
    }

    @Test
    @DisplayName("verifyChain detects tampered previousHash linkage")
    void verifyChain_tamperedPreviousHash_returnsFailure() {
        Instant t1 = Instant.parse("2025-01-15T10:00:00Z");
        Instant t2 = Instant.parse("2025-01-15T10:01:00Z");

        AuditEventEntity event1 = buildVerifiableEvent(TENANT_A, 1, GENESIS_HASH, t1);

        // Tamper with event2: set wrong previousHash
        AuditEventEntity event2 = new AuditEventEntity();
        event2.setId(UUID.randomUUID());
        event2.setTenantId(TENANT_A);
        event2.setEventType("AUTH_DECISION");
        event2.setActorId("user-2");
        event2.setAction("READ");
        event2.setOutcome("SUCCESS");
        event2.setPreviousHash("tampered_hash_that_does_not_match");
        event2.setEntryHash("doesnotmatter");
        event2.setSequenceNumber(2L);
        event2.setCreatedAt(t2);

        AuditChainHeadEntity head = buildChainHead(TENANT_A, "doesnotmatter", 2L);
        when(chainHeadRepository.findByTenantId(TENANT_A))
                .thenReturn(Optional.of(head));
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_A, 1L, 2L))
                .thenReturn(List.of(event1, event2));

        ChainVerificationResponse result = auditChainService.verifyChain(TENANT_A);

        assertThat(result.intact()).isFalse();
        assertThat(result.eventsVerified()).isEqualTo(1);
        assertThat(result.brokenAtSequence()).isEqualTo(2L);
        assertThat(result.message()).contains("broken at sequence 2");
    }

    @Test
    @DisplayName("verifyChain detects tampered entry hash")
    void verifyChain_tamperedEntryHash_returnsFailure() {
        Instant t1 = Instant.parse("2025-01-15T10:00:00Z");

        // Build event with correct previousHash but wrong entryHash
        AuditEventEntity event1 = new AuditEventEntity();
        event1.setId(UUID.randomUUID());
        event1.setTenantId(TENANT_A);
        event1.setEventType("AUTH_DECISION");
        event1.setActorId("user-1");
        event1.setAction("READ");
        event1.setOutcome("SUCCESS");
        event1.setPreviousHash(GENESIS_HASH); // correct previousHash
        event1.setEntryHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"); // tampered
        event1.setSequenceNumber(1L);
        event1.setCreatedAt(t1);

        AuditChainHeadEntity head = buildChainHead(
                TENANT_A, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1L);
        when(chainHeadRepository.findByTenantId(TENANT_A))
                .thenReturn(Optional.of(head));
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_A, 1L, 1L))
                .thenReturn(List.of(event1));

        ChainVerificationResponse result = auditChainService.verifyChain(TENANT_A);

        assertThat(result.intact()).isFalse();
        assertThat(result.eventsVerified()).isEqualTo(0);
        assertThat(result.brokenAtSequence()).isEqualTo(1L);
    }

    @Test
    @DisplayName("verifyChain detects mismatch between last event hash and chain head")
    void verifyChain_headMismatch_returnsFailure() {
        Instant t1 = Instant.parse("2025-01-15T10:00:00Z");

        AuditEventEntity event1 = buildVerifiableEvent(TENANT_A, 1, GENESIS_HASH, t1);

        // Chain head has a different hash than the last event's entryHash
        AuditChainHeadEntity head = buildChainHead(
                TENANT_A, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 1L);
        when(chainHeadRepository.findByTenantId(TENANT_A))
                .thenReturn(Optional.of(head));
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_A, 1L, 1L))
                .thenReturn(List.of(event1));

        ChainVerificationResponse result = auditChainService.verifyChain(TENANT_A);

        assertThat(result.intact()).isFalse();
        assertThat(result.eventsVerified()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // computeHash edge cases
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("computeHash produces deterministic 64-character lowercase hex output")
    void computeHash_isDeterministicAndCorrectLength() {
        String hash1 = auditChainService.computeHash(
                GENESIS_HASH, TENANT_A.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "1", "2025-01-15T10:00:00Z"
        );
        String hash2 = auditChainService.computeHash(
                GENESIS_HASH, TENANT_A.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "1", "2025-01-15T10:00:00Z"
        );

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("computeHash produces different output when any field changes")
    void computeHash_changesWithDifferentInput() {
        String baseline = auditChainService.computeHash(
                GENESIS_HASH, TENANT_A.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "1", "2025-01-15T10:00:00Z"
        );

        // Change each field individually and verify the hash changes
        String differentPrevHash = auditChainService.computeHash(
                "1111111111111111111111111111111111111111111111111111111111111111",
                TENANT_A.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "1", "2025-01-15T10:00:00Z"
        );
        assertThat(differentPrevHash).isNotEqualTo(baseline);

        String differentTenant = auditChainService.computeHash(
                GENESIS_HASH, TENANT_B.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "1", "2025-01-15T10:00:00Z"
        );
        assertThat(differentTenant).isNotEqualTo(baseline);

        String differentActor = auditChainService.computeHash(
                GENESIS_HASH, TENANT_A.toString(), "AUTH_DECISION",
                "user-999", "READ", "SUCCESS", "1", "2025-01-15T10:00:00Z"
        );
        assertThat(differentActor).isNotEqualTo(baseline);

        String differentSeq = auditChainService.computeHash(
                GENESIS_HASH, TENANT_A.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "2", "2025-01-15T10:00:00Z"
        );
        assertThat(differentSeq).isNotEqualTo(baseline);

        String differentTimestamp = auditChainService.computeHash(
                GENESIS_HASH, TENANT_A.toString(), "AUTH_DECISION",
                "user-1", "READ", "SUCCESS", "1", "2025-01-15T11:00:00Z"
        );
        assertThat(differentTimestamp).isNotEqualTo(baseline);
    }

    // -----------------------------------------------------------------------
    // Detail serialization
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("appendEvent with null detail stores null detail field")
    void ingest_nullDetail_storesNull() {
        when(chainHeadRepository.findByTenantIdForUpdate(TENANT_A))
                .thenReturn(Optional.empty());

        AuditEventRequest request = new AuditEventRequest(
                TENANT_A, "AUTH_DECISION", "user-1", "PRACTITIONER",
                null, null, null, "READ", "SUCCESS", null,
                FACILITY_ID, CORRELATION_ID, null
        );

        AuditEventEntity result = auditChainService.appendEvent(request);

        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDetail()).isNull();
    }
}
