package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;

import java.time.Instant;
import java.util.Date;

/**
 * Cryptographically verifies an offline capability token's JWS before any claim is
 * trusted. Verification is LOCAL (against the JWKS public keys) so it works offline —
 * the keys-service is never contacted to verify a token. Fail-closed: a malformed
 * token, unsupported algorithm, missing/unknown {@code kid}, bad signature, or an
 * expired/not-yet-valid token all return invalid.
 */
@Component
public class CapabilityTokenJwsVerifier {

    private static final Logger log = LoggerFactory.getLogger(CapabilityTokenJwsVerifier.class);

    private final KeysServiceClient keysClient;

    public CapabilityTokenJwsVerifier(KeysServiceClient keysClient) {
        this.keysClient = keysClient;
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
        JWK jwk;
        try {
            jwk = JWKSet.parse(keysClient.fetchJwks()).getKeyByKeyId(kid);
        } catch (Exception e) {
            log.warn("Capability token verification: JWKS unavailable", e);
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
            return Result.valid(claims);
        } catch (Exception e) {
            return Result.invalid("VERIFY_ERROR");
        }
    }
}
