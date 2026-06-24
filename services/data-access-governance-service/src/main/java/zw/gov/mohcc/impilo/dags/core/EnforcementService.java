package zw.gov.mohcc.impilo.dags.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Issues signed permit tokens for approved DAGS requests.
 *
 * <p>The signing key is mandatory and must be a strong secret: the service refuses
 * to start with a blank or the historical insecure default, because either would
 * make every permit token forgeable by anyone who knows the source default. The
 * requester is cryptographically bound into the signed payload via a full SHA-256
 * digest (not a collision-prone 32-bit hashCode), and the HMAC-SHA256 signature is
 * used at full length.</p>
 */
@Service
public class EnforcementService {

    private static final String INSECURE_DEFAULT = "change-me-in-prod";

    private final byte[] signingKey;

    public EnforcementService(@Value("${dags.enforcement.signing-key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank() || INSECURE_DEFAULT.equals(configuredKey)) {
            throw new IllegalStateException(
                    "dags.enforcement.signing-key must be configured with a strong secret. "
                    + "Refusing to start with a blank or default ('" + INSECURE_DEFAULT + "') key — "
                    + "permit tokens would be forgeable by anyone who knows the source default.");
        }
        this.signingKey = configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    public String issuePermitToken(Long requestId, UUID tenantId, String requesterId) {
        String requesterMarker = requesterId == null ? "unknown" : requesterId;
        String payload = String.join("|",
                tenantId.toString(),
                base64Url(sha256(requesterMarker)),
                String.valueOf(requestId),
                String.valueOf(Instant.now().getEpochSecond()),
                UUID.randomUUID().toString().substring(0, 8));
        String body = base64Url(payload);
        String signature = base64Url(sign(body));
        return "permit-token:v1:" + body + "." + signature;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to issue signed permit token");
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to bind requester");
        }
    }

    private String base64Url(byte[] raw) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String base64Url(String raw) {
        return base64Url(raw.getBytes(StandardCharsets.UTF_8));
    }

}
