package zw.gov.mohcc.impilo.mushex.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment rail adapter for mobile money providers (e.g. EcoCash, OneMoney).
 * In production this would call the mobile money provider's REST API.
 * Current implementation logs the request and returns a PENDING status.
 */
@Component
public class MobileMoneyAdapter implements PaymentRailAdapter {

    private static final Logger log = LoggerFactory.getLogger(MobileMoneyAdapter.class);

    @Override
    public String adapterType() {
        return "MOBILE_MONEY";
    }

    @Override
    public AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency, Map<String, String> config) {
        String reference = "MOMO-" + UlidGenerator.generate();
        log.info("Initiating mobile money payment: intentId={}, amount={} {}, ref={}",
                intentId, amount, currency, reference);

        // In production: call mobile money provider API (e.g. EcoCash C2B)
        // String msisdn = config.get("msisdn");
        // String merchantCode = config.get("merchant_code");
        // Response response = momoClient.initiateC2B(msisdn, merchantCode, amount, reference);

        return new AdapterResponse(
                reference,
                "PENDING",
                "Mobile money payment initiated, awaiting customer confirmation",
                Map.of(
                        "intentId", intentId,
                        "channel", "MOBILE_MONEY",
                        "initiatedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }

    @Override
    public AdapterResponse checkStatus(String adapterRef, Map<String, String> config) {
        log.info("Checking mobile money payment status: ref={}", adapterRef);

        // In production: poll mobile money provider for transaction status
        return new AdapterResponse(
                adapterRef,
                "PENDING",
                "Transaction still pending customer confirmation",
                Map.of("checkedAt", java.time.OffsetDateTime.now().toString())
        );
    }

    @Override
    public boolean verifyWebhook(String signature, String payload, Map<String, String> config) {
        log.info("Verifying mobile money webhook signature");

        // In production: verify HMAC signature using provider's webhook secret
        // String secret = config.get("webhook_secret");
        // String computed = HmacUtils.computeHmacSha256(secret, payload);
        // return constantTimeEquals(computed, signature);

        return signature != null && !signature.isBlank();
    }

    @Override
    public AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> config) {
        String refundRef = "MOMO-REF-" + UlidGenerator.generate();
        log.info("Initiating mobile money refund: originalRef={}, amount={}, refundRef={}",
                adapterRef, amount, refundRef);

        // In production: call mobile money provider's reversal/refund API
        return new AdapterResponse(
                refundRef,
                "PENDING",
                "Mobile money refund initiated",
                Map.of(
                        "originalRef", adapterRef,
                        "refundAmount", amount.toPlainString(),
                        "initiatedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }
}
