package zw.gov.mohcc.impilo.mushex.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;
import zw.gov.mohcc.impilo.mushex.service.WebhookSecurityService;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment rail adapter for bank transfer / RTGS / ZIPIT channels.
 * In production this would call the bank's API or submit via file-based interface.
 * Current implementation logs the request and returns a PENDING status.
 */
@Component
public class BankTransferAdapter implements PaymentRailAdapter {

    private static final Logger log = LoggerFactory.getLogger(BankTransferAdapter.class);

    private final WebhookSecurityService webhookSecurityService;

    public BankTransferAdapter(WebhookSecurityService webhookSecurityService) {
        this.webhookSecurityService = webhookSecurityService;
    }

    @Override
    public String adapterType() {
        return "BANK_TRANSFER";
    }

    @Override
    public AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency, Map<String, String> config) {
        String reference = "BANK-" + UlidGenerator.generate();
        log.info("Initiating bank transfer payment: intentId={}, amount={} {}, ref={}",
                intentId, amount, currency, reference);

        // In production: call bank API (e.g. RTGS, ZIPIT) or enqueue file-based instruction
        // String accountNumber = config.get("account_number");
        // String bankCode = config.get("bank_code");
        // BankResponse resp = bankClient.initiate(accountNumber, bankCode, amount, reference);

        return new AdapterResponse(
                reference,
                "PENDING",
                "Bank transfer instruction submitted, awaiting settlement",
                Map.of(
                        "intentId", intentId,
                        "channel", "BANK_TRANSFER",
                        "initiatedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }

    @Override
    public AdapterResponse checkStatus(String adapterRef, Map<String, String> config) {
        log.info("Checking bank transfer status: ref={}", adapterRef);

        // In production: query bank for settlement status
        return new AdapterResponse(
                adapterRef,
                "PENDING",
                "Bank transfer settlement pending",
                Map.of("checkedAt", java.time.OffsetDateTime.now().toString())
        );
    }

    @Override
    public boolean verifyWebhook(String signature, String payload, Map<String, String> config) {
        // Real HMAC-SHA256 verification against the configured webhook secret (constant-time).
        return webhookSecurityService.verifyWebhook(payload, signature);
    }

    @Override
    public AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> config) {
        String refundRef = "BANK-REF-" + UlidGenerator.generate();
        log.info("Initiating bank transfer refund: originalRef={}, amount={}, refundRef={}",
                adapterRef, amount, refundRef);

        // In production: submit reversal instruction to bank
        return new AdapterResponse(
                refundRef,
                "PENDING",
                "Bank transfer refund instruction submitted",
                Map.of(
                        "originalRef", adapterRef,
                        "refundAmount", amount.toPlainString(),
                        "initiatedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }
}
