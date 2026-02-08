package zw.gov.mohcc.impilo.msikaflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.core.PaymentService;

@Service
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "mushex.payment.status_changed", groupId = "msika-flow-service")
    public void onPaymentStatusChanged(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String mushexPaymentIntentId = node.path("paymentIntentId").asText();
            String status = node.path("status").asText();
            String actorId = node.path("actorId").asText("SYSTEM");

            if (mushexPaymentIntentId.isBlank()) {
                log.warn("Payment event missing paymentIntentId");
                return;
            }

            paymentService.handlePaymentCallback(mushexPaymentIntentId, status, actorId);
            log.info("Processed payment event: mushexId={} status={}", mushexPaymentIntentId, status);

        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage(), e);
        }
    }
}
