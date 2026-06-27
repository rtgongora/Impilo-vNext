package zw.gov.mohcc.impilo.mushex;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.mushex.service.HmacService;
import zw.gov.mohcc.impilo.mushex.service.WebhookSecurityService;
import zw.gov.mohcc.impilo.mushex.service.adapter.BankTransferAdapter;
import zw.gov.mohcc.impilo.mushex.service.adapter.CardGatewayAdapter;
import zw.gov.mohcc.impilo.mushex.service.adapter.MobileMoneyAdapter;
import zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the E1 fix (G…): the real payment rail adapters now perform genuine HMAC-SHA256
 * webhook verification (delegating to WebhookSecurityService) instead of accepting ANY
 * non-blank signature. A forged-but-non-blank signature — which the old stub accepted, so
 * an attacker could fake a payment confirmation — is now rejected.
 */
class AdapterWebhookVerificationTest {

    private static final String SECRET = "webhook-test-secret-key-2024";

    private List<PaymentRailAdapter> realAdapters() {
        WebhookSecurityService svc = new WebhookSecurityService(new HmacService(SECRET));
        return List.of(
                new MobileMoneyAdapter(svc),
                new BankTransferAdapter(svc),
                new CardGatewayAdapter(svc));
    }

    @Test
    void validHmacSignature_isAccepted() {
        String payload = "{\"status\":\"SUCCESS\",\"amount\":100.00}";
        String signature = hmacHex(payload, SECRET);
        for (PaymentRailAdapter adapter : realAdapters()) {
            assertThat(adapter.verifyWebhook(signature, payload, Map.of()))
                    .as("%s should accept a valid HMAC signature", adapter.adapterType())
                    .isTrue();
        }
    }

    @Test
    void forgedNonBlankSignature_isRejected() {
        String payload = "{\"status\":\"SUCCESS\",\"amount\":100.00}";
        // Non-blank but not the real HMAC — the OLD stub returned true for this (the vuln).
        String forged = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef00";
        for (PaymentRailAdapter adapter : realAdapters()) {
            assertThat(adapter.verifyWebhook(forged, payload, Map.of()))
                    .as("%s must reject a forged signature", adapter.adapterType())
                    .isFalse();
        }
    }

    @Test
    void blankOrNullSignature_isRejected() {
        String payload = "{\"x\":1}";
        for (PaymentRailAdapter adapter : realAdapters()) {
            assertThat(adapter.verifyWebhook("", payload, Map.of())).isFalse();
            assertThat(adapter.verifyWebhook(null, payload, Map.of())).isFalse();
        }
    }

    @Test
    void signatureForDifferentPayload_isRejected() {
        // A signature valid for one payload must not verify a tampered payload.
        String signature = hmacHex("{\"amount\":1}", SECRET);
        for (PaymentRailAdapter adapter : realAdapters()) {
            assertThat(adapter.verifyWebhook(signature, "{\"amount\":1000000}", Map.of())).isFalse();
        }
    }

    private static String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
