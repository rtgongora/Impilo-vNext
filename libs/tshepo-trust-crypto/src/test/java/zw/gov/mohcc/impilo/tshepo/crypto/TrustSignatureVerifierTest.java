package zw.gov.mohcc.impilo.tshepo.crypto;

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
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pass/fail proof of the Tshepo trust-crypto primitive: real Ed25519 JWS, canonical errors. */
class TrustSignatureVerifierTest {

    private OctetKeyPair signingKey;   // published in the JWKS
    private OctetKeyPair foreignKey;   // same kid, NOT published
    private TrustSignatureVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-1").generate();
        foreignKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("kid-1").generate();
        JWKSet jwks = new JWKSet(signingKey.toPublicJWK());
        verifier = new TrustSignatureVerifier(jwks);
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer("tshepo-offline-service")
                .audience("impilo-offline")
                .claim("purpose", "OFFLINE_CAPABILITY")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000));
    }

    private String sign(OctetKeyPair key, String kid, JWTClaimsSet c) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(kid).build(), c);
        jwt.sign(new Ed25519Signer(key));
        return jwt.serialize();
    }

    @Test
    void valid_noExpectations() throws Exception {
        var r = verifier.verify(sign(signingKey, "kid-1", claims().build()), TrustExpectations.none());
        assertThat(r.valid()).isTrue();
        assertThat(r.claims().getIssuer()).isEqualTo("tshepo-offline-service");
    }

    @Test
    void valid_withIssuerAudiencePurpose() throws Exception {
        TrustExpectations exp = TrustExpectations.builder()
                .withIssuers("tshepo-offline-service")
                .withAudience("impilo-offline")
                .withPurpose("OFFLINE_CAPABILITY");
        assertThat(verifier.verify(sign(signingKey, "kid-1", claims().build()), exp).valid()).isTrue();
    }

    @Test
    void malformed() {
        assertThat(verifier.verify("not-a-jws", TrustExpectations.none()).error()).isEqualTo(TrustError.MALFORMED);
    }

    @Test
    void unsupportedAlg() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("kid-1").build(),
                claims().build());
        jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
        assertThat(verifier.verify(jwt.serialize(), TrustExpectations.none()).error())
                .isEqualTo(TrustError.UNSUPPORTED_ALG);
    }

    @Test
    void unknownKey() throws Exception {
        assertThat(verifier.verify(sign(signingKey, "kid-other", claims().build()), TrustExpectations.none()).error())
                .isEqualTo(TrustError.UNKNOWN_KEY);
    }

    @Test
    void invalidSignature() throws Exception {
        assertThat(verifier.verify(sign(foreignKey, "kid-1", claims().build()), TrustExpectations.none()).error())
                .isEqualTo(TrustError.INVALID_SIGNATURE);
    }

    @Test
    void expired() throws Exception {
        JWTClaimsSet c = claims().expirationTime(new Date(System.currentTimeMillis() - 120_000)).build();
        assertThat(verifier.verify(sign(signingKey, "kid-1", c), TrustExpectations.none()).error())
                .isEqualTo(TrustError.EXPIRED);
    }

    @Test
    void unknownIssuer() throws Exception {
        TrustExpectations exp = TrustExpectations.builder().withIssuers("only-trusted-issuer");
        assertThat(verifier.verify(sign(signingKey, "kid-1", claims().build()), exp).error())
                .isEqualTo(TrustError.UNKNOWN_ISSUER);
    }

    @Test
    void wrongAudience() throws Exception {
        TrustExpectations exp = TrustExpectations.builder().withAudience("some-other-aud");
        assertThat(verifier.verify(sign(signingKey, "kid-1", claims().build()), exp).error())
                .isEqualTo(TrustError.WRONG_AUDIENCE);
    }

    @Test
    void wrongPurpose() throws Exception {
        TrustExpectations exp = TrustExpectations.builder().withPurpose("PERMIT");
        assertThat(verifier.verify(sign(signingKey, "kid-1", claims().build()), exp).error())
                .isEqualTo(TrustError.WRONG_PURPOSE);
    }

    @Test
    void unsupportedAlg_whenNotOnAllowlist() throws Exception {
        // Allowlist excludes EdDSA ⇒ even a correctly-signed Ed25519 token is rejected by policy.
        TrustSignatureVerifier rsOnly = new TrustSignatureVerifier(
                new JWKSet(signingKey.toPublicJWK()), Set.of(JWSAlgorithm.RS256), 60);
        assertThat(rsOnly.verify(sign(signingKey, "kid-1", claims().build()), TrustExpectations.none()).error())
                .isEqualTo(TrustError.UNSUPPORTED_ALG);
    }
}
