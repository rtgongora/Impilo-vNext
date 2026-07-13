package zw.gov.mohcc.impilo.mushex.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.integration.paynow.PaynowClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The Paynow aggregator rail — registered as the CARD_GATEWAY adapter when
 * {@code mushex.adapters.paynow.enabled=true} (replacing the stub). One
 * commercial integration lights up EcoCash, OneMoney, InnBucks, ZimSwitch and
 * Visa/Mastercard: the citizen's chosen method travels in the attempt config
 * ({@code method} + {@code phone} → express USSD push; otherwise the standard
 * browser-redirect checkout).
 *
 * <p>{@link #liveCapable()} is true ONLY when real credentials are configured —
 * the attempt-time safety gate stays closed for a credential-less deploy.
 * Settlement completes asynchronously: the status poller drives
 * {@link #checkStatus} on the transaction's poll URL, and Paynow's result POST
 * lands on {@code PaynowResultController} — both hash-verified.</p>
 */
@Component
@ConditionalOnProperty(name = "mushex.adapters.paynow.enabled", havingValue = "true")
public class PaynowGatewayAdapter implements PaymentRailAdapter {

    private static final Logger log = LoggerFactory.getLogger(PaynowGatewayAdapter.class);

    private final PaynowClient client;
    private final MushexProperties.Paynow config;

    public PaynowGatewayAdapter(MushexProperties properties, RestClient.Builder restClientBuilder) {
        this.config = properties.getAdapters().getPaynow();
        this.client = new PaynowClient(
                restClientBuilder,
                config.getBaseUrl(),
                config.getIntegrationId(),
                config.getIntegrationKey(),
                config.getResultUrl(),
                config.getReturnUrl());
    }

    /** Exposed for the result controller's hash verification. */
    public PaynowClient client() {
        return client;
    }

    @Override
    public String adapterType() {
        return "CARD_GATEWAY";
    }

    @Override
    public boolean liveCapable() {
        return config.isEnabled()
                && !config.getIntegrationId().isBlank()
                && !config.getIntegrationKey().isBlank();
    }

    @Override
    public AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency,
                                           Map<String, String> config) {
        String method = config.get("method");
        String phone = config.get("phone");

        PaynowClient.InitiateResult result;
        if (method != null && !method.isBlank() && phone != null && !phone.isBlank()) {
            log.info("Paynow express checkout: intent={} method={} amount={} {}",
                    intentId, method, amount, currency);
            result = client.initiateExpressCheckout(intentId, amount,
                    "Impilo payment " + intentId, method, phone);
        } else {
            log.info("Paynow standard checkout: intent={} amount={} {}", intentId, amount, currency);
            result = client.initiateTransaction(intentId, amount, "Impilo payment " + intentId);
        }

        if (!result.ok()) {
            return new AdapterResponse(null, "FAILED",
                    "Paynow initiate failed: " + result.error(), Map.of("intentId", intentId));
        }
        // The poll URL IS the durable external reference — unique per transaction,
        // and everything downstream (poller, result post) keys off it.
        return new AdapterResponse(
                result.pollUrl(),
                "PENDING",
                "Paynow transaction initiated; awaiting customer confirmation",
                mapOfNonNull("intentId", intentId,
                        "browserUrl", result.browserUrl(),
                        "pollUrl", result.pollUrl()));
    }

    @Override
    public AdapterResponse checkStatus(String adapterRef, Map<String, String> config) {
        PaynowClient.StatusResult status = client.pollStatus(adapterRef);
        if (!status.hashValid()) {
            log.error("Paynow poll response failed hash verification for {} — treating as PENDING", adapterRef);
            return new AdapterResponse(adapterRef, "PENDING",
                    "Poll response failed hash verification; will retry", Map.of());
        }
        String mapped = status.paid() ? "SUCCESS"
                : status.cancelled() ? "CANCELLED"
                : "PENDING";
        return new AdapterResponse(adapterRef, mapped,
                "Paynow status: " + status.status(),
                mapOfNonNull("paynowReference", status.paynowReference(),
                        "reference", status.reference()));
    }

    @Override
    public boolean verifyWebhook(String signature, String payload, Map<String, String> config) {
        // Paynow's integrity check is the in-payload hash (SHA-512 of field
        // values + integration key), not a header signature.
        return client.verifyHash(PaynowClient.parseForm(payload));
    }

    @Override
    public AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> config) {
        // Paynow exposes no refund API — refunds are actioned in the Paynow
        // merchant dashboard. Fail honestly rather than pretending.
        return new AdapterResponse(adapterRef, "FAILED",
                "Paynow refunds are manual (merchant dashboard); no API refund exists",
                Map.of("requestedAmount", amount.toPlainString()));
    }

    private static Map<String, Object> mapOfNonNull(String k1, String v1, String k2, String v2) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (v1 != null) m.put(k1, v1);
        if (v2 != null) m.put(k2, v2);
        return m;
    }

    private static Map<String, Object> mapOfNonNull(String k1, String v1, String k2, String v2,
                                                    String k3, String v3) {
        Map<String, Object> m = mapOfNonNull(k1, v1, k2, v2);
        if (v3 != null) m.put(k3, v3);
        return m;
    }
}
