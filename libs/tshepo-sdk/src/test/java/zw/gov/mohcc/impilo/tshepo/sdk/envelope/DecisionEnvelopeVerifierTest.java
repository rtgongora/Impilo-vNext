package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.trust.DecisionEnvelopeClaims;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of these is not that a good envelope verifies — it is that each way of forging
 * or reusing one is refused, and refused for the right reason.
 */
class DecisionEnvelopeVerifierTest {

    private static final String ACTOR = "actor-1";
    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String OBLIGATIONS = "{\"maxScope\":\"read\"}";

    private OctetKeyPair pdpKey;
    private OctetKeyPair otherKey;
    private DecisionEnvelopeVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        pdpKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("pdp-1").generate();
        otherKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("not-the-pdp").generate();
        verifier = new DecisionEnvelopeVerifier(fixedKeys(pdpKey.toPublicJWK()), 60, false);
    }

    private static JwksSource fixedKeys(com.nimbusds.jose.jwk.JWK... keys) {
        JWKSet set = new JWKSet(java.util.List.of(keys));
        return new JwksSource("http://unused", java.time.Duration.ofHours(1), java.time.Duration.ofHours(1)) {
            @Override
            public JWKSet current() { return set; }
            @Override
            public boolean refreshForUnknownKid() { return false; }
        };
    }

    private String mint(OctetKeyPair key, Map<String, Object> overrides) throws Exception {
        Instant now = Instant.now();
        Map<String, Object> claims = DecisionEnvelopeClaims.build(
                ACTOR, "PROVIDER", TENANT, "cor-1", "GET", "/v1/patients/p1",
                OBLIGATIONS, now, now.plusSeconds(120));
        claims.putAll(overrides);

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        claims.forEach((k, v) -> {
            switch (k) {
                case DecisionEnvelopeClaims.CLAIM_ISSUER -> builder.issuer((String) v);
                case DecisionEnvelopeClaims.CLAIM_SUBJECT -> builder.subject((String) v);
                case DecisionEnvelopeClaims.CLAIM_JWT_ID -> builder.jwtID((String) v);
                case DecisionEnvelopeClaims.CLAIM_ISSUED_AT ->
                        builder.issueTime(java.util.Date.from(Instant.ofEpochSecond((Long) v)));
                case DecisionEnvelopeClaims.CLAIM_EXPIRES_AT ->
                        builder.expirationTime(java.util.Date.from(Instant.ofEpochSecond((Long) v)));
                default -> builder.claim(k, v);
            }
        });

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(key.getKeyID()).build(),
                builder.build());
        jwt.sign(new Ed25519Signer(key));
        return jwt.serialize();
    }

    private DecisionEnvelopeVerifier.RequestFacts facts(String envelope) {
        return new DecisionEnvelopeVerifier.RequestFacts(
                envelope, ACTOR, TENANT, "GET", "/v1/patients/p1", OBLIGATIONS);
    }

    @Test
    @DisplayName("a genuine envelope on its own request verifies")
    void genuineEnvelopeVerifies() throws Exception {
        assertThat(verifier.verify(facts(mint(pdpKey, Map.of()))).valid()).isTrue();
    }

    @Test
    @DisplayName("no envelope at all is refused — the default state of a caller that skipped the gate")
    void missingEnvelopeIsRefused() {
        DecisionEnvelopeVerifier.Result result = verifier.verify(facts(null));
        assertThat(result.valid()).isFalse();
        assertThat(result.rejection()).isEqualTo(DecisionEnvelopeVerifier.Rejection.MISSING);
    }

    @Test
    @DisplayName("an envelope signed by anything other than the PDP is refused")
    void wrongSignerIsRefused() throws Exception {
        DecisionEnvelopeVerifier.Result result = verifier.verify(facts(mint(otherKey, Map.of())));
        assertThat(result.valid()).isFalse();
        assertThat(result.rejection()).isEqualTo(DecisionEnvelopeVerifier.Rejection.SIGNATURE);
    }

    @Test
    @DisplayName("a genuine envelope replayed by a different actor is refused")
    void replayByAnotherActorIsRefused() throws Exception {
        String stolen = mint(pdpKey, Map.of());
        DecisionEnvelopeVerifier.Result result = verifier.verify(
                new DecisionEnvelopeVerifier.RequestFacts(
                        stolen, "someone-else", TENANT, "GET", "/v1/patients/p1", OBLIGATIONS));
        assertThat(result.rejection()).isEqualTo(DecisionEnvelopeVerifier.Rejection.ACTOR_MISMATCH);
    }

    @Test
    @DisplayName("a genuine envelope replayed against another tenant is refused")
    void crossTenantReplayIsRefused() throws Exception {
        String stolen = mint(pdpKey, Map.of());
        DecisionEnvelopeVerifier.Result result = verifier.verify(
                new DecisionEnvelopeVerifier.RequestFacts(stolen, ACTOR,
                        "22222222-2222-2222-2222-222222222222", "GET", "/v1/patients/p1", OBLIGATIONS));
        assertThat(result.rejection()).isEqualTo(DecisionEnvelopeVerifier.Rejection.TENANT_MISMATCH);
    }

    @Test
    @DisplayName("a read's envelope does not authorise the delete of the same resource")
    void methodIsBound() throws Exception {
        String readEnvelope = mint(pdpKey, Map.of());
        DecisionEnvelopeVerifier.Result result = verifier.verify(
                new DecisionEnvelopeVerifier.RequestFacts(
                        readEnvelope, ACTOR, TENANT, "DELETE", "/v1/patients/p1", OBLIGATIONS));
        assertThat(result.rejection()).isEqualTo(DecisionEnvelopeVerifier.Rejection.METHOD_MISMATCH);
    }

    @Test
    @DisplayName("keeping the envelope and widening the obligations beside it is refused")
    void wideningObligationsIsRefused() throws Exception {
        String envelope = mint(pdpKey, Map.of());
        DecisionEnvelopeVerifier.Result result = verifier.verify(
                new DecisionEnvelopeVerifier.RequestFacts(envelope, ACTOR, TENANT, "GET",
                        "/v1/patients/p1", "{\"maxScope\":\"write\"}"));
        assertThat(result.rejection()).isEqualTo(DecisionEnvelopeVerifier.Rejection.OBLIGATIONS_TAMPERED);
    }

    @Test
    @DisplayName("an expired envelope is refused")
    void expiredIsRefused() throws Exception {
        Instant longAgo = Instant.now().minusSeconds(3600);
        String stale = mint(pdpKey, Map.of(
                DecisionEnvelopeClaims.CLAIM_ISSUED_AT, longAgo.getEpochSecond(),
                DecisionEnvelopeClaims.CLAIM_EXPIRES_AT, longAgo.plusSeconds(120).getEpochSecond()));
        assertThat(verifier.verify(facts(stale)).rejection())
                .isEqualTo(DecisionEnvelopeVerifier.Rejection.SIGNATURE);
    }

    @Test
    @DisplayName("a signed envelope claiming something other than ALLOW is refused")
    void nonAllowDecisionIsRefused() throws Exception {
        String denyEnvelope = mint(pdpKey, Map.of(DecisionEnvelopeClaims.CLAIM_DECISION, "DENY"));
        assertThat(verifier.verify(facts(denyEnvelope)).rejection())
                .isEqualTo(DecisionEnvelopeVerifier.Rejection.NOT_AN_ALLOW);
    }

    @Test
    @DisplayName("an envelope minted for another purpose by the same key is refused")
    void wrongPurposeIsRefused() throws Exception {
        String otherPurpose = mint(pdpKey, Map.of(DecisionEnvelopeClaims.CLAIM_PURPOSE, "offline-capability"));
        assertThat(verifier.verify(facts(otherPurpose)).rejection())
                .isEqualTo(DecisionEnvelopeVerifier.Rejection.SIGNATURE);
    }

    @Test
    @DisplayName("an envelope from an unexpected issuer is refused")
    void wrongIssuerIsRefused() throws Exception {
        String impostor = mint(pdpKey, Map.of(DecisionEnvelopeClaims.CLAIM_ISSUER, "some-other-service"));
        assertThat(verifier.verify(facts(impostor)).rejection())
                .isEqualTo(DecisionEnvelopeVerifier.Rejection.SIGNATURE);
    }

    @Test
    @DisplayName("with no key set available, nothing verifies — unavailable keys never mean allow")
    void withoutKeysNothingVerifies() throws Exception {
        DecisionEnvelopeVerifier noKeys = new DecisionEnvelopeVerifier(
                new JwksSource("http://unused", java.time.Duration.ofHours(1), java.time.Duration.ofHours(1)) {
                    @Override
                    public JWKSet current() { return null; }
                }, 60, false);
        assertThat(noKeys.verify(facts(mint(pdpKey, Map.of()))).rejection())
                .isEqualTo(DecisionEnvelopeVerifier.Rejection.NO_KEYS);
    }

    @Test
    @DisplayName("path binding is off by default and enforced when switched on")
    void pathBindingIsOptIn() throws Exception {
        String envelope = mint(pdpKey, Map.of());
        DecisionEnvelopeVerifier.RequestFacts rewritten = new DecisionEnvelopeVerifier.RequestFacts(
                envelope, ACTOR, TENANT, "GET", "/v1/rewritten-by-the-gateway", OBLIGATIONS);

        // Default: a gateway that rewrites paths does not break every request.
        assertThat(verifier.verify(rewritten).valid()).isTrue();

        DecisionEnvelopeVerifier strict = new DecisionEnvelopeVerifier(
                fixedKeys(pdpKey.toPublicJWK()), 60, true);
        assertThat(strict.verify(rewritten).rejection())
                .isEqualTo(DecisionEnvelopeVerifier.Rejection.PATH_MISMATCH);
        assertThat(strict.verify(facts(envelope)).valid()).isTrue();
    }
}
