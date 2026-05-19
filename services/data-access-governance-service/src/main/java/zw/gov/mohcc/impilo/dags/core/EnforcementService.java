package zw.gov.mohcc.impilo.dags.core;

import java.nio.charset.StandardCharsets;
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
 */
@Service
public class EnforcementService {

    private final String signingKey;

    public EnforcementService(@Value("${dags.enforcement.signing-key:change-me-in-prod}") String signingKey) {
        this.signingKey = signingKey;
    }

    public String issuePermitToken(Long requestId, UUID tenantId, String requesterId) {
        String requesterMarker = requesterId == null ? "unknown" : requesterId;
        String payload = String.join("|",
                tenantId.toString(),
                Integer.toHexString(requesterMarker.hashCode()),
                String.valueOf(requestId),
                String.valueOf(Instant.now().getEpochSecond()),
                UUID.randomUUID().toString().substring(0, 8));
        String body = base64Url(payload);
        String signature = base64Url(truncateSignature(sign(body)));
        return "permit-token:v1:" + body + "." + signature;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to issue signed permit token");
        }
    }

    private byte[] truncateSignature(byte[] raw) {
        if (raw.length <= 16) {
            return raw;
        }
        byte[] truncated = new byte[16];
        System.arraycopy(raw, 0, truncated, 0, truncated.length);
        return truncated;
    }

    private String base64Url(byte[] raw) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String base64Url(String raw) {
        return base64Url(raw.getBytes(StandardCharsets.UTF_8));
    }

}
