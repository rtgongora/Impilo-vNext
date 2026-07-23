package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.core.CommitmentService;
import zw.gov.mohcc.impilo.msikaflow.core.PaymentService;

@Service
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;
    private final CommitmentService commitmentService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(PaymentService paymentService,
                                CommitmentService commitmentService,
                                ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.commitmentService = commitmentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "mushex.payment.status.changed",
                    // v1.1 canonical (envelope) topic
                    "impilo.mushex.payment"
            },
            groupId = "msika-flow-service"
    )
    public void onPaymentStatusChanged(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = unwrapPayload(root);

            String mushexPaymentIntentId = text(node, "intentId");
            if (mushexPaymentIntentId == null || mushexPaymentIntentId.isBlank()) {
                mushexPaymentIntentId = text(node, "paymentIntentId");
            }
            if (mushexPaymentIntentId == null) {
                mushexPaymentIntentId = node.path("paymentIntentId").asText("");
            }
            String status = text(node, "toStatus");
            if (status == null || status.isBlank()) {
                status = text(node, "status");
            }
            if (status == null || status.isBlank()) {
                status = node.path("status").asText("");
            }
            String actorId = text(node, "actorId");
            if (actorId == null || actorId.isBlank()) {
                actorId = "SYSTEM";
            }

            if (mushexPaymentIntentId.isBlank()) {
                log.warn("Payment event missing intentId / paymentIntentId");
                return;
            }

            // M3 BUG-7 — route by intent identity, each path with its OWN error
            // isolation. The marketplace resume runs FIRST: a marketplace
            // selection intent has no legacy mf_settlements row, so the legacy
            // cart path throws "Settlement not found" — that legacy-side failure
            // must never swallow the resume (it used to discard the whole event,
            // stranding every paid selection in AWAITING_PAYMENT until the TTL
            // sweep killed it with PAYMENT_TIMEOUT).
            //
            // OF-B10 — a held marketplace selection (AWAITING_PAYMENT) resumes
            // its guarded steps 9–12 on PAID, or compensates on terminal failure.
            // CC-2: the event itself never sets fulfilment state — the commitment
            // sequence (state machines + §13.4 gate) does.
            boolean marketplaceHandled = false;
            try {
                marketplaceHandled = commitmentService
                        .onPaymentStatusChanged(mushexPaymentIntentId, status)
                        .map(result -> {
                            log.info("Marketplace selection {} resumed from payment event: outcome={}",
                                    result.selection().getSelectionId(), result.outcomeCode());
                            return true;
                        })
                        .orElse(false);
            } catch (Exception e) {
                log.error("Marketplace payment resume failed for intent {} status {}: {}",
                        mushexPaymentIntentId, status, e.getMessage(), e);
            }

            // Legacy cart settlement path — isolated: an intent that maps to no
            // legacy settlement (marketplace-only intents) is a routine
            // routing miss, not an error; any other legacy failure is logged
            // without affecting the marketplace outcome above.
            try {
                paymentService.handlePaymentCallback(mushexPaymentIntentId, status, actorId);
            } catch (IllegalArgumentException e) {
                if (marketplaceHandled) {
                    log.debug("No legacy settlement for intent {} (marketplace selection handled): {}",
                            mushexPaymentIntentId, e.getMessage());
                } else {
                    log.warn("Payment event matched neither legacy settlement nor held marketplace selection: intent={} status={}: {}",
                            mushexPaymentIntentId, status, e.getMessage());
                }
            } catch (Exception e) {
                log.error("Legacy payment callback failed for intent {} status {}: {}",
                        mushexPaymentIntentId, status, e.getMessage(), e);
            }
            log.info("Processed payment event: mushexId={} status={}", mushexPaymentIntentId, status);

        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage(), e);
        }
    }

    private static JsonNode unwrapPayload(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) {
            return root.get("payload");
        }
        if (root.has("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
