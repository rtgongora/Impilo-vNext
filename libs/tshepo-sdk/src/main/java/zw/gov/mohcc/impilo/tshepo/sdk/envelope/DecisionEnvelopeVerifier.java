package zw.gov.mohcc.impilo.tshepo.sdk.envelope;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import zw.gov.mohcc.impilo.tshepo.contracts.trust.DecisionEnvelopeClaims;
import zw.gov.mohcc.impilo.tshepo.crypto.TrustError;
import zw.gov.mohcc.impilo.tshepo.crypto.TrustExpectations;
import zw.gov.mohcc.impilo.tshepo.crypto.TrustSignatureVerifier;
import zw.gov.mohcc.impilo.tshepo.crypto.TrustVerificationResult;

import java.util.Set;

/**
 * Verifies a decision envelope and checks that it belongs to <em>this</em> request (D3).
 *
 * <p>A valid signature only proves the PDP minted the envelope at some point. On its own
 * that would let a caller lift a genuine envelope from a request they were allowed to make
 * and replay it on one they were not, for the whole of its lifetime. The binding checks are
 * what close that: the envelope names an actor, a tenant, a method and a digest of the
 * obligations, and all of them must match what actually arrived.</p>
 */
public class DecisionEnvelopeVerifier {

    /** Why an envelope was not accepted. Distinct from {@link TrustError} so binding failures
     *  are not reported as cryptographic ones — they mean very different things in a log. */
    public enum Rejection {
        MISSING,
        NO_KEYS,
        SIGNATURE,
        NOT_AN_ALLOW,
        ACTOR_MISMATCH,
        TENANT_MISMATCH,
        METHOD_MISMATCH,
        PATH_MISMATCH,
        OBLIGATIONS_TAMPERED
    }

    public record Result(boolean valid, Rejection rejection, String detail) {
        static Result ok() { return new Result(true, null, null); }
        static Result no(Rejection rejection, String detail) { return new Result(false, rejection, detail); }
    }

    /** What the request actually presented, as the service sees it. */
    public record RequestFacts(String envelope,
                               String actorId,
                               String tenantId,
                               String method,
                               String path,
                               String obligations) {}

    private static final TrustExpectations EXPECTATIONS = TrustExpectations.none()
            .withIssuers(DecisionEnvelopeClaims.ISSUER)
            .withPurpose(DecisionEnvelopeClaims.PURPOSE);

    private final JwksSource jwksSource;
    private final int clockSkewSeconds;
    private final boolean bindPath;

    public DecisionEnvelopeVerifier(JwksSource jwksSource, int clockSkewSeconds, boolean bindPath) {
        this.jwksSource = jwksSource;
        this.clockSkewSeconds = clockSkewSeconds;
        this.bindPath = bindPath;
    }

    public Result verify(RequestFacts facts) {
        if (facts.envelope() == null || facts.envelope().isBlank()) {
            return Result.no(Rejection.MISSING, "no " + DecisionEnvelopeClaims.CLAIM_ISSUER + " envelope header");
        }

        JWKSet keys = jwksSource.current();
        if (keys == null) {
            return Result.no(Rejection.NO_KEYS, "no PDP key set available");
        }

        TrustVerificationResult signature = verifyAgainst(keys, facts.envelope());
        if (!signature.valid() && signature.error() == TrustError.UNKNOWN_KEY) {
            // A rotated key the service has not seen yet is the ordinary case, not an attack.
            if (jwksSource.refreshForUnknownKid()) {
                JWKSet refreshed = jwksSource.current();
                if (refreshed != null) {
                    signature = verifyAgainst(refreshed, facts.envelope());
                }
            }
        }
        if (!signature.valid()) {
            return Result.no(Rejection.SIGNATURE, signature.error() + ": " + signature.detail());
        }

        JWTClaimsSet claims = signature.claims();

        if (!"ALLOW".equals(stringClaim(claims, DecisionEnvelopeClaims.CLAIM_DECISION))) {
            // The PDP only mints envelopes on allow, so anything else is a forgery attempt
            // or a bug, and both should stop here rather than be treated as an allow.
            return Result.no(Rejection.NOT_AN_ALLOW, "decision claim was not ALLOW");
        }

        if (!matches(claims.getSubject(), facts.actorId())) {
            return Result.no(Rejection.ACTOR_MISMATCH, "envelope names a different actor");
        }
        if (!matches(stringClaim(claims, DecisionEnvelopeClaims.CLAIM_TENANT_ID), facts.tenantId())) {
            return Result.no(Rejection.TENANT_MISMATCH, "envelope names a different tenant");
        }
        if (!matches(stringClaim(claims, DecisionEnvelopeClaims.CLAIM_METHOD), facts.method())) {
            // Without this, a GET's envelope authorises the DELETE of the same resource.
            return Result.no(Rejection.METHOD_MISMATCH, "envelope was minted for a different method");
        }
        if (bindPath && !matches(stringClaim(claims, DecisionEnvelopeClaims.CLAIM_PATH), facts.path())) {
            return Result.no(Rejection.PATH_MISMATCH, "envelope was minted for a different path");
        }

        String expectedDigest = DecisionEnvelopeClaims.digest(facts.obligations());
        if (!expectedDigest.equals(stringClaim(claims, DecisionEnvelopeClaims.CLAIM_OBLIGATIONS_DIGEST))) {
            return Result.no(Rejection.OBLIGATIONS_TAMPERED,
                    "obligations do not match the ones the PDP signed");
        }

        return Result.ok();
    }

    private TrustVerificationResult verifyAgainst(JWKSet keys, String envelope) {
        return new TrustSignatureVerifier(keys,
                Set.of(com.nimbusds.jose.JWSAlgorithm.EdDSA), clockSkewSeconds)
                .verify(envelope, EXPECTATIONS);
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        return value == null ? null : value.toString();
    }

    /**
     * Compare a signed claim to what arrived. Both blank counts as a match — the PDP writes
     * an empty string for an absent value rather than omitting the claim, so an optional
     * field that is absent on both sides is agreement, not a mismatch.
     */
    private static boolean matches(String signed, String presented) {
        String a = signed == null ? "" : signed;
        String b = presented == null ? "" : presented;
        return a.equals(b);
    }
}
