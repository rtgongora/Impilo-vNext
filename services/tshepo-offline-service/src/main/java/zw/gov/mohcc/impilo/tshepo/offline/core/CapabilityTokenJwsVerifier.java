package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Cryptographically verifies an offline capability token's JWS before any claim is
 * trusted. Verification is LOCAL (against a cached JWKS) so it works offline — the
 * keys-service is never contacted on the hot path (see {@link JwksCache}).
 *
 * <p>Fail-closed on: malformed token, unsupported algorithm, missing/unknown {@code kid},
 * bad signature, expired/not-yet-valid, wrong issuer, wrong audience, or a missing/empty
 * capability scope. Issuer and audience are checked against the configured expected values
 * so a validly-signed token minted for a different issuer/audience is not accepted.</p>
 */
@Component
public class CapabilityTokenJwsVerifier {

    private final JwksCache jwksCache;
    private final String expectedIssuer;
    private final String expectedAudience;

    public CapabilityTokenJwsVerifier(JwksCache jwksCache, OfflineProperties properties) {
        this.jwksCache = jwksCache;
        this.expectedIssuer = properties.tokenIssuer();
        this.expectedAudience = properties.tokenAudience();
    }

    public record Result(boolean valid, String reason, JWTClaimsSet claims) {
        static Result invalid(String reason) { return new Result(false, reason, null); }
        static Result valid(JWTClaimsSet claims) { return new Result(true, null, claims); }
    }

    public Result verify(String signedToken) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(signedToken);
        } catch (Exception e) {
            return Result.invalid("MALFORMED");
        }
        if (!JWSAlgorithm.EdDSA.equals(jwt.getHeader().getAlgorithm())) {
            return Result.invalid("UNSUPPORTED_ALG");
        }
        String kid = jwt.getHeader().getKeyID();
        if (kid == null || kid.isBlank()) {
            return Result.invalid("MISSING_KID");
        }

        // Resolve the signing key from the cached JWKS (offline). If the kid is unknown,
        // force a single refresh in case the key was rotated, then fail closed.
        JWK jwk;
        try {
            JWKSet set = jwksCache.get();
            jwk = set.getKeyByKeyId(kid);
            if (jwk == null) {
                try {
                    jwk = jwksCache.forceRefresh().getKeyByKeyId(kid);
                } catch (JwksUnavailableException ignored) {
                    // keep jwk null — refresh failed, fall through to UNKNOWN_KID
                }
            }
        } catch (JwksUnavailableException e) {
            return Result.invalid("JWKS_UNAVAILABLE");
        }
        if (jwk == null) {
            return Result.invalid("UNKNOWN_KID");
        }

        try {
            if (!jwt.verify(new Ed25519Verifier(jwk.toOctetKeyPair().toPublicJWK()))) {
                return Result.invalid("INVALID_SIGNATURE");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            Date exp = claims.getExpirationTime();
            if (exp != null && Instant.now().isAfter(exp.toInstant())) {
                return Result.invalid("EXPIRED");
            }
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && Instant.now().isBefore(nbf.toInstant())) {
                return Result.invalid("NOT_YET_VALID");
            }
            // Trust-claim checks (only after the signature has been verified).
            if (!expectedIssuer.equals(claims.getIssuer())) {
                return Result.invalid("WRONG_ISSUER");
            }
            List<String> aud = claims.getAudience();
            if (aud == null || !aud.contains(expectedAudience)) {
                return Result.invalid("WRONG_AUDIENCE");
            }
            Object capabilities = claims.getClaim("capabilities");
            if (!(capabilities instanceof List<?> caps) || caps.isEmpty()) {
                return Result.invalid("MISSING_SCOPE");
            }
            return Result.valid(claims);
        } catch (Exception e) {
            return Result.invalid("VERIFY_ERROR");
        }
    }
}
