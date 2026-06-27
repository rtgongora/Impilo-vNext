package zw.gov.mohcc.impilo.dags.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.PermitReplayEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.PermitReplayRepository;

/**
 * Issues and verifies signed permit tokens for approved DAGS requests.
 *
 * <p><b>Issuance</b> binds the full access context — tenant, requester (as a SHA-256
 * digest), request id, resource type/id, purpose, issue/expiry timestamps and a unique
 * nonce — into an HMAC-SHA256-signed token. The signing key is mandatory and must be a
 * strong secret: the service refuses to start with a blank or the historical insecure
 * default, since either would make every permit forgeable.</p>
 *
 * <p><b>Verification</b> ({@link #verifyAndConsume}) is what makes the signature mean
 * something (previously no consumer verified it): it checks the signature in constant
 * time, the expiry, and that every bound claim matches the enforcing caller's context,
 * then consumes the nonce so the token cannot be replayed. Every failure has a specific
 * {@link Reason} so the enforcement point can audit precisely why access was denied.</p>
 */
@Service
public class EnforcementService {

    private static final String INSECURE_DEFAULT = "change-me-in-prod";
    private static final String V2_PREFIX = "permit-token:v2:";
    private static final String ANY_PREFIX = "permit-token:";

    private final byte[] signingKey;
    private final long ttlSeconds;
    private final PermitReplayRepository replayRepository;

    public EnforcementService(@Value("${dags.enforcement.signing-key:}") String configuredKey,
                              @Value("${dags.enforcement.permit-ttl-seconds:3600}") long ttlSeconds,
                              PermitReplayRepository replayRepository) {
        if (configuredKey == null || configuredKey.isBlank() || INSECURE_DEFAULT.equals(configuredKey)) {
            throw new IllegalStateException(
                    "dags.enforcement.signing-key must be configured with a strong secret. "
                    + "Refusing to start with a blank or default ('" + INSECURE_DEFAULT + "') key — "
                    + "permit tokens would be forgeable by anyone who knows the source default.");
        }
        this.signingKey = configuredKey.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 3600;
        this.replayRepository = replayRepository;
    }

    /** The enforcing caller's claimed access context, checked against the token's bound claims. */
    public record PermitContext(UUID tenantId, String requesterId, String resourceType,
                                String resourceId, String purpose) {}

    /** Why a permit verification passed or failed. */
    public enum Reason {
        VALID, MALFORMED, UNSUPPORTED_VERSION, INVALID_SIGNATURE, EXPIRED,
        WRONG_TENANT, WRONG_REQUESTER, WRONG_RESOURCE, WRONG_PURPOSE, REPLAYED
    }

    public record PermitVerification(boolean valid, Reason reason) {
        static PermitVerification deny(Reason reason) { return new PermitVerification(false, reason); }
        static final PermitVerification OK = new PermitVerification(true, Reason.VALID);
    }

    /**
     * Issue a v2 permit token binding the full access context of an approved request.
     */
    public String issuePermitToken(AccessRequestEntity request) {
        long now = Instant.now().getEpochSecond();
        String requesterMarker = request.getRequesterId() == null ? "unknown" : request.getRequesterId();
        String payload = String.join("|",
                request.getTenantId().toString(),
                base64Url(sha256(requesterMarker)),
                String.valueOf(request.getId()),
                base64Url(nullToEmpty(request.getResourceType())),
                base64Url(nullToEmpty(request.getResourceId())),
                base64Url(nullToEmpty(request.getPurpose())),
                String.valueOf(now),
                String.valueOf(now + ttlSeconds),
                UUID.randomUUID().toString());
        String body = base64Url(payload);
        String signature = base64Url(sign(body));
        return V2_PREFIX + body + "." + signature;
    }

    /**
     * Verify a permit token against the enforcing caller's context and consume its nonce
     * (single use). Returns a specific {@link Reason} on any failure; never throws for a
     * bad token (only for an internal crypto fault).
     */
    public PermitVerification verifyAndConsume(String token, PermitContext context) {
        if (token == null || !token.startsWith(ANY_PREFIX)) {
            return PermitVerification.deny(Reason.MALFORMED);
        }
        if (!token.startsWith(V2_PREFIX)) {
            return PermitVerification.deny(Reason.UNSUPPORTED_VERSION);
        }
        String rest = token.substring(V2_PREFIX.length());
        int dot = rest.lastIndexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) {
            return PermitVerification.deny(Reason.MALFORMED);
        }
        String body = rest.substring(0, dot);
        String providedSigB64 = rest.substring(dot + 1);

        // 1. Signature (constant-time) — before trusting any claim.
        byte[] providedSig;
        try {
            providedSig = Base64.getUrlDecoder().decode(providedSigB64);
        } catch (IllegalArgumentException e) {
            return PermitVerification.deny(Reason.MALFORMED);
        }
        if (!MessageDigest.isEqual(sign(body), providedSig)) {
            return PermitVerification.deny(Reason.INVALID_SIGNATURE);
        }

        // 2. Parse the now-authenticated payload.
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return PermitVerification.deny(Reason.MALFORMED);
        }
        String[] f = payload.split("\\|", -1);
        if (f.length != 9) {
            return PermitVerification.deny(Reason.MALFORMED);
        }
        String tenant = f[0];
        String requesterHash = f[1];
        String requestIdStr = f[2];
        String resourceType = b64Decode(f[3]);
        String resourceId = b64Decode(f[4]);
        String purpose = b64Decode(f[5]);
        long exp;
        try {
            exp = Long.parseLong(f[7]);
        } catch (NumberFormatException e) {
            return PermitVerification.deny(Reason.MALFORMED);
        }
        String nonce = f[8];

        // 3. Expiry.
        if (Instant.now().getEpochSecond() > exp) {
            return PermitVerification.deny(Reason.EXPIRED);
        }

        // 4. Bound-claim checks against the enforcing context.
        if (context == null || context.tenantId() == null
                || !context.tenantId().toString().equals(tenant)) {
            return PermitVerification.deny(Reason.WRONG_TENANT);
        }
        String expectedRequesterHash = base64Url(sha256(
                context.requesterId() == null ? "unknown" : context.requesterId()));
        if (!expectedRequesterHash.equals(requesterHash)) {
            return PermitVerification.deny(Reason.WRONG_REQUESTER);
        }
        if (!nullToEmpty(context.resourceType()).equals(resourceType)
                || !nullToEmpty(context.resourceId()).equals(resourceId)) {
            return PermitVerification.deny(Reason.WRONG_RESOURCE);
        }
        if (!nullToEmpty(context.purpose()).equals(purpose)) {
            return PermitVerification.deny(Reason.WRONG_PURPOSE);
        }

        // 5. Replay: consume the nonce (single use). The unique constraint also closes the
        // race where two concurrent verifications pass the existence check.
        if (replayRepository.existsByNonce(nonce)) {
            return PermitVerification.deny(Reason.REPLAYED);
        }
        try {
            PermitReplayEntity consumed = new PermitReplayEntity();
            consumed.setTenantId(context.tenantId());
            consumed.setNonce(nonce);
            consumed.setRequestId(parseLongOrNull(requestIdStr));
            replayRepository.saveAndFlush(consumed);
        } catch (DataIntegrityViolationException duplicate) {
            return PermitVerification.deny(Reason.REPLAYED);
        }
        return PermitVerification.OK;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to sign permit token");
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to bind requester");
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String b64Decode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private String base64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String base64Url(String raw) {
        return base64Url(raw.getBytes(StandardCharsets.UTF_8));
    }
}
