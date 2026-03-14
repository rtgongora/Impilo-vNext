package zw.gov.mohcc.impilo.offlineedge;

import com.fasterxml.jackson.databind.JsonNode;
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
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlement;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlementVerifier;
import zw.gov.mohcc.impilo.offlineedge.api.dto.CaptureVitalsRequest;
import zw.gov.mohcc.impilo.offlineedge.core.OfflineVitalsService;
import zw.gov.mohcc.impilo.offlineedge.domain.CapturedActionEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.CapturedActionRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the Wave 15 offline vitals endpoint.
 *
 * <p>Covers:
 * <ul>
 *   <li>Entitlement verification deny/allow via JWT</li>
 *   <li>Offline capture writes audit + queue row</li>
 *   <li>Expired token rejection</li>
 *   <li>Missing capability rejection</li>
 * </ul>
 */
class OfflineVitalsEndpointTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");
    private static final UUID FACILITY_ID = UUID.fromString("bbbb2222-2222-2222-2222-222222222222");
    private static final String ACTOR_ID = "nurse-offline-001";

    private OctetKeyPair signingKey;
    private OfflineEntitlementVerifier verifier;
    private CapturedActionRepository actionRepo;
    private OutboxEventRepository outboxRepo;
    private OfflineVitalsService vitalsService;
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
        objectMapper = new ObjectMapper();

        when(actionRepo.save(any(CapturedActionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepo.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        vitalsService = new OfflineVitalsService(actionRepo, outboxRepo, objectMapper);
    }

    @Nested
    @DisplayName("Entitlement verification")
    class EntitlementVerification {

        @Test
        @DisplayName("ALLOW: valid token with CAPTURE_VITALS capability")
        void allowValidToken() throws Exception {
            String token = buildToken(
                    List.of("CAPTURE_VITALS", "READ_PATIENT"),
                    Instant.now().plus(8, ChronoUnit.HOURS));

            var result = verifier.verify(token, "CAPTURE_VITALS");

            assertThat(result.valid()).isTrue();
            assertThat(result.entitlement().actorId()).isEqualTo(ACTOR_ID);
            assertThat(result.entitlement().tenantId()).isEqualTo(TENANT_ID);
            assertThat(result.entitlement().facilityId()).isEqualTo(FACILITY_ID);
            assertThat(result.entitlement().hasCapability("CAPTURE_VITALS")).isTrue();
        }

        @Test
        @DisplayName("DENY: expired token")
        void denyExpiredToken() throws Exception {
            String token = buildToken(
                    List.of("CAPTURE_VITALS"),
                    Instant.now().minus(2, ChronoUnit.HOURS)); // expired

            var result = verifier.verify(token, "CAPTURE_VITALS");

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("expired");
        }

        @Test
        @DisplayName("DENY: missing CAPTURE_VITALS capability")
        void denyMissingCapability() throws Exception {
            String token = buildToken(
                    List.of("READ_PATIENT"), // no CAPTURE_VITALS
                    Instant.now().plus(8, ChronoUnit.HOURS));

            var result = verifier.verify(token, "CAPTURE_VITALS");

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("Missing required capability");
        }

        @Test
        @DisplayName("DENY: no token provided")
        void denyNullToken() {
            var result = verifier.verify(null, "CAPTURE_VITALS");
            assertThat(result.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Offline capture writes audit + queue")
    class CaptureAudit {

        @Test
        @DisplayName("Capture stores action with QUEUED status and emits outbox event")
        void captureStoresAndAudits() throws Exception {
            String token = buildToken(
                    List.of("CAPTURE_VITALS"),
                    Instant.now().plus(8, ChronoUnit.HOURS));
            var verification = verifier.verify(token, "CAPTURE_VITALS");
            assertThat(verification.valid()).isTrue();

            OfflineEntitlement entitlement = verification.entitlement();
            CaptureVitalsRequest request = new CaptureVitalsRequest(
                    "CPID-12345",
                    Map.of("code", "8867-4", "value", 72, "unit", "bpm",
                            "display", "Heart rate",
                            "effective_date_time", "2026-03-14T10:00:00Z"),
                    OffsetDateTime.now().toString(),
                    "DEVICE-EDGE-001",
                    1,
                    "idem-vitals-001",
                    UUID.randomUUID().toString()
            );

            Map<String, Object> result = vitalsService.captureVital(entitlement, request);

            // Verify response
            assertThat(result.get("action_type")).isEqualTo("CAPTURE_VITALS");
            assertThat(result.get("status")).isEqualTo("QUEUED");
            assertThat(result.get("patient_ref")).isEqualTo("CPID-12345");
            assertThat(result.get("action_id")).isNotNull();

            // Verify action was stored
            verify(actionRepo).save(argThat(action ->
                    "QUEUED".equals(action.getStatus()) &&
                    "CPID-12345".equals(action.getPatientRef()) &&
                    "CAPTURE_VITALS".equals(action.getActionType()) &&
                    ACTOR_ID.equals(action.getActorId())
            ));

            // Verify outbox event was emitted
            verify(outboxRepo).save(argThat(event ->
                    "impilo.offline.vital.captured.v1".equals(event.getEventType()) &&
                    "idem-vitals-001".equals(event.getIdempotencyKey()) &&
                    "OfflineVital".equals(event.getAggregateType())
            ));
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
        claims.put("device_fingerprint", "device-fp-test");
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
