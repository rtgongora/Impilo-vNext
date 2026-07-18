package zw.gov.mohcc.impilo.cardprint.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.cardprint.generator.CardPayloadGenerator;
import zw.gov.mohcc.impilo.cardprint.service.SecureElementKeyService;
import zw.gov.mohcc.impilo.cardprint.service.VitoCardClient;

import java.util.UUID;

@Component
public class PrintJobListener {

    private static final Logger log = LoggerFactory.getLogger(PrintJobListener.class);

    private final ObjectMapper objectMapper;
    private final CardPayloadGenerator generator;
    private final SecureElementKeyService secureElementKeyService;
    private final VitoCardClient vitoCardClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PrintJobListener(ObjectMapper objectMapper, CardPayloadGenerator generator,
                             SecureElementKeyService secureElementKeyService,
                             VitoCardClient vitoCardClient,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.generator = generator;
        this.secureElementKeyService = secureElementKeyService;
        this.vitoCardClient = vitoCardClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "vito.print", groupId = "card-print-agent")
    public void onPrintJob(String message) {
        long cardId = -1;
        try {
            JsonNode node = objectMapper.readTree(message);
            // Defensive: a mis-configured producer (JsonSerializer over a pre-serialized
            // JSON string) puts a quoted string literal on the wire — unwrap it so cardId
            // and the other fields don't silently read as missing/zero.
            if (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            cardId = node.path("cardId").asLong();
            String template = node.path("template").asText("STANDARD");
            String did = node.path("did").asText();
            UUID tenantId = parseTenantId(node);

            log.info("Processing print job for card {} with template {}", cardId, template);

            byte[] payload = generator.generate(cardId, template, did);

            log.info("Generated print payload for card {} ({} bytes)", cardId, payload.length);

            // G032: personalise the card's secure element and provision its public key into VITO.
            // VITO fails activation closed without a real key, so this callback is mandatory for the
            // card to become usable. It also drives the REQUESTED -> PRINTED transition.
            String publicKeyPem = secureElementKeyService.generatePublicKeyPem(cardId);
            vitoCardClient.confirmPrinted(tenantId, cardId, publicKeyPem);

            // Emit audit event
            String auditEvent = objectMapper.writeValueAsString(java.util.Map.of(
                    "cardId", cardId,
                    "template", template,
                    "payloadSize", payload.length,
                    "publicKeyProvisioned", true,
                    "status", "PRINTED",
                    "timestamp", java.time.OffsetDateTime.now().toString()
            ));
            kafkaTemplate.send("vito.print.audit", String.valueOf(cardId), auditEvent);

        } catch (Exception e) {
            log.error("Failed to process print job for card {}: {}", cardId, e.getMessage(), e);
            emitFailureAudit(cardId, e);
        }
    }

    private UUID parseTenantId(JsonNode node) {
        String raw = node.path("tenantId").asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Print job carried an unparseable tenantId '{}'; proceeding without tenant scope", raw);
            return null;
        }
    }

    private void emitFailureAudit(long cardId, Exception cause) {
        try {
            String auditEvent = objectMapper.writeValueAsString(java.util.Map.of(
                    "cardId", cardId,
                    "publicKeyProvisioned", false,
                    "status", "FAILED",
                    "error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(),
                    "timestamp", java.time.OffsetDateTime.now().toString()
            ));
            kafkaTemplate.send("vito.print.audit", String.valueOf(cardId), auditEvent);
        } catch (Exception nested) {
            log.error("Failed to emit print-failure audit for card {}: {}", cardId, nested.getMessage());
        }
    }
}
