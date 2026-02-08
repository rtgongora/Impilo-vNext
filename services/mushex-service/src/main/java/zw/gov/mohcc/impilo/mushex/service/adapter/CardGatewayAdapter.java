package zw.gov.mohcc.impilo.mushex.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment rail adapter for card-based payment gateways (Visa, Mastercard, etc.).
 * In production this would call the payment gateway's tokenization and charge APIs.
 * Current implementation logs the request and returns a PENDING status.
 */
@Component
public class CardGatewayAdapter implements PaymentRailAdapter {

    private static final Logger log = LoggerFactory.getLogger(CardGatewayAdapter.class);

    @Override
    public String adapterType() {
        return "CARD_GATEWAY";
    }

    @Override
    public AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency, Map<String, String> config) {
        String reference = "CARD-" + UlidGenerator.generate();
        log.info("Initiating card gateway payment: intentId={}, amount={} {}, ref={}",
                intentId, amount, currency, reference);

        // In production: call payment gateway API (e.g. Paynow, DPO)
        // String tokenizedCard = config.get("card_token");
        // String merchantId = config.get("merchant_id");
        // GatewayResponse resp = gatewayClient.charge(tokenizedCard, merchantId, amount, currency, reference);

        return new AdapterResponse(
                reference,
                "PENDING",
                "Card payment authorization initiated",
                Map.of(
                        "intentId", intentId,
                        "channel", "CARD_GATEWAY",
                        "initiatedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }

    @Override
    public AdapterResponse checkStatus(String adapterRef, Map<String, String> config) {
        log.info("Checking card gateway payment status: ref={}", adapterRef);

        // In production: query gateway for transaction status
        return new AdapterResponse(
                adapterRef,
                "PENDING",
                "Card payment authorization pending",
                Map.of("checkedAt", java.time.OffsetDateTime.now().toString())
        );
    }

    @Override
    public boolean verifyWebhook(String signature, String payload, Map<String, String> config) {
        log.info("Verifying card gateway webhook signature");

        // In production: verify HMAC or RSA signature using gateway's webhook key
        // String webhookKey = config.get("webhook_key");
        // return SignatureVerifier.verify(webhookKey, payload, signature);

        return signature != null && !signature.isBlank();
    }

    @Override
    public AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> config) {
        String refundRef = "CARD-REF-" + UlidGenerator.generate();
        log.info("Initiating card gateway refund: originalRef={}, amount={}, refundRef={}",
                adapterRef, amount, refundRef);

        // In production: call gateway's refund/reversal API
        return new AdapterResponse(
                refundRef,
                "PENDING",
                "Card refund initiated",
                Map.of(
                        "originalRef", adapterRef,
                        "refundAmount", amount.toPlainString(),
                        "initiatedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }
}
