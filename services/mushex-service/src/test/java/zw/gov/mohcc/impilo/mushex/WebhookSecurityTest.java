package zw.gov.mohcc.impilo.mushex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.service.HmacService;
import zw.gov.mohcc.impilo.mushex.service.WebhookSecurityService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebhookSecurityService}.
 *
 * Validates HMAC signature verification for incoming payment adapter
 * webhooks: valid signature acceptance, invalid signature rejection,
 * and missing signature handling.
 */
@ExtendWith(MockitoExtension.class)
class WebhookSecurityTest {

    private static final String WEBHOOK_SECRET = "webhook-test-secret-key-2024";

    private WebhookSecurityService webhookService;
    private HmacService hmacService;

    @BeforeEach
    void setUp() {
        hmacService = new HmacService(WEBHOOK_SECRET);
        webhookService = new WebhookSecurityService(hmacService);
    }

    // ---------------------------------------------------------------
    // verifyWebhook - valid signature
    // ---------------------------------------------------------------

    @Test
    void verifyWebhook_validHmacSignature_succeeds() {
        String payload = "{\"status\":\"SUCCESS\",\"amount\":100.00}";
        String signature = computeHmacSha256(payload, WEBHOOK_SECRET);

        boolean result = webhookService.verifyWebhook(payload, signature);

        assertTrue(result);
    }

    @Test
    void verifyWebhook_validSignature_differentPayloads() {
        String payload1 = "{\"ref\":\"PAY-001\",\"status\":\"COMPLETED\"}";
        String signature1 = computeHmacSha256(payload1, WEBHOOK_SECRET);
        assertTrue(webhookService.verifyWebhook(payload1, signature1));

        String payload2 = "simple-text-payload";
        String signature2 = computeHmacSha256(payload2, WEBHOOK_SECRET);
        assertTrue(webhookService.verifyWebhook(payload2, signature2));
    }

    @Test
    void verifyWebhook_validSignature_emptyPayload() {
        String payload = "";
        String signature = computeHmacSha256(payload, WEBHOOK_SECRET);

        boolean result = webhookService.verifyWebhook(payload, signature);

        assertTrue(result);
    }

    // ---------------------------------------------------------------
    // verifyWebhook - invalid signature
    // ---------------------------------------------------------------

    @Test
    void verifyWebhook_invalidSignature_fails() {
        String payload = "{\"status\":\"SUCCESS\",\"amount\":100.00}";
        String wrongSignature = "0000000000000000000000000000000000000000000000000000000000000000";

        boolean result = webhookService.verifyWebhook(payload, wrongSignature);

        assertFalse(result);
    }

    @Test
    void verifyWebhook_tamperedPayload_fails() {
        String originalPayload = "{\"status\":\"SUCCESS\",\"amount\":100.00}";
        String signature = computeHmacSha256(originalPayload, WEBHOOK_SECRET);

        // Attacker modifies payload
        String tamperedPayload = "{\"status\":\"SUCCESS\",\"amount\":999999.99}";

        boolean result = webhookService.verifyWebhook(tamperedPayload, signature);

        assertFalse(result);
    }

    @Test
    void verifyWebhook_wrongSecret_fails() {
        String payload = "{\"status\":\"SUCCESS\"}";
        String signedWithWrongKey = computeHmacSha256(payload, "wrong-secret-key");

        boolean result = webhookService.verifyWebhook(payload, signedWithWrongKey);

        assertFalse(result);
    }

    @Test
    void verifyWebhook_truncatedSignature_fails() {
        String payload = "{\"data\":\"test\"}";
        String fullSignature = computeHmacSha256(payload, WEBHOOK_SECRET);
        String truncated = fullSignature.substring(0, 10);

        boolean result = webhookService.verifyWebhook(payload, truncated);

        assertFalse(result);
    }

    @Test
    void verifyWebhook_extraCharsInSignature_fails() {
        String payload = "{\"data\":\"test\"}";
        String fullSignature = computeHmacSha256(payload, WEBHOOK_SECRET);
        String extended = fullSignature + "extra";

        boolean result = webhookService.verifyWebhook(payload, extended);

        assertFalse(result);
    }

    // ---------------------------------------------------------------
    // verifyWebhook - missing signature
    // ---------------------------------------------------------------

    @Test
    void verifyWebhook_nullSignature_fails() {
        String payload = "{\"status\":\"SUCCESS\"}";

        boolean result = webhookService.verifyWebhook(payload, null);

        assertFalse(result);
    }

    @Test
    void verifyWebhook_emptySignature_fails() {
        String payload = "{\"status\":\"SUCCESS\"}";

        boolean result = webhookService.verifyWebhook(payload, "");

        assertFalse(result);
    }

    @Test
    void verifyWebhook_blankSignature_fails() {
        String payload = "{\"status\":\"SUCCESS\"}";

        boolean result = webhookService.verifyWebhook(payload, "   ");

        assertFalse(result);
    }

    // ---------------------------------------------------------------
    // verifyWebhook - null payload
    // ---------------------------------------------------------------

    @Test
    void verifyWebhook_nullPayload_fails() {
        boolean result = webhookService.verifyWebhook(null, "some-signature");

        assertFalse(result);
    }

    // ---------------------------------------------------------------
    // Signature consistency
    // ---------------------------------------------------------------

    @Test
    void verifyWebhook_samePayload_producesConsistentResult() {
        String payload = "{\"ref\":\"PAY-CONSISTENT\"}";
        String signature = computeHmacSha256(payload, WEBHOOK_SECRET);

        // Verify multiple times to ensure deterministic behavior
        for (int i = 0; i < 10; i++) {
            assertTrue(webhookService.verifyWebhook(payload, signature),
                "Verification should be consistent on iteration " + i);
        }
    }

    @Test
    void verifyWebhook_specialCharactersInPayload_works() {
        String payload = "{\"name\":\"O'Brien\",\"notes\":\"line1\\nline2\",\"emoji\":\"\\u2764\"}";
        String signature = computeHmacSha256(payload, WEBHOOK_SECRET);

        assertTrue(webhookService.verifyWebhook(payload, signature));
    }

    @Test
    void verifyWebhook_largePayload_works() {
        // Simulate a large webhook payload
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(i).append(",\"amount\":").append(i * 10).append("}");
        }
        sb.append("]}");
        String payload = sb.toString();
        String signature = computeHmacSha256(payload, WEBHOOK_SECRET);

        assertTrue(webhookService.verifyWebhook(payload, signature));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Compute HMAC-SHA256 hex string for test verification.
     * This mirrors what an external payment adapter would compute
     * when signing a webhook payload.
     */
    private static String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
}
