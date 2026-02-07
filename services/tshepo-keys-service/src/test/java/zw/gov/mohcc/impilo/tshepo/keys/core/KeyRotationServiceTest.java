package zw.gov.mohcc.impilo.tshepo.keys.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.keys.config.KeysProperties;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.KeyRotationLogEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.KeyRotationLogRepository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KeyRotationService}.
 *
 * <p>Tests cover manual rotation (creating a new key and deactivating old ones),
 * rotation audit logging, outbox event emission, key revocation, and scheduled rotation.</p>
 */
@ExtendWith(MockitoExtension.class)
class KeyRotationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ADMIN_USER = "admin-user-1";
    private static final String ROTATION_REASON = "Scheduled quarterly rotation";

    @Mock
    private SigningKeyRepository signingKeyRepository;

    @Mock
    private KeyRotationLogRepository rotationLogRepository;

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private Ed25519SigningService ed25519SigningService;

    @Captor
    private ArgumentCaptor<KeyRotationLogEntity> logCaptor;

    @Captor
    private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    @Captor
    private ArgumentCaptor<SigningKeyEntity> keyCaptor;

    private KeysProperties keysProperties;
    private KeyRotationService keyRotationService;

    @BeforeEach
    void setUp() {
        keysProperties = buildKeysProperties();
        keyRotationService = new KeyRotationService(
                signingKeyRepository,
                rotationLogRepository,
                eventOutboxRepository,
                ed25519SigningService,
                keysProperties
        );
    }

    // ------------------------------------------------------------------
    // rotateKey tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("rotateKey creates a new key and deactivates the old one")
    void rotateKey_createsNewKeyAndDeactivatesOld() {
        // Arrange: one existing active key
        SigningKeyEntity oldKey = buildActiveKeyEntity("kid-old-12345678");
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(oldKey));
        when(signingKeyRepository.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SigningKeyEntity newKey = buildActiveKeyEntity("kid-new-87654321");
        when(ed25519SigningService.generateKeyPair(TENANT_ID)).thenReturn(newKey);

        when(rotationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SigningKeyEntity result = keyRotationService.rotateKey(TENANT_ID, ADMIN_USER, ROTATION_REASON);

        // Assert: new key is returned
        assertThat(result.getKeyId()).isEqualTo("kid-new-87654321");

        // Assert: old key was marked ROTATED
        verify(signingKeyRepository).save(keyCaptor.capture());
        SigningKeyEntity capturedOld = keyCaptor.getValue();
        assertThat(capturedOld.getStatus()).isEqualTo("ROTATED");
        assertThat(capturedOld.getRotatedAt()).isNotNull();

        // Assert: new key was generated
        verify(ed25519SigningService).generateKeyPair(TENANT_ID);
    }

    @Test
    @DisplayName("rotateKey logs the rotation event with old and new key IDs")
    void rotateKey_logsRotationEvent() {
        // Arrange
        SigningKeyEntity oldKey = buildActiveKeyEntity("kid-old-aaaaaaaa");
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(oldKey));
        when(signingKeyRepository.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SigningKeyEntity newKey = buildActiveKeyEntity("kid-new-bbbbbbbb");
        when(ed25519SigningService.generateKeyPair(TENANT_ID)).thenReturn(newKey);

        when(rotationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        keyRotationService.rotateKey(TENANT_ID, ADMIN_USER, ROTATION_REASON);

        // Assert: rotation log was persisted with correct data
        verify(rotationLogRepository).save(logCaptor.capture());
        KeyRotationLogEntity log = logCaptor.getValue();
        assertThat(log.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(log.getOldKeyId()).isEqualTo("kid-old-aaaaaaaa");
        assertThat(log.getNewKeyId()).isEqualTo("kid-new-bbbbbbbb");
        assertThat(log.getRotatedBy()).isEqualTo(ADMIN_USER);
        assertThat(log.getReason()).isEqualTo(ROTATION_REASON);
    }

    @Test
    @DisplayName("rotateKey writes an outbox event with type KEY_ROTATED")
    void rotateKey_writesOutboxEvent() {
        // Arrange
        SigningKeyEntity oldKey = buildActiveKeyEntity("kid-old-cccccccc");
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(oldKey));
        when(signingKeyRepository.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SigningKeyEntity newKey = buildActiveKeyEntity("kid-new-dddddddd");
        when(ed25519SigningService.generateKeyPair(TENANT_ID)).thenReturn(newKey);

        when(rotationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        keyRotationService.rotateKey(TENANT_ID, ADMIN_USER, ROTATION_REASON);

        // Assert: outbox event was written
        verify(eventOutboxRepository).save(outboxCaptor.capture());
        EventOutboxEntity event = outboxCaptor.getValue();
        assertThat(event.getAggregateType()).isEqualTo("SigningKey");
        assertThat(event.getAggregateId()).isEqualTo("kid-new-dddddddd");
        assertThat(event.getEventType()).isEqualTo("KEY_ROTATED");
        assertThat(event.getPayload()).contains("kid-old-cccccccc");
        assertThat(event.getPayload()).contains("kid-new-dddddddd");
        assertThat(event.getPayload()).contains(ADMIN_USER);
    }

    @Test
    @DisplayName("rotateKey handles no existing active keys gracefully (first-ever rotation)")
    void rotateKey_noExistingActiveKeys_succeeds() {
        // Arrange: no active keys
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of());

        SigningKeyEntity newKey = buildActiveKeyEntity("kid-first-key001");
        when(ed25519SigningService.generateKeyPair(TENANT_ID)).thenReturn(newKey);

        when(rotationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        SigningKeyEntity result = keyRotationService.rotateKey(TENANT_ID, ADMIN_USER, "Initial key generation");

        // Assert
        assertThat(result.getKeyId()).isEqualTo("kid-first-key001");

        // Rotation log should have null oldKeyId
        verify(rotationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getOldKeyId()).isNull();
    }

    @Test
    @DisplayName("rotateKey deactivates multiple active keys when more than one exists")
    void rotateKey_deactivatesMultipleActiveKeys() {
        // Arrange: two active keys (unusual but possible)
        SigningKeyEntity key1 = buildActiveKeyEntity("kid-multi-one001");
        SigningKeyEntity key2 = buildActiveKeyEntity("kid-multi-two002");
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(key1, key2));
        when(signingKeyRepository.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SigningKeyEntity newKey = buildActiveKeyEntity("kid-multi-new003");
        when(ed25519SigningService.generateKeyPair(TENANT_ID)).thenReturn(newKey);

        when(rotationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        keyRotationService.rotateKey(TENANT_ID, ADMIN_USER, "Cleanup rotation");

        // Assert: both old keys saved as ROTATED
        verify(signingKeyRepository, times(2)).save(keyCaptor.capture());
        List<SigningKeyEntity> saved = keyCaptor.getAllValues();
        assertThat(saved).extracting(SigningKeyEntity::getStatus).containsOnly("ROTATED");
    }

    // ------------------------------------------------------------------
    // revokeKey tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("revokeKey marks the key as REVOKED and writes an outbox event")
    void revokeKey_marksKeyAsRevoked() {
        // Arrange
        SigningKeyEntity key = buildActiveKeyEntity("kid-to-revoke01");
        when(signingKeyRepository.findByKeyId("kid-to-revoke01")).thenReturn(Optional.of(key));
        when(signingKeyRepository.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        keyRotationService.revokeKey("kid-to-revoke01", ADMIN_USER);

        // Assert
        verify(signingKeyRepository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getStatus()).isEqualTo("REVOKED");
        assertThat(keyCaptor.getValue().getRotatedAt()).isNotNull();

        verify(eventOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("KEY_REVOKED");
    }

    @Test
    @DisplayName("revokeKey throws when key is not found")
    void revokeKey_keyNotFound_throws() {
        when(signingKeyRepository.findByKeyId("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keyRotationService.revokeKey("nonexistent", ADMIN_USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key not found");
    }

    // ------------------------------------------------------------------
    // scheduledRotation tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("scheduledRotation rotates expired keys per tenant")
    void scheduledRotation_rotatesExpiredKeys() {
        // Arrange: one expired key
        SigningKeyEntity expiredKey = buildActiveKeyEntity("kid-expired-0001");
        expiredKey.setCreatedAt(Instant.now().minus(180, ChronoUnit.DAYS));

        when(signingKeyRepository.findKeysNeedingRotation(any(Instant.class)))
                .thenReturn(List.of(expiredKey));
        when(signingKeyRepository.findActiveKeysByTenant(TENANT_ID)).thenReturn(List.of(expiredKey));
        when(signingKeyRepository.save(any(SigningKeyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SigningKeyEntity newKey = buildActiveKeyEntity("kid-auto-rotated");
        when(ed25519SigningService.generateKeyPair(TENANT_ID)).thenReturn(newKey);

        when(rotationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventOutboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        keyRotationService.scheduledRotation();

        // Assert: rotation was triggered for the tenant
        verify(ed25519SigningService).generateKeyPair(TENANT_ID);
    }

    @Test
    @DisplayName("scheduledRotation does nothing when no keys need rotation")
    void scheduledRotation_noKeysNeedRotation_doesNothing() {
        when(signingKeyRepository.findKeysNeedingRotation(any(Instant.class)))
                .thenReturn(List.of());

        // Act
        keyRotationService.scheduledRotation();

        // Assert: no rotation triggered
        verify(ed25519SigningService, never()).generateKeyPair(any());
    }

    // ------------------------------------------------------------------
    // Test fixture helpers
    // ------------------------------------------------------------------

    private KeysProperties buildKeysProperties() {
        KeysProperties props = new KeysProperties();
        props.setKek("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        props.setKeyAlgorithm("Ed25519");
        props.setRotationIntervalDays(90);
        props.setTokenDefaultTtlSeconds(300);
        props.setJwksCacheTtlSeconds(60);
        return props;
    }

    /**
     * Build an ACTIVE {@link SigningKeyEntity} with the given key ID.
     */
    private SigningKeyEntity buildActiveKeyEntity(String keyId) {
        SigningKeyEntity entity = new SigningKeyEntity();
        entity.setId(1L);
        entity.setTenantId(TENANT_ID);
        entity.setKeyId(keyId);
        entity.setAlgorithm("Ed25519");
        entity.setPublicKeyPem("-----BEGIN ED25519 PUBLIC KEY-----\ntest\n-----END ED25519 PUBLIC KEY-----");
        entity.setPrivateKeyEncrypted(new byte[60]);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        entity.setExpiresAt(Instant.now().plus(60, ChronoUnit.DAYS));
        return entity;
    }
}
