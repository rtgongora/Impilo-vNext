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

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Proves the REAL Ed25519 JWS verification against a JWKS (B2). Mints genuine signed
 * tokens with Nimbus and asserts that valid tokens pass while tampered, wrong-key,
 * unknown-kid, expired and wrong-algorithm tokens all fail closed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityTokenJwsVerifierTest {

    @Mock private KeysServiceClient keysClient;

    private OctetKeyPair signingKey;     // in the JWKS
    private OctetKeyPair foreignKey;     // NOT in the JWKS
    private CapabilityTokenJwsVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-test").generate();
        foreignKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-test").generate();
        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
        lenient().when(keysClient.fetchJwks()).thenReturn(jwks);
        verifier = new CapabilityTokenJwsVerifier(keysClient);
    }

    private String sign(OctetKeyPair key, String kid, Date exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject("actor-1")
                .expirationTime(exp)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(kid).build(), claims);
        jwt.sign(new Ed25519Signer(key));
        return jwt.serialize();
    }

    @Test
    @DisplayName("valid Ed25519 token verifies")
    void valid() throws Exception {
        String token = sign(signingKey, "kid-test", new Date(System.currentTimeMillis() + 3_600_000));
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isTrue();
        assertThat(r.claims().getJWTID()).isNotBlank();
    }

    @Test
    @DisplayName("token signed by a different key (same kid) fails: INVALID_SIGNATURE")
    void wrongKey_failsClosed() throws Exception {
        String token = sign(foreignKey, "kid-test", new Date(System.currentTimeMillis() + 3_600_000));
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("unknown kid not in JWKS fails: UNKNOWN_KID")
    void unknownKid_failsClosed() throws Exception {
        String token = sign(signingKey, "kid-other", new Date(System.currentTimeMillis() + 3_600_000));
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("UNKNOWN_KID");
    }

    @Test
    @DisplayName("expired token fails: EXPIRED")
    void expired_failsClosed() throws Exception {
        String token = sign(signingKey, "kid-test", new Date(System.currentTimeMillis() - 1000));
        CapabilityTokenJwsVerifier.Result r = verifier.verify(token);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("EXPIRED");
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
