package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateShareLinkRequest;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkCreatedResponse;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkDto;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ShareLinkValidationResult;
import zw.gov.mohcc.impilo.tshepo.consent.exception.ShareLinkExpiredException;
import zw.gov.mohcc.impilo.tshepo.consent.exception.ShareLinkNotFoundException;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ShareLinkEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ShareLinkRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShareLinkService}.
 *
 * <p>Tests the patient-delegated data sharing link lifecycle: create, validate,
 * consume, and revoke. Verifies secure token generation, SHA-256 hashing,
 * expiration checks, use-count enforcement, and outbox event publishing.</p>
 */
@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private ConsentProperties properties;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShareLinkService shareLinkService;

    @Captor
    private ArgumentCaptor<ShareLinkEntity> entityCaptor;

    @Captor
    private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    // --- Shared test fixtures ---
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PATIENT_REF = "Patient/CPID-456";
    private static final String CREATOR_ID = "practitioner-123";
    private static final String SCOPE = "clinical-data";
    private static final String PURPOSE = "TREATMENT";

    /**
     * Builds a standard CreateShareLinkRequest for testing.
     */
    private CreateShareLinkRequest buildCreateRequest(int maxUses, int expiresInHours) {
        return new CreateShareLinkRequest(
                TENANT_ID,
                PATIENT_REF,
                SCOPE,
                PURPOSE,
                maxUses,
                expiresInHours
        );
    }

    /**
     * Builds a persisted ShareLinkEntity with configurable state.
     */
    private ShareLinkEntity buildShareLinkEntity(Instant expiresAt, int maxUses, int useCount) {
        ShareLinkEntity entity = new ShareLinkEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TENANT_ID);
        entity.setPatientRef(PATIENT_REF);
        entity.setCreatedBy(CREATOR_ID);
        entity.setScope(SCOPE);
        entity.setPurpose(PURPOSE);
        entity.setTokenHash("abc123hash");
        entity.setMaxUses(maxUses);
        entity.setUseCount(useCount);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        return entity;
    }

    /**
     * Builds a valid, non-expired share link entity with remaining uses.
     */
    private ShareLinkEntity buildValidShareLinkEntity() {
        return buildShareLinkEntity(
                Instant.now().plus(24, ChronoUnit.HOURS), // expires in 24 hours
                5,  // max 5 uses
                0   // 0 uses so far
        );
    }

    // =========================================================================
    // Create tests
    // =========================================================================

    @Nested
    @DisplayName("Create share link")
    class CreateTests {

        @Test
        @DisplayName("createLink_generatesUniqueToken - returns unique plaintext token and persists hash only")
        void createLink_generatesUniqueToken() {
            CreateShareLinkRequest request = buildCreateRequest(3, 24);

            when(properties.getMaxShareLinkHours()).thenReturn(72);
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> {
                ShareLinkEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            ShareLinkCreatedResponse response = shareLinkService.create(request, CREATOR_ID);

            // Verify the plaintext token is returned
            assertThat(response.token()).isNotNull();
            assertThat(response.token()).isNotBlank();
            // URL-safe Base64 encoded 32 bytes = 43 chars (without padding)
            assertThat(response.token().length()).isGreaterThanOrEqualTo(40);

            // Verify the entity was saved with a hash, NOT the plaintext token
            verify(shareLinkRepo).save(entityCaptor.capture());
            ShareLinkEntity saved = entityCaptor.getValue();
            assertThat(saved.getTokenHash()).isNotNull();
            assertThat(saved.getTokenHash()).isNotBlank();
            // SHA-256 hex is 64 chars
            assertThat(saved.getTokenHash()).hasSize(64);
            // The hash must NOT equal the plaintext token
            assertThat(saved.getTokenHash()).isNotEqualTo(response.token());

            // Verify the hash of the returned token matches what was stored
            String expectedHash = ShareLinkService.hashToken(response.token());
            assertThat(saved.getTokenHash()).isEqualTo(expectedHash);
        }

        @Test
        @DisplayName("createLink_setsCorrectFields - entity has correct tenant, patient, scope, purpose, maxUses")
        void createLink_setsCorrectFields() {
            CreateShareLinkRequest request = buildCreateRequest(5, 12);

            when(properties.getMaxShareLinkHours()).thenReturn(72);
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> {
                ShareLinkEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            ShareLinkCreatedResponse response = shareLinkService.create(request, CREATOR_ID);

            verify(shareLinkRepo).save(entityCaptor.capture());
            ShareLinkEntity saved = entityCaptor.getValue();
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getPatientRef()).isEqualTo(PATIENT_REF);
            assertThat(saved.getCreatedBy()).isEqualTo(CREATOR_ID);
            assertThat(saved.getScope()).isEqualTo(SCOPE);
            assertThat(saved.getPurpose()).isEqualTo(PURPOSE);
            assertThat(saved.getMaxUses()).isEqualTo(5);
            assertThat(saved.getUseCount()).isEqualTo(0);
            assertThat(saved.getExpiresAt()).isNotNull();
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());

            assertThat(response.maxUses()).isEqualTo(5);
            assertThat(response.expiresAt()).isNotNull();
            assertThat(response.id()).isNotNull();
        }

        @Test
        @DisplayName("createLink_publishesOutboxEvent - creates ShareLinkCreated outbox event")
        void createLink_publishesOutboxEvent() {
            CreateShareLinkRequest request = buildCreateRequest(1, 8);

            when(properties.getMaxShareLinkHours()).thenReturn(72);
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> {
                ShareLinkEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            shareLinkService.create(request, CREATOR_ID);

            verify(outboxRepo).save(outboxCaptor.capture());
            EventOutboxEntity outbox = outboxCaptor.getValue();
            assertThat(outbox.getAggregateType()).isEqualTo("ShareLink");
            assertThat(outbox.getEventType()).isEqualTo("ShareLinkCreated");
            assertThat(outbox.getAggregateId()).isNotNull();
        }

        @Test
        @DisplayName("createLink_exceedsMaxHours_throws - rejects expiration exceeding max allowed")
        void createLink_exceedsMaxHours_throws() {
            CreateShareLinkRequest request = buildCreateRequest(1, 100);

            when(properties.getMaxShareLinkHours()).thenReturn(72);

            assertThatThrownBy(() -> shareLinkService.create(request, CREATOR_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("72");
        }

        @Test
        @DisplayName("createLink_generatesDistinctTokens - two calls produce different tokens")
        void createLink_generatesDistinctTokens() {
            CreateShareLinkRequest request = buildCreateRequest(1, 8);

            when(properties.getMaxShareLinkHours()).thenReturn(72);
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> {
                ShareLinkEntity saved = invocation.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            ShareLinkCreatedResponse r1 = shareLinkService.create(request, CREATOR_ID);
            ShareLinkCreatedResponse r2 = shareLinkService.create(request, CREATOR_ID);

            assertThat(r1.token()).isNotEqualTo(r2.token());
        }
    }

    // =========================================================================
    // Validate and consume tests
    // =========================================================================

    @Nested
    @DisplayName("Validate and consume share link")
    class ValidateConsumeTests {

        @Test
        @DisplayName("resolveLink_beforeExpiry_returnsConsent - valid token returns success with patient/scope/purpose")
        void resolveLink_beforeExpiry_returnsConsent() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            String plainToken = "some-valid-token";
            String tokenHash = ShareLinkService.hashToken(plainToken);
            entity.setTokenHash(tokenHash);

            when(shareLinkRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShareLinkValidationResult result = shareLinkService.validateAndConsume(plainToken);

            assertThat(result.valid()).isTrue();
            assertThat(result.patientRef()).isEqualTo(PATIENT_REF);
            assertThat(result.scope()).isEqualTo(SCOPE);
            assertThat(result.purpose()).isEqualTo(PURPOSE);
            assertThat(result.remainingUses()).isEqualTo(4); // 5 max - 1 consumed = 4

            // Verify use count was incremented and saved
            verify(shareLinkRepo).save(entityCaptor.capture());
            ShareLinkEntity saved = entityCaptor.getValue();
            assertThat(saved.getUseCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("resolveLink_afterExpiry_throwsExpired - expired token returns failure")
        void resolveLink_afterExpiry_throwsExpired() {
            ShareLinkEntity entity = buildShareLinkEntity(
                    Instant.now().minus(1, ChronoUnit.HOURS), // Already expired
                    5, 0
            );
            String plainToken = "expired-token";
            String tokenHash = ShareLinkService.hashToken(plainToken);
            entity.setTokenHash(tokenHash);

            when(shareLinkRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));

            ShareLinkValidationResult result = shareLinkService.validateAndConsume(plainToken);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("expired");

            // Verify use count was NOT incremented
            verify(shareLinkRepo, never()).save(any());
        }

        @Test
        @DisplayName("resolveLink_revokedLink_returnsFailed - revoked link returns failure")
        void resolveLink_revokedLink_returnsFailed() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            entity.setRevokedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
            String plainToken = "revoked-token";
            String tokenHash = ShareLinkService.hashToken(plainToken);
            entity.setTokenHash(tokenHash);

            when(shareLinkRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));

            ShareLinkValidationResult result = shareLinkService.validateAndConsume(plainToken);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("revoked");
        }

        @Test
        @DisplayName("resolveLink_maxUsesExceeded_returnsFailed - exhausted link returns failure")
        void resolveLink_maxUsesExceeded_returnsFailed() {
            ShareLinkEntity entity = buildShareLinkEntity(
                    Instant.now().plus(24, ChronoUnit.HOURS), // Not expired
                    3, 3 // All uses consumed
            );
            String plainToken = "exhausted-token";
            String tokenHash = ShareLinkService.hashToken(plainToken);
            entity.setTokenHash(tokenHash);

            when(shareLinkRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));

            ShareLinkValidationResult result = shareLinkService.validateAndConsume(plainToken);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("maximum uses");
        }

        @Test
        @DisplayName("resolveLink_unknownToken_throws - non-existent token throws ShareLinkNotFoundException")
        void resolveLink_unknownToken_throws() {
            String unknownToken = "unknown-token-xyz";
            String tokenHash = ShareLinkService.hashToken(unknownToken);

            when(shareLinkRepo.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shareLinkService.validateAndConsume(unknownToken))
                    .isInstanceOf(ShareLinkNotFoundException.class);
        }

        @Test
        @DisplayName("resolveLink_nullToken_returnsFailure - null/blank token returns failure without DB hit")
        void resolveLink_nullToken_returnsFailure() {
            ShareLinkValidationResult result = shareLinkService.validateAndConsume(null);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("required");

            // Verify no DB lookup was attempted
            verify(shareLinkRepo, never()).findByTokenHash(any());
        }

        @Test
        @DisplayName("resolveLink_blankToken_returnsFailure - blank token returns failure without DB hit")
        void resolveLink_blankToken_returnsFailure() {
            ShareLinkValidationResult result = shareLinkService.validateAndConsume("   ");

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("required");
            verify(shareLinkRepo, never()).findByTokenHash(any());
        }

        @Test
        @DisplayName("resolveLink_publishesConsumedEvent - creates ShareLinkConsumed outbox event on success")
        void resolveLink_publishesConsumedEvent() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            String plainToken = "valid-token-for-event";
            String tokenHash = ShareLinkService.hashToken(plainToken);
            entity.setTokenHash(tokenHash);

            when(shareLinkRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            shareLinkService.validateAndConsume(plainToken);

            verify(outboxRepo).save(outboxCaptor.capture());
            EventOutboxEntity outbox = outboxCaptor.getValue();
            assertThat(outbox.getEventType()).isEqualTo("ShareLinkConsumed");
            assertThat(outbox.getAggregateType()).isEqualTo("ShareLink");
        }
    }

    // =========================================================================
    // Revoke tests
    // =========================================================================

    @Nested
    @DisplayName("Revoke share link")
    class RevokeTests {

        @Test
        @DisplayName("revoke_setsRevokedTimestamp - marks share link as revoked")
        void revoke_setsRevokedTimestamp() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            UUID linkId = entity.getId();

            when(shareLinkRepo.findById(linkId)).thenReturn(Optional.of(entity));
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            shareLinkService.revoke(linkId, CREATOR_ID);

            verify(shareLinkRepo).save(entityCaptor.capture());
            ShareLinkEntity saved = entityCaptor.getValue();
            assertThat(saved.getRevokedAt()).isNotNull();
            assertThat(saved.getRevokedAt()).isBefore(Instant.now().plus(1, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("revoke_publishesEvent - creates ShareLinkRevoked outbox event")
        void revoke_publishesEvent() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            UUID linkId = entity.getId();

            when(shareLinkRepo.findById(linkId)).thenReturn(Optional.of(entity));
            when(shareLinkRepo.save(any(ShareLinkEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            shareLinkService.revoke(linkId, CREATOR_ID);

            verify(outboxRepo).save(outboxCaptor.capture());
            EventOutboxEntity outbox = outboxCaptor.getValue();
            assertThat(outbox.getEventType()).isEqualTo("ShareLinkRevoked");
        }

        @Test
        @DisplayName("revoke_alreadyRevoked_throws - throws ShareLinkExpiredException for already-revoked link")
        void revoke_alreadyRevoked_throws() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            entity.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));
            UUID linkId = entity.getId();

            when(shareLinkRepo.findById(linkId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> shareLinkService.revoke(linkId, CREATOR_ID))
                    .isInstanceOf(ShareLinkExpiredException.class)
                    .hasMessageContaining("already revoked");
        }

        @Test
        @DisplayName("revoke_notFound_throws - throws ShareLinkNotFoundException for unknown link")
        void revoke_notFound_throws() {
            UUID unknownId = UUID.randomUUID();
            when(shareLinkRepo.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shareLinkService.revoke(unknownId, CREATOR_ID))
                    .isInstanceOf(ShareLinkNotFoundException.class);
        }
    }

    // =========================================================================
    // Find tests
    // =========================================================================

    @Nested
    @DisplayName("Find share link")
    class FindTests {

        @Test
        @DisplayName("findById_returnsDto - returns DTO for existing share link")
        void findById_returnsDto() {
            ShareLinkEntity entity = buildValidShareLinkEntity();
            UUID linkId = entity.getId();

            when(shareLinkRepo.findById(linkId)).thenReturn(Optional.of(entity));

            ShareLinkDto dto = shareLinkService.findById(linkId);

            assertThat(dto).isNotNull();
            assertThat(dto.id()).isEqualTo(linkId);
            assertThat(dto.tenantId()).isEqualTo(TENANT_ID);
            assertThat(dto.patientRef()).isEqualTo(PATIENT_REF);
            assertThat(dto.scope()).isEqualTo(SCOPE);
            assertThat(dto.purpose()).isEqualTo(PURPOSE);
            assertThat(dto.maxUses()).isEqualTo(5);
            assertThat(dto.useCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("findById_notFound_throws - throws for non-existent share link")
        void findById_notFound_throws() {
            UUID unknownId = UUID.randomUUID();
            when(shareLinkRepo.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shareLinkService.findById(unknownId))
                    .isInstanceOf(ShareLinkNotFoundException.class);
        }
    }

    // =========================================================================
    // Token hashing tests
    // =========================================================================

    @Nested
    @DisplayName("Token hashing")
    class HashingTests {

        @Test
        @DisplayName("hashToken_deterministic - same input produces same hash")
        void hashToken_deterministic() {
            String token = "test-token-abc";
            String hash1 = ShareLinkService.hashToken(token);
            String hash2 = ShareLinkService.hashToken(token);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("hashToken_differentInputs_differentHashes - different tokens produce different hashes")
        void hashToken_differentInputs_differentHashes() {
            String hash1 = ShareLinkService.hashToken("token-one");
            String hash2 = ShareLinkService.hashToken("token-two");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("hashToken_returnsHex64 - SHA-256 hash is 64-char hex string")
        void hashToken_returnsHex64() {
            String hash = ShareLinkService.hashToken("any-token");

            assertThat(hash).hasSize(64);
            assertThat(hash).matches("[0-9a-f]{64}");
        }
    }
}
