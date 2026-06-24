package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Proves the REAL Ed25519 JWS verification against a JWKS (B2) plus the B-core "b" trust
 * claim checks (G057): valid tokens pass; tampered, wrong-key, unknown-kid, expired,
 * wrong-algorithm, wrong-issuer, wrong-audience and missing-scope tokens all fail closed.
 * The JWKS is served via a real {@link JwksCache} over a mocked keys-service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityTokenJwsVerifierTest {

    private static final String ISSUER = "tshepo-offline-service";
    private static final String AUDIENCE = "impilo-offline";

    @Mock private KeysServiceClient keysClient;

    private OctetKeyPair signingKey;     // in the JWKS
    private OctetKeyPair foreignKey;     // NOT in the JWKS
    private CapabilityTokenJwsVerifier verifier;

    private static OfflineProperties props() {
        // Blank issuer/audience/ttl ⇒ defaults (tshepo-offline-service / impilo-offline / 3600).
        return new OfflineProperties(0, 0, 0, null, 0, 0, null, null, 0);
    }

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-test").generate();
        foreignKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-test").generate();
        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
        lenient().when(keysClient.fetchJwks()).thenReturn(jwks);
        verifier = new CapabilityTokenJwsVerifier(new JwksCache(keysClient, props()), props());
    }

    /** A fully-valid claim set (correct issuer, audience, non-empty scope, future expiry). */
    private JWTClaimsSet.Builder validClaims(Date exp) {
        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject("actor-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .claim("capabilities", List.of("READ_PATIENT"))
                .expirationTime(exp);
    }

    private String sign(OctetKeyPair key, String kid, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(kid).build(), claims);
        jwt.sign(new Ed25519Signer(key));
        return jwt.serialize();
    }

    private Date future() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    @Test
    @DisplayName("valid Ed25519 token verifies")
    void valid() throws Exception {
        String token = sign(signingKey, "kid-test", validClaims(future()).build());
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isTrue();
        assertThat(r.claims().getJWTID()).isNotBlank();
    }

    @Test
    @DisplayName("token signed by a different key (same kid) fails: INVALID_SIGNATURE")
    void wrongKey_failsClosed() throws Exception {
        String token = sign(foreignKey, "kid-test", validClaims(future()).build());
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("unknown kid not in JWKS fails: UNKNOWN_KID")
    void unknownKid_failsClosed() throws Exception {
        String token = sign(signingKey, "kid-other", validClaims(future()).build());
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("UNKNOWN_KID");
    }

    @Test
    @DisplayName("expired token fails: EXPIRED")
    void expired_failsClosed() throws Exception {
        String token = sign(signingKey, "kid-test", validClaims(new Date(System.currentTimeMillis() - 1000)).build());
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("wrong issuer fails: WRONG_ISSUER")
    void wrongIssuer_failsClosed() throws Exception {
        String token = sign(signingKey, "kid-test", validClaims(future()).issuer("evil-issuer").build());
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("WRONG_ISSUER");
    }

    @Test
    @DisplayName("wrong audience fails: WRONG_AUDIENCE")
    void wrongAudience_failsClosed() throws Exception {
        String token = sign(signingKey, "kid-test", validClaims(future()).audience("some-other-aud").build());
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("WRONG_AUDIENCE");
    }

    @Test
    @DisplayName("missing capability scope fails: MISSING_SCOPE")
    void missingScope_failsClosed() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject("actor-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(future())
                .build(); // no capabilities claim
        String token = sign(signingKey, "kid-test", claims);
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("MISSING_SCOPE");
    }

    @Test
    @DisplayName("wrong algorithm (HS256) fails: UNSUPPORTED_ALG")
    void wrongAlg_failsClosed() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("kid-test").build(), claims);
        jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef")); // 256-bit secret
        CapabilityTokenJwsVerifier.Result r = verifier.verify(jwt.serialize());
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("UNSUPPORTED_ALG");
    }

    @Test
    @DisplayName("malformed token fails: MALFORMED")
    void malformed_failsClosed() {
        CapabilityTokenJwsVerifier.Result r = verifier.verify("not-a-jws");
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("MALFORMED");
    }
}
