package zw.gov.mohcc.impilo.integration.governance.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Generates and verifies HMAC-SHA256 signatures for outbound webhook deliveries
 * to external applications.
 *
 * <p>Signature header layout (sent on every outbound webhook):</p>
 * <pre>
 *   X-Impilo-Webhook-Signature: t=&lt;unix-seconds&gt;,v1=&lt;hex-hmac-sha256&gt;
 *   X-Impilo-Webhook-Delivery-Id: &lt;uuid&gt;
 *   X-Impilo-Webhook-Topic: &lt;event-topic&gt;
 * </pre>
 *
 * <p>The signed payload is the literal string {@code "<timestamp>.<body>"} where
 * {@code <body>} is the exact UTF-8 JSON delivered to the consumer. External
 * apps MUST verify the signature and reject any unverified delivery (see
 * docs/developer/WEBHOOK_GUIDE.md).</p>
 *
 * <p>Tolerance for clock skew: external apps should reject deliveries with a
 * timestamp more than 5 minutes from their wall clock.</p>
 */
@Component
public class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";
    public static final String SIGNATURE_HEADER = "X-Impilo-Webhook-Signature";
    public static final String DELIVERY_ID_HEADER = "X-Impilo-Webhook-Delivery-Id";
    public static final String TOPIC_HEADER = "X-Impilo-Webhook-Topic";

    public String sign(String secret, long timestampEpochSec, String body) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("HMAC secret must be non-empty");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            String payload = timestampEpochSec + "." + (body == null ? "" : body);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestampEpochSec + ",v1=" + HexFormat.of().formatHex(hmac);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign webhook payload", e);
        }
    }

    public String signNow(String secret, String body) {
        return sign(secret, Instant.now().getEpochSecond(), body);
    }

    /**
     * Verify a previously produced signature. Useful for tests and for any
     * inbound-from-trusted-partner scenario where the partner signs back.
     */
    public boolean verify(String secret, String body, String signatureHeaderValue, long maxClockSkewSec) {
        if (signatureHeaderValue == null) return false;
        String[] parts = signatureHeaderValue.split(",");
        Long ts = null;
        String v1 = null;
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) {
                try { ts = Long.parseLong(kv[1]); } catch (NumberFormatException ignored) { return false; }
            } else if ("v1".equals(kv[0])) {
                v1 = kv[1];
            }
        }
        if (ts == null || v1 == null) return false;
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > maxClockSkewSec) return false;
        String expected = sign(secret, ts, body);
        String expectedV1 = expected.substring(expected.indexOf("v1=") + 3);
        return constantTimeEquals(v1, expectedV1);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
