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

            paymentService.handlePaymentCallback(mushexPaymentIntentId, status, actorId);
            // OF-B10 — a held marketplace selection (AWAITING_PAYMENT) resumes
            // its guarded steps 9–12 on PAID, or compensates on terminal failure.
            // CC-2: the event itself never sets fulfilment state — the commitment
            // sequence (state machines + §13.4 gate) does.
            commitmentService.onPaymentStatusChanged(mushexPaymentIntentId, status)
                    .ifPresent(result -> log.info(
                            "Marketplace selection {} resumed from payment event: outcome={}",
                            result.selection().getSelectionId(), result.outcomeCode()));
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
