package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityTokenRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityTokenResponse;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.CapabilityVerifyResponse;
import zw.gov.mohcc.impilo.tshepo.offline.client.AuthzServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;
import zw.gov.mohcc.impilo.tshepo.offline.exception.CapabilityTokenNotFoundException;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineServiceException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.CapabilityTokenEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.CapabilityTokenRepository;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CapabilityTokenService}.
 *
 * <p>Tests cover token issuance (TTL, capabilities), verification (valid, expired,
 * revoked), revocation, and facility access validation.</p>
 */
@ExtendWith(MockitoExtension.class)
class CapabilityTokenServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID FACILITY_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String ACTOR_ID = "provider-user-1";
    private static final String DEVICE_FINGERPRINT = "device-fp-abc123";

    @Mock
    private CapabilityTokenRepository tokenRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private AuthzServiceClient authzClient;

    @Mock
    private KeysServiceClient keysClient;

    @Mock
    private CapabilityTokenJwsVerifier jwsVerifier;

    @Captor
    private ArgumentCaptor<CapabilityTokenEntity> tokenCaptor;

    @Captor
    private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    private OfflineProperties offlineProperties;
    private ObjectMapper objectMapper;
    private CapabilityTokenService tokenService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        offlineProperties = buildOfflineProperties();
        tokenService = new CapabilityTokenService(
                tokenRepo, outboxRepo, authzClient, keysClient, offlineProperties, objectMapper, jwsVerifier);
        lenient().when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Stub the JWS verifier to accept a token carrying the given token id (crypto proven separately). */
    private void stubJwsValid(UUID jti) {
        when(jwsVerifier.verify(anyString())).thenReturn(new CapabilityTokenJwsVerifier.Result(
                true, null, new JWTClaimsSet.Builder().jwtID(jti.toString()).build()));
    }

    // ------------------------------------------------------------------
    // issueToken tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("issueToken sets the correct TTL based on requested hours")
    void issueToken_setsCorrectTtl() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(true);
        when(tokenRepo.save(any(CapabilityTokenEntity.class))).thenAnswer(inv -> {
            CapabilityTokenEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(keysClient.signPayload(any(UUID.class), anyString(), anyString())).thenReturn("eyJhbGciOiJFZERTQSJ9.payload.signature");
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int requestedTtlHours = 12;
        CapabilityTokenRequest request = buildTokenRequest(
                List.of("READ_PATIENT"), requestedTtlHours);

        Instant beforeIssue = Instant.now();

        // Act
        CapabilityTokenResponse response = tokenService.issueToken(request);

        // Assert: expiry is approximately 12 hours from now
        assertThat(response.expiresAt()).isAfter(beforeIssue.plus(11, ChronoUnit.HOURS));
        assertThat(response.expiresAt()).isBefore(beforeIssue.plus(13, ChronoUnit.HOURS));
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("issueToken uses default TTL when requested TTL is null")
    void issueToken_usesDefaultTtlWhenNull() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(true);
        when(tokenRepo.save(any(CapabilityTokenEntity.class))).thenAnswer(inv -> {
            CapabilityTokenEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(keysClient.signPayload(any(UUID.class), anyString(), anyString())).thenReturn("eyJhbGciOiJFZERTQSJ9.payload.signature");
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CapabilityTokenRequest request = buildTokenRequest(List.of("READ_PATIENT"), null);

        Instant beforeIssue = Instant.now();

        // Act
        CapabilityTokenResponse response = tokenService.issueToken(request);

        // Assert: default is 8 hours
        assertThat(response.expiresAt()).isAfter(beforeIssue.plus(7, ChronoUnit.HOURS));
        assertThat(response.expiresAt()).isBefore(beforeIssue.plus(9, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("issueToken caps TTL at maximum when requested exceeds max")
    void issueToken_capsTtlAtMaximum() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(true);
        when(tokenRepo.save(any(CapabilityTokenEntity.class))).thenAnswer(inv -> {
            CapabilityTokenEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(keysClient.signPayload(any(UUID.class), anyString(), anyString())).thenReturn("eyJhbGciOiJFZERTQSJ9.payload.signature");
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Request 200 hours but max is 72
        CapabilityTokenRequest request = buildTokenRequest(List.of("READ_PATIENT"), 200);

        Instant beforeIssue = Instant.now();

        // Act
        CapabilityTokenResponse response = tokenService.issueToken(request);

        // Assert: capped at 72 hours
        assertThat(response.expiresAt()).isBefore(beforeIssue.plus(73, ChronoUnit.HOURS));
    }

    @Test
    @DisplayName("issueToken includes only allowed capabilities (intersection of requested and configured)")
    void issueToken_includesAllowedCapabilities() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(true);
        when(tokenRepo.save(any(CapabilityTokenEntity.class))).thenAnswer(inv -> {
            CapabilityTokenEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(keysClient.signPayload(any(UUID.class), anyString(), anyString())).thenReturn("eyJhbGciOiJFZERTQSJ9.payload.signature");
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Request includes one valid and one invalid capability
        List<String> requested = List.of("READ_PATIENT", "ADMIN_OVERRIDE", "CREATE_PROVISIONAL_ENCOUNTER");
        CapabilityTokenRequest request = buildTokenRequest(requested, 8);

        // Act
        CapabilityTokenResponse response = tokenService.issueToken(request);

        // Assert: only the allowed capabilities are included
        assertThat(response.capabilities())
                .containsExactlyInAnyOrder("READ_PATIENT", "CREATE_PROVISIONAL_ENCOUNTER");
        assertThat(response.capabilities()).doesNotContain("ADMIN_OVERRIDE");
    }

    @Test
    @DisplayName("issueToken defaults to all allowed capabilities when requested is null")
    void issueToken_defaultsToAllAllowedCapabilitiesWhenNull() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(true);
        when(tokenRepo.save(any(CapabilityTokenEntity.class))).thenAnswer(inv -> {
            CapabilityTokenEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(keysClient.signPayload(any(UUID.class), anyString(), anyString())).thenReturn("eyJhbGciOiJFZERTQSJ9.payload.signature");
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CapabilityTokenRequest request = buildTokenRequest(null, 8);

        // Act
        CapabilityTokenResponse response = tokenService.issueToken(request);

        // Assert: all allowed capabilities from config
        assertThat(response.capabilities()).containsExactlyInAnyOrder(
                "READ_PATIENT",
                "CREATE_PROVISIONAL_ENCOUNTER",
                "CREATE_PROVISIONAL_REGISTRATION",
                "READ_MEDICATION",
                "DISPENSE_MEDICATION"
        );
    }

    @Test
    @DisplayName("issueToken throws when actor has no facility access")
    void issueToken_throwsWhenNoFacilityAccess() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(false);

        CapabilityTokenRequest request = buildTokenRequest(List.of("READ_PATIENT"), 8);

        // Act + Assert
        assertThatThrownBy(() -> tokenService.issueToken(request))
                .isInstanceOf(OfflineServiceException.class)
                .hasMessageContaining("does not have access to facility");
    }

    @Test
    @DisplayName("issueToken throws when no requested capabilities are allowed")
    void issueToken_throwsWhenNoValidCapabilities() {
        // Arrange
        when(authzClient.checkFacilityAccess(TENANT_ID, ACTOR_ID, FACILITY_ID)).thenReturn(true);

        // Request only disallowed capabilities
        CapabilityTokenRequest request = buildTokenRequest(
                List.of("ADMIN_OVERRIDE", "BULK_EXPORT"), 8);

        // Act + Assert
        assertThatThrownBy(() -> tokenService.issueToken(request))
                .isInstanceOf(OfflineServiceException.class)
                .hasMessageContaining("None of the requested capabilities are allowed");
    }

    // ------------------------------------------------------------------
    // verifyToken tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("verifyToken: valid signature + ACTIVE token returns capabilities")
    void verifyToken_valid_returnsCapabilities() {
        UUID tokenId = UUID.randomUUID();
        CapabilityTokenEntity entity = buildActiveTokenEntity(tokenId);
        stubJwsValid(tokenId);
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.of(entity));

        CapabilityVerifyResponse response = tokenService.verifyToken(
                new CapabilityVerifyRequest("signed.token.value", TENANT_ID));

        assertThat(response.valid()).isTrue();
        assertThat(response.tokenId()).isEqualTo(tokenId);
        assertThat(response.actorId()).isEqualTo(ACTOR_ID);
        assertThat(response.capabilities()).containsExactlyInAnyOrder("READ_PATIENT", "CREATE_PROVISIONAL_ENCOUNTER");
    }

    @Test
    @DisplayName("verifyToken: invalid signature fails closed (no DB lookup, no any-sig bypass)")
    void verifyToken_invalidSignature_failsClosed() {
        when(jwsVerifier.verify(anyString())).thenReturn(
                new CapabilityTokenJwsVerifier.Result(false, "INVALID_SIGNATURE", null));

        CapabilityVerifyResponse response = tokenService.verifyToken(
                new CapabilityVerifyRequest("tampered.token.value", TENANT_ID));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("INVALID_SIGNATURE");
        verify(tokenRepo, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("verifyToken: valid signature but expired registry token -> invalid")
    void verifyToken_expired_invalid() {
        UUID tokenId = UUID.randomUUID();
        CapabilityTokenEntity entity = buildActiveTokenEntity(tokenId);
        entity.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        stubJwsValid(tokenId);
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.of(entity));

        CapabilityVerifyResponse response = tokenService.verifyToken(
                new CapabilityVerifyRequest("signed.token.value", TENANT_ID));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("expired");
    }

    @Test
    @DisplayName("verifyToken: valid signature but revoked token -> invalid")
    void verifyToken_revoked_invalid() {
        UUID tokenId = UUID.randomUUID();
        CapabilityTokenEntity entity = buildActiveTokenEntity(tokenId);
        entity.setStatus("REVOKED");
        stubJwsValid(tokenId);
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.of(entity));

        CapabilityVerifyResponse response = tokenService.verifyToken(
                new CapabilityVerifyRequest("signed.token.value", TENANT_ID));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("revoked");
    }

    @Test
    @DisplayName("verifyToken: valid signature but token not in registry -> invalid")
    void verifyToken_notFound_invalid() {
        UUID tokenId = UUID.randomUUID();
        stubJwsValid(tokenId);
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.empty());

        CapabilityVerifyResponse response = tokenService.verifyToken(
                new CapabilityVerifyRequest("signed.token.value", TENANT_ID));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).contains("not found");
    }

    // ------------------------------------------------------------------
    // revokeToken tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("revokeToken marks an active token as REVOKED")
    void revokeToken_marksAsRevoked() {
        // Arrange
        UUID tokenId = UUID.randomUUID();
        CapabilityTokenEntity entity = buildActiveTokenEntity(tokenId);
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.of(entity));
        when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        tokenService.revokeToken(tokenId, TENANT_ID);

        // Assert
        verify(tokenRepo).save(tokenCaptor.capture());
        CapabilityTokenEntity saved = tokenCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("REVOKED");
        assertThat(saved.getRevokedAt()).isNotNull();

        verify(outboxRepo).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("CAPABILITY_TOKEN_REVOKED");
    }

    @Test
    @DisplayName("revokeToken throws when token is not found")
    void revokeToken_notFound_throws() {
        UUID tokenId = UUID.randomUUID();
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.revokeToken(tokenId, TENANT_ID))
                .isInstanceOf(CapabilityTokenNotFoundException.class);
    }

    @Test
    @DisplayName("revokeToken throws when token is not ACTIVE")
    void revokeToken_notActive_throws() {
        UUID tokenId = UUID.randomUUID();
        CapabilityTokenEntity entity = buildActiveTokenEntity(tokenId);
        entity.setStatus("REVOKED"); // already revoked
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> tokenService.revokeToken(tokenId, TENANT_ID))
                .isInstanceOf(OfflineServiceException.class)
                .hasMessageContaining("not active");
    }

    // ------------------------------------------------------------------
    // getToken tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getToken returns token response with capabilities and no signed token")
    void getToken_returnsResponseWithCapabilities() {
        UUID tokenId = UUID.randomUUID();
        CapabilityTokenEntity entity = buildActiveTokenEntity(tokenId);
        when(tokenRepo.findByIdAndTenantId(tokenId, TENANT_ID)).thenReturn(Optional.of(entity));

        CapabilityTokenResponse response = tokenService.getToken(tokenId, TENANT_ID);

        assertThat(response.id()).isEqualTo(tokenId);
        assertThat(response.signedToken()).isNull(); // signed token not returned on GET
        assertThat(response.capabilities()).containsExactlyInAnyOrder("READ_PATIENT", "CREATE_PROVISIONAL_ENCOUNTER");
    }

    // ------------------------------------------------------------------
    // Test fixture helpers
    // ------------------------------------------------------------------

    private OfflineProperties buildOfflineProperties() {
        return new OfflineProperties(
                8,   // defaultCapabilityTtlHours
                72,  // maxCapabilityTtlHours
                50,  // maxOfflineEncounters
                List.of(
                        "READ_PATIENT",
                        "CREATE_PROVISIONAL_ENCOUNTER",
                        "CREATE_PROVISIONAL_REGISTRATION",
                        "READ_MEDICATION",
                        "DISPENSE_MEDICATION"
                ),
                24,  // offlinePackTtlHours
                100  // reconciliationBatchSize
        );
    }

    private CapabilityTokenRequest buildTokenRequest(List<String> capabilities, Integer ttlHours) {
        return new CapabilityTokenRequest(
                TENANT_ID,
                ACTOR_ID,
                FACILITY_ID,
                DEVICE_FINGERPRINT,
                capabilities,
                ttlHours
        );
    }

    /**
     * Build an ACTIVE {@link CapabilityTokenEntity} with two capabilities.
     */
    private CapabilityTokenEntity buildActiveTokenEntity(UUID tokenId) {
        CapabilityTokenEntity entity = new CapabilityTokenEntity();
        entity.setId(tokenId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setFacilityId(FACILITY_ID);
        entity.setDeviceFingerprint(DEVICE_FINGERPRINT);
        entity.setCapabilities("[\"READ_PATIENT\",\"CREATE_PROVISIONAL_ENCOUNTER\"]");
        entity.setIssuedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.HOURS));
        entity.setStatus("ACTIVE");
        return entity;
    }
}
