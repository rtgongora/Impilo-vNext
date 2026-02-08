package zw.gov.mohcc.impilo.mushex.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.service.UlidGenerator;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Sandbox adapter for testing and development.
 * Simulates immediate payment success with a configurable delay.
 * Never calls any external system.
 */
@Component
public class SandboxMockAdapter implements PaymentRailAdapter {

    private static final Logger log = LoggerFactory.getLogger(SandboxMockAdapter.class);

    private final MushexProperties properties;

    public SandboxMockAdapter(MushexProperties properties) {
        this.properties = properties;
    }

    @Override
    public String adapterType() {
        return "SANDBOX";
    }

    @Override
    public AdapterResponse initiatePayment(String intentId, BigDecimal amount, String currency, Map<String, String> config) {
        String reference = "SANDBOX-" + UlidGenerator.generate();
        log.info("Sandbox: simulating payment success: intentId={}, amount={} {}, ref={}",
                intentId, amount, currency, reference);

        simulateDelay();

        return new AdapterResponse(
                reference,
                "SUCCESS",
                "Sandbox payment simulated successfully",
                Map.of(
                        "intentId", intentId,
                        "channel", "SANDBOX",
                        "simulated", true,
                        "completedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }

    @Override
    public AdapterResponse checkStatus(String adapterRef, Map<String, String> config) {
        log.info("Sandbox: checking status for ref={}", adapterRef);

        simulateDelay();

        return new AdapterResponse(
                adapterRef,
                "SUCCESS",
                "Sandbox transaction completed",
                Map.of(
                        "simulated", true,
                        "checkedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }

    @Override
    public boolean verifyWebhook(String signature, String payload, Map<String, String> config) {
        log.info("Sandbox: auto-verifying webhook signature");
        return true;
    }

    @Override
    public AdapterResponse initiateRefund(String adapterRef, BigDecimal amount, Map<String, String> config) {
        String refundRef = "SANDBOX-REF-" + UlidGenerator.generate();
        log.info("Sandbox: simulating refund success: originalRef={}, amount={}, refundRef={}",
                adapterRef, amount, refundRef);

        simulateDelay();

        return new AdapterResponse(
                refundRef,
                "SUCCESS",
                "Sandbox refund simulated successfully",
                Map.of(
                        "originalRef", adapterRef,
                        "refundAmount", amount.toPlainString(),
                        "simulated", true,
                        "completedAt", java.time.OffsetDateTime.now().toString()
                )
        );
    }

    private void simulateDelay() {
        long delayMs = properties.getAdapters().getSandbox().getSimulateDelayMs();
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sandbox delay interrupted");
            }
        }
    }
}
