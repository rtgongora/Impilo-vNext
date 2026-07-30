package zw.gov.mohcc.impilo.tshepo.contracts.trust;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The claim vocabulary of the signed decision envelope, and the one place its payload is
 * built (Phase D, D2).
 *
 * <p>The envelope answers a question the other obligation headers cannot: {@code x-decision:
 * ALLOW} is a claim any caller can type, so it is only evidence if it arrived from the gate.
 * The envelope is a short-lived JWS the PDP mints on ALLOW, and it is <em>bound</em> to the
 * request that earned it — actor, tenant, correlation, method, path, and a digest of the
 * obligations. Binding is what makes it non-transferable: lifting a genuine envelope off one
 * request and replaying it on another fails on the binding, not merely on expiry.</p>
 *
 * <p>Signer and verifier live in different services and will be changed by different people
 * at different times. If they computed the payload separately, any drift between them would
 * surface as a verification failure at best and a silent bypass at worst, so both sides call
 * {@link #build} and neither owns a private copy of the shape.</p>
 */
public final class DecisionEnvelopeClaims {

    private DecisionEnvelopeClaims() {}

    /** Issuer: the policy decision point. */
    public static final String ISSUER = "tshepo-authz";

    /**
     * Purpose claim, checked by {@code TrustExpectations.requiredPurpose}. A key authorised
     * for some other trust artefact must not produce something a verifier accepts as a
     * decision.
     */
    public static final String PURPOSE = "authz-decision";

    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_ISSUED_AT = "iat";
    public static final String CLAIM_EXPIRES_AT = "exp";
    public static final String CLAIM_JWT_ID = "jti";
    public static final String CLAIM_PURPOSE = "purpose";

    /** Actor type — an actor id alone does not say whether it is a human or a service. */
    public static final String CLAIM_ACTOR_TYPE = "act";
    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_CORRELATION_ID = "cid";
    /** Always {@code ALLOW}: the envelope is only minted on an allow, never on a denial. */
    public static final String CLAIM_DECISION = "dec";
    public static final String CLAIM_METHOD = "mth";
    public static final String CLAIM_PATH = "pth";
    /**
     * Digest of the serialized obligations. Without it a caller could keep a genuine
     * envelope and widen {@code x-obligations} beside it.
     */
    public static final String CLAIM_OBLIGATIONS_DIGEST = "obh";

    /**
     * Build the envelope payload for an allowed request.
     *
     * @param obligationsJson the exact serialization placed in {@code x-obligations}; the
     *                        digest must cover what the verifier will actually read, so this
     *                        is the string, not the object it came from
     */
    public static Map<String, Object> build(String actorId,
                                            String actorType,
                                            String tenantId,
                                            String correlationId,
                                            String method,
                                            String path,
                                            String obligationsJson,
                                            Instant issuedAt,
                                            Instant expiresAt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(CLAIM_ISSUER, ISSUER);
        claims.put(CLAIM_PURPOSE, PURPOSE);
        claims.put(CLAIM_JWT_ID, UUID.randomUUID().toString());
        claims.put(CLAIM_ISSUED_AT, issuedAt.getEpochSecond());
        claims.put(CLAIM_EXPIRES_AT, expiresAt.getEpochSecond());
        claims.put(CLAIM_DECISION, "ALLOW");
        claims.put(CLAIM_SUBJECT, nullSafe(actorId));
        claims.put(CLAIM_ACTOR_TYPE, nullSafe(actorType));
        claims.put(CLAIM_TENANT_ID, nullSafe(tenantId));
        claims.put(CLAIM_CORRELATION_ID, nullSafe(correlationId));
        claims.put(CLAIM_METHOD, nullSafe(method));
        claims.put(CLAIM_PATH, nullSafe(path));
        claims.put(CLAIM_OBLIGATIONS_DIGEST, digest(obligationsJson));
        return claims;
    }

    /**
     * Base64url SHA-256 of the obligations serialization. Absent obligations digest to the
     * digest of the empty string rather than to a null the verifier would have to special-case
     * — a claim that is sometimes missing is a claim that is sometimes unchecked.
     */
    public static String digest(String obligationsJson) {
        String source = obligationsJson == null ? "" : obligationsJson;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
