package zw.gov.mohcc.impilo.offline.sdk.entitlement;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OfflineEntitlementVerifier}.
 *
 * <p>Covers: valid token, expired token, wrong issuer, wrong audience,
 * missing capability, invalid signature, malformed JWS.</p>
 */
class OfflineEntitlementVerifierTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FACILITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR_ID = "nurse-offline-001";
    private static final String DEVICE_FP = "device-fp-xyz";

    private static OctetKeyPair signingKey;
    private static OfflineEntitlementVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        signingKey = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID("offline-capability-signing-key")
                .generate();
        JWKSet jwkSet = new JWKSet(signingKey.toPublicJWK());
        verifier = new OfflineEntitlementVerifier(jwkSet);
    }

    @Nested
    @DisplayName("Valid tokens")
    class ValidTokens {

        @Test
        @DisplayName("Accepts valid token with correct capability")
        void validTokenWithCapability() throws Exception {
            String token = buildSignedToken(
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("CAPTURE_VITALS", "READ_PATIENT"),
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(7, ChronoUnit.HOURS)
            );

            EntitlementVerificationResult result = verifier.verify(token, "CAPTURE_VITALS");

            assertThat(result.valid()).isTrue();
            assertThat(result.entitlement()).isNotNull();
            assertThat(result.entitlement().actorId()).isEqualTo(ACTOR_ID);
            assertThat(result.entitlement().tenantId()).isEqualTo(TENANT_ID);
            assertThat(result.entitlement().facilityId()).isEqualTo(FACILITY_ID);
            assertThat(result.entitlement().hasCapability("CAPTURE_VITALS")).isTrue();
            assertThat(result.entitlement().hasCapability("READ_PATIENT")).isTrue();
            assertThat(result.entitlement().isValid()).isTrue();
        }

        @Test
        @DisplayName("Accepts valid token without capability check")
        void validTokenNoCapabilityCheck() throws Exception {
            String token = buildSignedToken(
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("CAPTURE_VITALS"),
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(7, ChronoUnit.HOURS)
            );

            EntitlementVerificationResult result = verifier.verify(token);

            assertThat(result.valid()).isTrue();
            assertThat(result.entitlement().maxOfflineEncounters()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("Denied tokens")
    class DeniedTokens {

        @Test
        @DisplayName("Rejects expired token")
        void expiredToken() throws Exception {
            String token = buildSignedToken(
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("CAPTURE_VITALS"),
                    Instant.now().minus(10, ChronoUnit.HOURS),
                    Instant.now().minus(2, ChronoUnit.HOURS) // expired 2h ago
            );

            EntitlementVerificationResult result = verifier.verify(token);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("expired");
        }

        @Test
        @DisplayName("Rejects token with wrong issuer")
        void wrongIssuer() throws Exception {
            String token = buildSignedTokenCustomIssuer(
                    "wrong-issuer",
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("CAPTURE_VITALS"),
                    Instant.now().plus(7, ChronoUnit.HOURS)
            );

            EntitlementVerificationResult result = verifier.verify(token);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("Invalid issuer");
        }

        @Test
        @DisplayName("Rejects token with wrong audience")
        void wrongAudience() throws Exception {
            String token = buildSignedTokenCustomAudience(
                    "wrong-audience",
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("CAPTURE_VITALS"),
                    Instant.now().plus(7, ChronoUnit.HOURS)
            );

            EntitlementVerificationResult result = verifier.verify(token);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("Invalid audience");
        }

        @Test
        @DisplayName("Rejects token missing required capability")
        void missingCapability() throws Exception {
            String token = buildSignedToken(
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("READ_PATIENT"), // does NOT have CAPTURE_VITALS
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(7, ChronoUnit.HOURS)
            );

            EntitlementVerificationResult result = verifier.verify(token, "CAPTURE_VITALS");

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("Missing required capability");
        }

        @Test
        @DisplayName("Rejects token with invalid signature")
        void invalidSignature() throws Exception {
            // Sign with a different key
            OctetKeyPair differentKey = new OctetKeyPairGenerator(Curve.Ed25519)
                    .keyID("different-key")
                    .generate();

            String token = buildSignedTokenWithKey(
                    differentKey,
                    ACTOR_ID, TENANT_ID, FACILITY_ID,
                    List.of("CAPTURE_VITALS"),
                    Instant.now().plus(7, ChronoUnit.HOURS)
            );

            EntitlementVerificationResult result = verifier.verify(token);

            assertThat(result.valid()).isFalse();
            // Signature should fail since the key is not in the verifier's JWKS
        }

        @Test
        @DisplayName("Rejects null token")
        void nullToken() {
            EntitlementVerificationResult result = verifier.verify(null);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("null or empty");
        }

        @Test
        @DisplayName("Rejects empty token")
        void emptyToken() {
            EntitlementVerificationResult result = verifier.verify("");

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("null or empty");
        }

        @Test
        @DisplayName("Rejects malformed JWS")
        void malformedJws() {
            EntitlementVerificationResult result = verifier.verify("not-a-valid-jws");

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).contains("Invalid JWS format");
        }
    }

    // ------------------------------------------------------------------
    // Token building helpers
    // ------------------------------------------------------------------

    private static String buildSignedToken(String actorId, UUID tenantId, UUID facilityId,
                                           List<String> capabilities, Instant iat, Instant exp) throws Exception {
        return buildSignedTokenWithKey(signingKey, actorId, tenantId, facilityId, capabilities, exp);
    }

    private static String buildSignedTokenWithKey(OctetKeyPair key, String actorId, UUID tenantId,
                                                  UUID facilityId, List<String> capabilities,
                                                  Instant exp) throws Exception {
        Map<String, Object> claims = buildClaims(
                "tshepo-offline-service", "impilo-offline",
                actorId, tenantId, facilityId, capabilities, exp);
        return signClaims(key, claims);
    }

    private static String buildSignedTokenCustomIssuer(String issuer, String actorId, UUID tenantId,
                                                       UUID facilityId, List<String> capabilities,
                                                       Instant exp) throws Exception {
        Map<String, Object> claims = buildClaims(issuer, "impilo-offline",
                actorId, tenantId, facilityId, capabilities, exp);
        return signClaims(signingKey, claims);
    }

    private static String buildSignedTokenCustomAudience(String audience, String actorId, UUID tenantId,
                                                         UUID facilityId, List<String> capabilities,
                                                         Instant exp) throws Exception {
        Map<String, Object> claims = buildClaims("tshepo-offline-service", audience,
                actorId, tenantId, facilityId, capabilities, exp);
        return signClaims(signingKey, claims);
    }

    private static Map<String, Object> buildClaims(String issuer, String audience,
                                                    String actorId, UUID tenantId, UUID facilityId,
                                                    List<String> capabilities, Instant exp) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("iss", issuer);
        claims.put("sub", actorId);
        claims.put("aud", audience);
        claims.put("iat", Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond());
        claims.put("exp", exp.getEpochSecond());
        claims.put("tenant_id", tenantId.toString());
        claims.put("facility_id", facilityId.toString());
        claims.put("device_fingerprint", DEVICE_FP);
        claims.put("capabilities", capabilities);
        claims.put("max_offline_encounters", 50);
        return claims;
    }

    private static String signClaims(OctetKeyPair key, Map<String, Object> claims) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String payloadJson = mapper.writeValueAsString(claims);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                .keyID(key.getKeyID())
                .build();
        JWSObject jws = new JWSObject(header, new Payload(payloadJson));
        jws.sign(new Ed25519Signer(key));
        return jws.serialize();
    }
}
