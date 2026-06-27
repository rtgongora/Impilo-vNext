package zw.gov.mohcc.impilo.tshepo.crypto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * The Tshepo trust-plane signature primitive: verifies a compact JWS against a trusted
 * key set with kid resolution and an algorithm allowlist, then applies optional issuer /
 * audience / purpose expectations — mapping every failure to a canonical {@link TrustError}.
 *
 * <p>This is intentionally a small, dependency-light building block (Nimbus only). It does
 * not perform revocation or trust-boundary checks itself — those are consumer concerns,
 * with {@link TrustError#REVOKED} / {@link TrustError#TRUST_BOUNDARY} reserved in the model
 * so consumers report them uniformly. The verification semantics mirror the existing
 * {@code OfflineEntitlementVerifier} / {@code CapabilityTokenJwsVerifier} but with the
 * canonical error model rather than ad-hoc strings.</p>
 */
public final class TrustSignatureVerifier {

    private static final int DEFAULT_CLOCK_SKEW_SECONDS = 60;

    private final JWKSet jwkSet;
    private final Set<JWSAlgorithm> allowedAlgorithms;
    private final long clockSkewSeconds;

    /** Default: Ed25519 (EdDSA) only, 60s clock skew. */
    public TrustSignatureVerifier(JWKSet jwkSet) {
        this(jwkSet, Set.of(JWSAlgorithm.EdDSA), DEFAULT_CLOCK_SKEW_SECONDS);
    }

    public TrustSignatureVerifier(JWKSet jwkSet, Set<JWSAlgorithm> allowedAlgorithms, int clockSkewSeconds) {
        if (jwkSet == null) {
            throw new IllegalArgumentException("jwkSet must not be null");
        }
        if (allowedAlgorithms == null || allowedAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("allowedAlgorithms must not be empty");
        }
        this.jwkSet = jwkSet;
        this.allowedAlgorithms = Set.copyOf(allowedAlgorithms);
        this.clockSkewSeconds = Math.max(0, clockSkewSeconds);
    }

    public TrustVerificationResult verify(String compactJws, TrustExpectations expectations) {
        TrustExpectations exp = expectations == null ? TrustExpectations.none() : expectations;

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(compactJws);
        } catch (Exception e) {
            return TrustVerificationResult.fail(TrustError.MALFORMED, "unparseable JWS");
        }

        JWSAlgorithm alg = jwt.getHeader().getAlgorithm();
        if (alg == null || !allowedAlgorithms.contains(alg)) {
            return TrustVerificationResult.fail(TrustError.UNSUPPORTED_ALG, String.valueOf(alg));
        }

        String kid = jwt.getHeader().getKeyID();
        JWK jwk = kid == null ? null : jwkSet.getKeyByKeyId(kid);
        if (jwk == null) {
            return TrustVerificationResult.fail(TrustError.UNKNOWN_KEY, "kid=" + kid);
        }

        JWSVerifier verifier;
        try {
            verifier = resolveVerifier(jwk);
        } catch (Exception e) {
            return TrustVerificationResult.fail(TrustError.UNKNOWN_KEY, "unusable key for kid=" + kid);
        }

        try {
            if (!jwt.verify(verifier)) {
                return TrustVerificationResult.fail(TrustError.INVALID_SIGNATURE, "signature mismatch");
            }
        } catch (Exception e) {
            return TrustVerificationResult.fail(TrustError.INVALID_SIGNATURE, "verify error");
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (Exception e) {
            return TrustVerificationResult.fail(TrustError.MALFORMED, "unparseable claims");
        }

        Instant now = Instant.now();
        Date expiry = claims.getExpirationTime();
        if (expiry != null && now.minusSeconds(clockSkewSeconds).isAfter(expiry.toInstant())) {
            return TrustVerificationResult.fail(TrustError.EXPIRED, "exp passed");
        }
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && now.plusSeconds(clockSkewSeconds).isBefore(notBefore.toInstant())) {
            return TrustVerificationResult.fail(TrustError.NOT_YET_VALID, "nbf in future");
        }

        if (exp.allowedIssuers() != null && !exp.allowedIssuers().isEmpty()
                && !exp.allowedIssuers().contains(claims.getIssuer())) {
            return TrustVerificationResult.fail(TrustError.UNKNOWN_ISSUER, String.valueOf(claims.getIssuer()));
        }
        if (exp.requiredAudience() != null) {
            List<String> aud = claims.getAudience();
            if (aud == null || !aud.contains(exp.requiredAudience())) {
                return TrustVerificationResult.fail(TrustError.WRONG_AUDIENCE, String.valueOf(aud));
            }
        }
        if (exp.requiredPurpose() != null) {
            Object purpose = claims.getClaim("purpose");
            if (!exp.requiredPurpose().equals(purpose)) {
                return TrustVerificationResult.fail(TrustError.WRONG_PURPOSE, String.valueOf(purpose));
            }
        }

        return TrustVerificationResult.ok(claims);
    }

    private JWSVerifier resolveVerifier(JWK jwk) throws Exception {
        if (jwk instanceof OctetKeyPair okp) {
            return new Ed25519Verifier(okp.toPublicJWK());
        }
        if (jwk instanceof RSAKey rsa) {
            return new RSASSAVerifier(rsa.toRSAPublicKey());
        }
        if (jwk instanceof ECKey ec) {
            return new ECDSAVerifier(ec.toECPublicKey());
        }
        throw new IllegalArgumentException("Unsupported key type: " + jwk.getKeyType());
    }
}
