package zw.gov.mohcc.impilo.wallet.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * B5: tells MusheX that a SMART-card offline payment settled a payment intent, so
 * Costa / Msika-flow / MusheX recognize the charge/order as paid — closing the gap
 * where card money was mushe-ledger-only. The card purse already moved the money in
 * the wallet ledger, so this only RECORDS the payment against the intent (like an
 * external rail); MusheX drives the intent to PAID and fans out
 * {@code mushex.payment.status.changed} to the existing consumers.
 *
 * <p>Called from the Kafka-thread-less redeem path, so v1.1 trust headers are
 * synthesized (the {@code service-to-service-v11-headers} pattern) plus
 * {@code X-Access-Mode: INTERNAL}. Fail-open: on any error it returns false and the
 * card payment still stands (the payer was debited) — the settlement is reconciled
 * later rather than reversing a completed debit.</p>
 */
@Component
public class MushexSettlementClient {

    private static final Logger log = LoggerFactory.getLogger(MushexSettlementClient.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String mushexBaseUrl;

    public MushexSettlementClient(
            @Value("${MUSHEX_BASE_URL:${mushe.mushex.base-url:http://localhost:8102}}") String mushexBaseUrl) {
        this.mushexBaseUrl = mushexBaseUrl.endsWith("/")
                ? mushexBaseUrl.substring(0, mushexBaseUrl.length() - 1) : mushexBaseUrl;
    }

    /**
     * Record a card-funded payment against a MusheX intent.
     * @return true if MusheX accepted it (intent driven to/at PAID), false on any failure (fail-open).
     */
    public boolean recordCardPayment(String intentId, BigDecimal amount, UUID tenantId, String cardReference) {
        if (intentId == null || intentId.isBlank() || amount == null || amount.signum() <= 0) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (tenantId != null) {
                headers.set("X-Tenant-ID", tenantId.toString());
            }
            headers.set("X-Pod-ID", "national");
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID", UUID.randomUUID().toString());
            headers.set("X-Access-Mode", "INTERNAL");
            headers.set("X-Service-Id", "mushe-wallet-service");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amount);
            if (cardReference != null) {
                body.put("cardReference", cardReference);
            }
            restTemplate.postForObject(
                    mushexBaseUrl + "/mushex/v1/payment-intents/" + intentId + "/record-card-payment",
                    new HttpEntity<>(body, headers), Object.class);
            log.info("Recorded card-funded settlement against MusheX intent {} (amount {})", intentId, amount);
            return true;
        } catch (RuntimeException e) {
            // Fail-open: the payer was already debited; log for reconciliation, don't reverse the payment.
            log.warn("MusheX card-settlement record failed for intent {} (payment stands, reconcile later): {}",
                    intentId, e.getMessage());
            return false;
        }
    }
}
