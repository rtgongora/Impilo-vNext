package zw.gov.mohcc.impilo.cardprint.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.cardprint.generator.CardPayloadGenerator;

@Component
public class PrintJobListener {

    private static final Logger log = LoggerFactory.getLogger(PrintJobListener.class);

    private final ObjectMapper objectMapper;
    private final CardPayloadGenerator generator;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PrintJobListener(ObjectMapper objectMapper, CardPayloadGenerator generator,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.generator = generator;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "vito.print", groupId = "card-print-agent")
    public void onPrintJob(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            long cardId = node.path("cardId").asLong();
            String template = node.path("template").asText("STANDARD");
            String did = node.path("did").asText();

            log.info("Processing print job for card {} with template {}", cardId, template);

            byte[] payload = generator.generate(cardId, template, did);

            log.info("Generated print payload for card {} ({} bytes)", cardId, payload.length);

            // Emit audit event
            String auditEvent = objectMapper.writeValueAsString(java.util.Map.of(
                    "cardId", cardId,
                    "template", template,
                    "payloadSize", payload.length,
                    "status", "GENERATED",
                    "timestamp", java.time.OffsetDateTime.now().toString()
            ));
            kafkaTemplate.send("vito.print.audit", String.valueOf(cardId), auditEvent);

        } catch (Exception e) {
            log.error("Failed to process print job: {}", e.getMessage(), e);
        }
    }
}
