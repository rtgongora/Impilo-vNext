package zw.gov.mohcc.impilo.varapi.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.varapi.core.ProviderPaymentObligationService;

/**
 * Reconciles {@link zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPaymentObligationEntity}
 * when MusheX publishes payment intent status on Kafka (see mushex-service {@code OutboxPublisher}).
 */
@Component
@ConditionalOnProperty(
        prefix = "varapi.council-regulatory",
        name = "mushex-payment-status-kafka-enabled",
        havingValue = "true")
public class MushexPaymentStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(MushexPaymentStatusChangedListener.class);

    private final ObjectMapper objectMapper;
    private final ProviderPaymentObligationService obligationService;

    public MushexPaymentStatusChangedListener(
            ObjectMapper objectMapper,
            ProviderPaymentObligationService obligationService) {
        this.objectMapper = objectMapper;
        this.obligationService = obligationService;
    }

    @KafkaListener(
            topics = "mushex.payment.status.changed",
            groupId = "${spring.kafka.consumer.group-id:varapi-service}")
    public void onMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String intentId = root.path("intentId").asText("");
            String toStatus = root.path("toStatus").asText("");
            if (intentId.isBlank() || toStatus.isBlank()) {
                log.debug("Ignoring MusheX status event without intentId/toStatus: {}", payload);
                return;
            }
            obligationService.reconcileObligationFromMusheXKafka(intentId, toStatus);
        } catch (Exception e) {
            log.warn("Failed to process mushex.payment.status.changed payload: {}", e.getMessage());
        }
    }
}
