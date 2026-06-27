package zw.gov.mohcc.impilo.tshepo.crypto;

import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Outcome of a trust verification. On success {@code valid} is true, {@code error} is null
 * and {@code claims} carries the verified claim set. On failure {@code error} is the
 * canonical {@link TrustError} and {@code detail} a short human-readable note (never the
 * raw token); {@code claims} may be null.
 */
public record TrustVerificationResult(boolean valid, TrustError error, JWTClaimsSet claims, String detail) {

    public static TrustVerificationResult ok(JWTClaimsSet claims) {
        return new TrustVerificationResult(true, null, claims, null);
    }

    public static TrustVerificationResult fail(TrustError error, String detail) {
        return new TrustVerificationResult(false, error, null, detail);
    }
}
