package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.FacilityFeeService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationEntity;

import java.util.Optional;

/**
 * Closes the HPA registration-fee loop: MusheX publishes
 * {@code mushex.payment.status.changed} when an intent settles; this consumer
 * resolves the application by its stored payment reference (the MusheX intent
 * id) and, on PAID, flips the fee to PAID and advances the application off
 * AWAITING_FEE. Kafka threads carry no servlet TrustContext, so a system
 * context is synthesised for the transactional write (varapi reconciliation
 * pattern).
 */
@Component
public class MushexFeePaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(MushexFeePaymentConsumer.class);

    private final ObjectMapper objectMapper;
    private final FacilityFeeService facilityFeeService;

    public MushexFeePaymentConsumer(ObjectMapper objectMapper, FacilityFeeService facilityFeeService) {
        this.objectMapper = objectMapper;
        this.facilityFeeService = facilityFeeService;
    }

    @KafkaListener(topics = {"mushex.payment.status.changed", "impilo.mushex.payment"}, groupId = "tuso-service")
    public void onPaymentStatusChanged(String message) {
        try {
            JsonNode node = unwrap(objectMapper.readTree(message));
            String intentId = text(node, "intentId");
            if (intentId == null) intentId = text(node, "paymentIntentId");
            String status = text(node, "toStatus");
            if (status == null) status = text(node, "status");
            if (intentId == null || status == null) {
                return;
            }
            if (!"PAID".equalsIgnoreCase(status)) {
                return; // only settlement advances the application; other transitions are no-ops here
            }
            Optional<FacilityApplicationEntity> match = facilityFeeService.findByPaymentReference(intentId);
            if (match.isEmpty()) {
                // Not an HPA-fee intent (COSTA/marketplace intents share this topic) — ignore.
                return;
            }
            FacilityApplicationEntity application = match.get();
            TrustContext ctx = systemContext(application.getTenantId());
            TrustContextHolder.set(ctx);
            try {
                facilityFeeService.markPaid(ctx, application, intentId);
                log.info("HPA fee PAID reconciled for application {} (intent {})",
                        application.getApplicationId(), intentId);
            } finally {
                TrustContextHolder.clear();
            }
        } catch (Exception e) {
            log.error("Failed to process mushex payment status event: {}", e.getMessage(), e);
        }
    }

    private static TrustContext systemContext(java.util.UUID tenantId) {
        return new TrustContext(tenantId, "SYSTEM:MUSHEX-KAFKA", "SERVICE", "PAYMENT_RECONCILIATION",
                "kafka-consumer", null, null, null, null, AccessMode.INTERNAL);
    }

    private static JsonNode unwrap(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) return root.get("payload");
        if (root.has("data") && root.get("data").isObject()) return root.get("data");
        return root;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
