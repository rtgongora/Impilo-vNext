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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IntrospectRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IntrospectResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IssueScopedTokenRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IssueWorkContextTokenRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ScopedTokenResponse;
import zw.gov.mohcc.impilo.tshepo.identity.config.IdentityProperties;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ScopedTokenEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ScopedTokenRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TokenIssuanceService}.
 *
 * <p>Validates scoped token issuance (TTL, scope, JTI), introspection
 * (active, expired, revoked, unknown), and revocation workflows.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenIssuanceService")
class TokenIssuanceServiceTest {

    @Mock
    private ScopedTokenRepository tokenRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private RestTemplate keysRestTemplate;

    private TokenIssuanceService service;

    private static final int DEFAULT_TTL_SECONDS = 300;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties(
                DEFAULT_TTL_SECONDS,
                null,
                "http://localhost:8086",
                null,
                null
        );
        ObjectMapper objectMapper = new ObjectMapper();
        service = new TokenIssuanceService(
                tokenRepo, outboxRepo, keysRestTemplate, objectMapper, properties);
    }

    // ── Fixture helpers ────────────────────────────────────────────────────

    private static UUID tenantId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private IssueScopedTokenRequest defaultTokenRequest() {
        return new IssueScopedTokenRequest(
                tenantId(),
                "actor-user-1",
                "clinical-lookup",
                "butano-fhir",
                "patient:read",
                "cpid:abcdef",
                null // use default TTL
        );
    }

    private IssueScopedTokenRequest tokenRequestWithTtl(int ttl) {
        return new IssueScopedTokenRequest(
                tenantId(),
                "actor-user-1",
                "clinical-lookup",
                "butano-fhir",
                "patient:read",
                "cpid:abcdef",
                ttl
        );
    }

    /**
     * Stubs the keys-service REST call to return a fake signature.
     */
    private void stubKeysServiceSuccess() {
        // Real keys-service /v1/sign response is FLAT {keyId, algorithm,
        // signature} — the previous {data:{signature}} stub encoded a shape the
        // service never returns, so it hid the live 400/empty-signature break.
        String responseBody = """
                {"keyId": "kid-test", "algorithm": "Ed25519", "signature": "fake-ed25519-signature-base64url"}
                """;
        when(keysRestTemplate.postForEntity(eq("/v1/sign"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    /**
     * Builds a ScopedTokenEntity as if it were persisted, with configurable state.
     */
    private ScopedTokenEntity buildTokenEntity(String jti, String status,
                                                 Instant expiresAt, Instant revokedAt) {
        ScopedTokenEntity entity = new ScopedTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId());
        entity.setActorId("actor-user-1");
        entity.setTargetService("butano-fhir");
        entity.setScope("patient:read");
        entity.setSubjectRef("cpid:abcdef");
        entity.setJti(jti);
        entity.setIssuedAt(Instant.now().minusSeconds(60));
        entity.setExpiresAt(expiresAt);
        entity.setRevokedAt(revokedAt);
        entity.setStatus(status);
        return entity;
    }

    /**
     * Creates a minimal valid JWS compact-serialization string with the given JTI
     * embedded in the payload claims. This is a syntactically valid JWS (3 dots)
     * that can be parsed by Nimbus SignedJWT.parse().
     */
    private String buildFakeJws(String jti) {
        // We need a valid JWS compact serialization: header.payload.signature
        // header = base64url({"alg":"EdDSA","typ":"JWT"})
        String header = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9";
        // Build claims JSON with the JTI
        String claimsJson = String.format(
                "{\"jti\":\"%s\",\"iss\":\"tshepo-identity-service\",\"tenant_id\":\"%s\"}",
                jti, tenantId());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claimsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = "fakesig";
        return header + "." + payload + "." + signature;
    }

    // ── issueToken tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("issueToken")
    class IssueToken {

        @Test
        @DisplayName("uses default TTL when request ttlSeconds is null")
        void issueToken_setsCorrectDefaultTtl() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ScopedTokenResponse response = service.issueToken(defaultTokenRequest());

            assertNotNull(response.expiresAt(), "expiresAt must not be null");
            // The expiry should be approximately DEFAULT_TTL_SECONDS from now
            Instant expectedMin = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS - 5);
            Instant expectedMax = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS + 5);
            assertTrue(response.expiresAt().isAfter(expectedMin)
                            && response.expiresAt().isBefore(expectedMax),
                    "expiresAt should be ~300 seconds from now");
        }

        @Test
        @DisplayName("uses custom TTL when provided in request")
        void issueToken_setsCustomTtl() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            int customTtl = 600;
            ScopedTokenResponse response = service.issueToken(tokenRequestWithTtl(customTtl));

            Instant expectedMin = Instant.now().plusSeconds(customTtl - 5);
            Instant expectedMax = Instant.now().plusSeconds(customTtl + 5);
            assertTrue(response.expiresAt().isAfter(expectedMin)
                            && response.expiresAt().isBefore(expectedMax),
                    "expiresAt should respect the custom TTL of 600s");
        }

        @Test
        @DisplayName("includes the requested scope in the response")
        void issueToken_includesScope() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ScopedTokenResponse response = service.issueToken(defaultTokenRequest());

            assertEquals("patient:read", response.scope(),
                    "Response scope must match the requested scope");
        }

        @Test
        @DisplayName("includes the target service in the response")
        void issueToken_includesTargetService() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ScopedTokenResponse response = service.issueToken(defaultTokenRequest());

            assertEquals("butano-fhir", response.targetService(),
                    "Response targetService must match the request");
        }

        @Test
        @DisplayName("generates a non-blank JTI")
        void issueToken_generatesJti() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ScopedTokenResponse response = service.issueToken(defaultTokenRequest());

            assertNotNull(response.jti(), "JTI must not be null");
            assertFalse(response.jti().isBlank(), "JTI must not be blank");
            // JTI should be a valid UUID
            assertDoesNotThrow(() -> UUID.fromString(response.jti()),
                    "JTI should be a valid UUID string");
        }

        @Test
        @DisplayName("returns a non-blank signed token string")
        void issueToken_returnsSignedToken() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ScopedTokenResponse response = service.issueToken(defaultTokenRequest());

            assertNotNull(response.token(), "Signed token must not be null");
            assertFalse(response.token().isBlank(), "Signed token must not be blank");
            // JWS compact serialization has 3 parts separated by dots
            String[] parts = response.token().split("\\.");
            assertEquals(3, parts.length,
                    "JWS compact serialization must have exactly 3 parts (header.payload.signature)");
            // The payload must be canonical JSON with the tenant claim — a
            // Map.toString() payload ({k=v}) is a valid-looking 3-part token
            // that every consumer fails to parse (regression guard).
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertDoesNotThrow(() -> new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson),
                    "token payload must be valid JSON, was: " + payloadJson);
            assertTrue(payloadJson.contains("\"tenant_id\""),
                    "token payload must carry the tenant_id claim as JSON, was: " + payloadJson);
        }

        @Test
        @DisplayName("persists token entity with correct fields")
        void issueToken_persistsEntity() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.issueToken(defaultTokenRequest());

            ArgumentCaptor<ScopedTokenEntity> captor = ArgumentCaptor.forClass(ScopedTokenEntity.class);
            verify(tokenRepo).save(captor.capture());
            ScopedTokenEntity saved = captor.getValue();

            assertEquals(tenantId(), saved.getTenantId());
            assertEquals("actor-user-1", saved.getActorId());
            assertEquals("butano-fhir", saved.getTargetService());
            assertEquals("patient:read", saved.getScope());
            assertEquals("cpid:abcdef", saved.getSubjectRef());
            assertEquals("ACTIVE", saved.getStatus());
            assertNotNull(saved.getJti());
            assertNotNull(saved.getExpiresAt());
        }

        @Test
        @DisplayName("publishes TOKEN_ISSUED outbox event")
        void issueToken_publishesOutboxEvent() {
            stubKeysServiceSuccess();
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.issueToken(defaultTokenRequest());

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("ScopedToken", event.getAggregateType());
            assertEquals("TOKEN_ISSUED", event.getEventType());
        }

        @Test
        @DisplayName("throws when keys-service returns error")
        void issueToken_keysServiceError_throws() {
            when(keysRestTemplate.postForEntity(eq("/v1/sign"), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));

            assertThrows(IllegalStateException.class,
                    () -> service.issueToken(defaultTokenRequest()),
                    "Must throw when keys-service fails");
        }
    }

    // ── introspect tests ───────────────────────────────────────────────────

    @Nested
    @DisplayName("introspect")
    class Introspect {

        @Test
        @DisplayName("returns active=true for a valid, non-expired, non-revoked token")
        void resolveToken_returnsIdentity() {
            String jti = UUID.randomUUID().toString();
            ScopedTokenEntity entity = buildTokenEntity(
                    jti, "ACTIVE", Instant.now().plusSeconds(600), null);
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.of(entity));

            String fakeJws = buildFakeJws(jti);
            IntrospectResponse response = service.introspect(new IntrospectRequest(fakeJws));

            assertTrue(response.active(), "Active, non-expired token must return active=true");
            assertEquals(jti, response.jti());
            assertEquals(tenantId(), response.tenantId());
            assertEquals("actor-user-1", response.actorId());
            assertEquals("patient:read", response.scope());
            assertEquals("butano-fhir", response.targetService());
            assertEquals("cpid:abcdef", response.subjectRef());
        }

        @Test
        @DisplayName("returns active=false for an expired token")
        void resolveToken_expired_returnsInactive() {
            String jti = UUID.randomUUID().toString();
            ScopedTokenEntity entity = buildTokenEntity(
                    jti, "ACTIVE", Instant.now().minusSeconds(60), null);
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.of(entity));

            String fakeJws = buildFakeJws(jti);
            IntrospectResponse response = service.introspect(new IntrospectRequest(fakeJws));

            assertFalse(response.active(), "Expired token must return active=false");
            assertNull(response.jti(), "Inactive introspection must not leak the JTI");
        }

        @Test
        @DisplayName("returns active=false for a revoked token")
        void resolveToken_revoked_returnsInactive() {
            String jti = UUID.randomUUID().toString();
            ScopedTokenEntity entity = buildTokenEntity(
                    jti, "REVOKED", Instant.now().plusSeconds(600), Instant.now());
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.of(entity));

            String fakeJws = buildFakeJws(jti);
            IntrospectResponse response = service.introspect(new IntrospectRequest(fakeJws));

            assertFalse(response.active(), "Revoked token must return active=false");
        }

        @Test
        @DisplayName("returns active=false when JTI is not found in database")
        void resolveToken_unknownJti_returnsInactive() {
            String jti = UUID.randomUUID().toString();
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.empty());

            String fakeJws = buildFakeJws(jti);
            IntrospectResponse response = service.introspect(new IntrospectRequest(fakeJws));

            assertFalse(response.active(), "Unknown JTI must return active=false");
        }

        @Test
        @DisplayName("returns active=false for a malformed token string")
        void resolveToken_malformedToken_returnsInactive() {
            IntrospectResponse response = service.introspect(
                    new IntrospectRequest("not-a-valid-jws"));

            assertFalse(response.active(), "Malformed token must return active=false");
        }

        @Test
        @DisplayName("returns active=false when revokedAt is set even if status is not REVOKED")
        void resolveToken_revokedAtSet_returnsInactive() {
            String jti = UUID.randomUUID().toString();
            // Status is ACTIVE but revokedAt is set (edge case)
            ScopedTokenEntity entity = buildTokenEntity(
                    jti, "ACTIVE", Instant.now().plusSeconds(600), Instant.now());
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.of(entity));

            String fakeJws = buildFakeJws(jti);
            IntrospectResponse response = service.introspect(new IntrospectRequest(fakeJws));

            assertFalse(response.active(),
                    "Token with revokedAt set must be treated as inactive regardless of status string");
        }
    }

    // ── revokeToken tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeToken")
    class RevokeToken {

        @Test
        @DisplayName("sets status to REVOKED and revokedAt")
        void revokeToken_updatesEntity() {
            String jti = UUID.randomUUID().toString();
            ScopedTokenEntity entity = buildTokenEntity(
                    jti, "ACTIVE", Instant.now().plusSeconds(600), null);
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.of(entity));
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.revokeToken(jti);

            ArgumentCaptor<ScopedTokenEntity> captor = ArgumentCaptor.forClass(ScopedTokenEntity.class);
            verify(tokenRepo).save(captor.capture());
            ScopedTokenEntity revoked = captor.getValue();

            assertEquals("REVOKED", revoked.getStatus());
            assertNotNull(revoked.getRevokedAt(), "revokedAt must be set");
        }

        @Test
        @DisplayName("throws IdentityNotFoundException for unknown JTI")
        void revokeToken_unknownJti_throws() {
            when(tokenRepo.findByJti("unknown-jti")).thenReturn(Optional.empty());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.revokeToken("unknown-jti"),
                    "Must throw IdentityNotFoundException for unknown JTI");
        }

        @Test
        @DisplayName("publishes TOKEN_REVOKED outbox event")
        void revokeToken_publishesOutboxEvent() {
            String jti = UUID.randomUUID().toString();
            ScopedTokenEntity entity = buildTokenEntity(
                    jti, "ACTIVE", Instant.now().plusSeconds(600), null);
            when(tokenRepo.findByJti(jti)).thenReturn(Optional.of(entity));
            when(tokenRepo.save(any(ScopedTokenEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.revokeToken(jti);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("ScopedToken", event.getAggregateType());
            assertEquals("TOKEN_REVOKED", event.getEventType());
            assertEquals(jti, event.getAggregateId());
        }
    }

    // ── Work-context tokens (D-P3) ─────────────────────────────────────────

    @Nested
    @DisplayName("work-context issuance")
    class WorkContext {

        private IssueWorkContextTokenRequest workRequest(String previousJti) {
            return new IssueWorkContextTokenRequest(
                    tenantId(),
                    "person-hid-1",
                    "PROVPUBLICID0000000000001",
                    UUID.fromString("11111111-1111-4111-8111-111111111111"),
                    null,
                    UUID.fromString("22222222-2222-4222-8222-222222222222"),
                    "NURSE_GENERAL",
                    "TREATMENT",
                    "LOA2",
                    previousJti,
                    null);
        }

        @Test
        @DisplayName("persists WORK_CONTEXT kind + context claims, 15-min default TTL")
        void issuesWorkContextToken() {
            stubKeysServiceSuccess();

            ScopedTokenResponse response = service.issueWorkContextToken(workRequest(null));

            ArgumentCaptor<ScopedTokenEntity> captor = ArgumentCaptor.forClass(ScopedTokenEntity.class);
            verify(tokenRepo).save(captor.capture());
            ScopedTokenEntity saved = captor.getValue();
            assertEquals("WORK_CONTEXT", saved.getTokenKind());
            assertEquals("work:context", saved.getScope());
            assertEquals("PROVPUBLICID0000000000001", saved.getSubjectRef());
            assertTrue(saved.getContextClaims().contains("11111111-1111-4111-8111-111111111111"));
            assertTrue(saved.getContextClaims().contains("workspace_id"));
            assertTrue(saved.getContextClaims().contains("NURSE_GENERAL"));
            // Live-truth fields must NOT be baked into the context.
            assertFalse(saved.getContextClaims().contains("licence"));
            long ttl = saved.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
            assertTrue(ttl > 800 && ttl <= 900, "expected ~900s TTL, got " + ttl);
            assertNotNull(response.token());
        }

        @Test
        @DisplayName("context switch revokes the previous jti before reissue")
        void contextSwitchRevokesPreviousToken() {
            stubKeysServiceSuccess();
            ScopedTokenEntity previous = new ScopedTokenEntity();
            previous.setId(UUID.randomUUID());
            previous.setJti("prev-jti");
            previous.setStatus("ACTIVE");
            when(tokenRepo.findByTenantIdAndJti(tenantId(), "prev-jti")).thenReturn(Optional.of(previous));

            service.issueWorkContextToken(workRequest("prev-jti"));

            assertEquals("REVOKED", previous.getStatus());
            assertNotNull(previous.getRevokedAt());
            // previous saved as revoked + new token saved
            verify(tokenRepo, times(2)).save(any(ScopedTokenEntity.class));
        }

        @Test
        @DisplayName("an already-revoked previous jti is left untouched")
        void alreadyRevokedPreviousJtiIsIgnored() {
            stubKeysServiceSuccess();
            ScopedTokenEntity previous = new ScopedTokenEntity();
            previous.setJti("prev-jti");
            previous.setStatus("REVOKED");
            when(tokenRepo.findByTenantIdAndJti(tenantId(), "prev-jti")).thenReturn(Optional.of(previous));

            service.issueWorkContextToken(workRequest("prev-jti"));

            verify(tokenRepo, times(1)).save(any(ScopedTokenEntity.class));
        }
    }
}
