package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipLinkRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipLinkResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.MosipVerifyResponse;
import zw.gov.mohcc.impilo.tshepo.identity.config.IdentityProperties;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.MosipLinkEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.MosipLinkRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MosipLinkService}.
 *
 * <p>Validates AES-256-GCM encryption of link_ref, SHA-256 hash storage for lookup,
 * conflict detection, and verification workflow.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MosipLinkService")
class MosipLinkServiceTest {

    @Mock
    private MosipLinkRepository mosipRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    private MosipLinkService service;

    /**
     * A stable 32-byte (256-bit) AES key, base64-encoded, for test use only.
     * Generated from: new byte[32] filled with 0x42.
     */
    private static final String TEST_KEK_BASE64;

    static {
        byte[] keyBytes = new byte[32];
        java.util.Arrays.fill(keyBytes, (byte) 0x42);
        TEST_KEK_BASE64 = Base64.getEncoder().encodeToString(keyBytes);
    }

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties(
                300,
                TEST_KEK_BASE64,
                null,
                null,
                null
        );
        ObjectMapper objectMapper = new ObjectMapper();
        service = new MosipLinkService(mosipRepo, outboxRepo, objectMapper, properties);
    }

    // ── Fixture helpers ────────────────────────────────────────────────────

    private static UUID tenantId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static UUID healthId() {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    private static String linkRef() {
        return "mosip-opaque-token-abc123";
    }

    private static String proofingRef() {
        return "proofing-event-xyz";
    }

    private MosipLinkRequest createLinkRequest() {
        return new MosipLinkRequest(tenantId(), healthId(), linkRef(), proofingRef());
    }

    private MosipLinkRequest createLinkRequest(String customLinkRef) {
        return new MosipLinkRequest(tenantId(), healthId(), customLinkRef, proofingRef());
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private MosipLinkEntity buildStoredEntity(String storedLinkRef) throws Exception {
        MosipLinkEntity entity = new MosipLinkEntity();
        entity.setId(1L);
        entity.setTenantId(tenantId());
        entity.setHealthId(healthId());
        entity.setLinkRefHash(sha256Hex(storedLinkRef));
        entity.setLinkRefEncrypted(new byte[]{1, 2, 3}); // dummy encrypted bytes
        entity.setVerificationStatus("PENDING");
        entity.setProofingEventRef(proofingRef());
        return entity;
    }

    // ── storeLink tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("storeLink")
    class StoreLink {

        @Test
        @DisplayName("encrypts link_ref — stored bytes differ from plaintext")
        void createLink_encryptsLinkRef() {
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> {
                        MosipLinkEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        return e;
                    });

            service.storeLink(createLinkRequest());

            ArgumentCaptor<MosipLinkEntity> captor = ArgumentCaptor.forClass(MosipLinkEntity.class);
            verify(mosipRepo).save(captor.capture());
            MosipLinkEntity saved = captor.getValue();

            byte[] encrypted = saved.getLinkRefEncrypted();
            assertNotNull(encrypted, "Encrypted link_ref must not be null");
            // Encrypted output = 12-byte IV + ciphertext + 16-byte GCM tag
            // So it must be longer than the plaintext
            assertTrue(encrypted.length > linkRef().length(),
                    "Encrypted data must be longer than the plaintext (IV + tag overhead)");
            // Verify the encrypted bytes are not just the plaintext
            byte[] plainBytes = linkRef().getBytes(StandardCharsets.UTF_8);
            assertNotEquals(
                    new String(encrypted, StandardCharsets.UTF_8),
                    linkRef(),
                    "Encrypted data must not equal the plaintext linkRef");
        }

        @Test
        @DisplayName("computes SHA-256 hash of link_ref for lookup")
        void createLink_hashesForLookup() throws Exception {
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> {
                        MosipLinkEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        return e;
                    });

            service.storeLink(createLinkRequest());

            ArgumentCaptor<MosipLinkEntity> captor = ArgumentCaptor.forClass(MosipLinkEntity.class);
            verify(mosipRepo).save(captor.capture());
            MosipLinkEntity saved = captor.getValue();

            String expectedHash = sha256Hex(linkRef());
            assertEquals(expectedHash, saved.getLinkRefHash(),
                    "Stored hash must be SHA-256 hex of the linkRef");
            // SHA-256 hex is always 64 chars
            assertEquals(64, saved.getLinkRefHash().length(),
                    "SHA-256 hex digest must be exactly 64 characters");
        }

        @Test
        @DisplayName("sets verification status to PENDING")
        void createLink_setsStatusPending() {
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> {
                        MosipLinkEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        return e;
                    });

            MosipLinkResponse response = service.storeLink(createLinkRequest());

            assertEquals("PENDING", response.verificationStatus(),
                    "New link must have PENDING verification status");
        }

        @Test
        @DisplayName("throws IdentityConflictException when link already exists")
        void createLink_duplicateLink_throwsConflict() {
            MosipLinkEntity existing = new MosipLinkEntity();
            existing.setId(1L);
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(existing));

            assertThrows(IdentityConflictException.class,
                    () -> service.storeLink(createLinkRequest()),
                    "Must throw IdentityConflictException for duplicate MOSIP link");
        }

        @Test
        @DisplayName("publishes MOSIP_LINK_CREATED outbox event")
        void createLink_publishesOutboxEvent() {
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> {
                        MosipLinkEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        return e;
                    });

            service.storeLink(createLinkRequest());

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("MosipLink", event.getAggregateType());
            assertEquals("MOSIP_LINK_CREATED", event.getEventType());
            assertEquals(healthId().toString(), event.getAggregateId());
        }

        @Test
        @DisplayName("encrypting the same linkRef twice produces different ciphertext (unique IVs)")
        void createLink_sameInput_differentCiphertext() {
            when(mosipRepo.findByTenantIdAndHealthId(any(), any()))
                    .thenReturn(Optional.empty());
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> {
                        MosipLinkEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        return e;
                    });

            // First call
            service.storeLink(new MosipLinkRequest(tenantId(), healthId(), linkRef(), proofingRef()));

            ArgumentCaptor<MosipLinkEntity> captor1 = ArgumentCaptor.forClass(MosipLinkEntity.class);
            verify(mosipRepo, times(1)).save(captor1.capture());
            byte[] encrypted1 = captor1.getValue().getLinkRefEncrypted();

            // Reset for second call with different IDs so no conflict
            UUID healthId2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            service.storeLink(new MosipLinkRequest(tenantId(), healthId2, linkRef(), proofingRef()));

            ArgumentCaptor<MosipLinkEntity> captor2 = ArgumentCaptor.forClass(MosipLinkEntity.class);
            verify(mosipRepo, times(2)).save(captor2.capture());
            byte[] encrypted2 = captor2.getAllValues().get(1).getLinkRefEncrypted();

            // GCM with random IV should produce different ciphertext each time
            assertFalse(java.util.Arrays.equals(encrypted1, encrypted2),
                    "AES-GCM encryption with random IV must produce different ciphertext for same plaintext");
        }
    }

    // ── verifyLink tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyLink")
    class VerifyLink {

        @Test
        @DisplayName("correct linkRef verifies successfully — hash matches")
        void verifyLink_correctLinkRef_returnsVerified() throws Exception {
            MosipLinkEntity stored = buildStoredEntity(linkRef());
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(stored));
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            MosipVerifyRequest request = new MosipVerifyRequest(tenantId(), healthId(), linkRef());
            MosipVerifyResponse response = service.verifyLink(request);

            assertTrue(response.verified(), "Verification must succeed when linkRef matches");
            assertEquals("VERIFIED", response.verificationStatus());
        }

        @Test
        @DisplayName("incorrect linkRef fails verification — hash mismatch")
        void verifyLink_wrongLinkRef_returnsNotVerified() throws Exception {
            MosipLinkEntity stored = buildStoredEntity(linkRef());
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(stored));

            MosipVerifyRequest request = new MosipVerifyRequest(
                    tenantId(), healthId(), "wrong-token-xyz");
            MosipVerifyResponse response = service.verifyLink(request);

            assertFalse(response.verified(), "Verification must fail when linkRef does not match");
            assertEquals("PENDING", response.verificationStatus(),
                    "Status must remain PENDING when verification fails");
        }

        @Test
        @DisplayName("throws IdentityNotFoundException when no link exists for tenant+healthId")
        void verifyLink_noLink_throwsNotFound() {
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());

            MosipVerifyRequest request = new MosipVerifyRequest(tenantId(), healthId(), linkRef());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.verifyLink(request),
                    "Must throw IdentityNotFoundException when no MOSIP link exists");
        }

        @Test
        @DisplayName("successful verification updates entity status to VERIFIED")
        void verifyLink_success_updatesEntity() throws Exception {
            MosipLinkEntity stored = buildStoredEntity(linkRef());
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(stored));
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            MosipVerifyRequest request = new MosipVerifyRequest(tenantId(), healthId(), linkRef());
            service.verifyLink(request);

            ArgumentCaptor<MosipLinkEntity> captor = ArgumentCaptor.forClass(MosipLinkEntity.class);
            verify(mosipRepo).save(captor.capture());
            MosipLinkEntity updated = captor.getValue();

            assertEquals("VERIFIED", updated.getVerificationStatus());
            assertNotNull(updated.getLinkedAt(), "linkedAt must be set on successful verification");
        }

        @Test
        @DisplayName("successful verification publishes MOSIP_LINK_VERIFIED outbox event")
        void verifyLink_success_publishesOutboxEvent() throws Exception {
            MosipLinkEntity stored = buildStoredEntity(linkRef());
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(stored));
            when(mosipRepo.save(any(MosipLinkEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            MosipVerifyRequest request = new MosipVerifyRequest(tenantId(), healthId(), linkRef());
            service.verifyLink(request);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("MosipLink", event.getAggregateType());
            assertEquals("MOSIP_LINK_VERIFIED", event.getEventType());
        }

        @Test
        @DisplayName("failed verification does NOT persist changes")
        void verifyLink_failure_doesNotPersist() throws Exception {
            MosipLinkEntity stored = buildStoredEntity(linkRef());
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(stored));

            MosipVerifyRequest request = new MosipVerifyRequest(
                    tenantId(), healthId(), "totally-wrong-ref");
            service.verifyLink(request);

            verify(mosipRepo, never()).save(any());
        }

        @Test
        @DisplayName("failed verification does NOT publish outbox event")
        void verifyLink_failure_doesNotPublishEvent() throws Exception {
            MosipLinkEntity stored = buildStoredEntity(linkRef());
            when(mosipRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(stored));

            MosipVerifyRequest request = new MosipVerifyRequest(
                    tenantId(), healthId(), "wrong-ref");
            service.verifyLink(request);

            verify(outboxRepo, never()).save(any());
        }
    }
}
