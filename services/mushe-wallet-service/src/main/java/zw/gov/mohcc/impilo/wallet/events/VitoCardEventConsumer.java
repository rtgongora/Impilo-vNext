package zw.gov.mohcc.impilo.wallet.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.wallet.card.CardService;

/**
 * Unified SMART card (Phase 2): mushe-wallet reacts to VITO CardCredential lifecycle events
 * ({@code impilo.vito.cards}). VITO owns the card as an identity credential; when the physical card is
 * revoked or suspended, the money facet must be frozen too. Matched to the money card by the canonical
 * VITO cardNumber (see {@code CardEntity.vitoCardNumber}); a no-op when no money card is linked.
 * Poison-message-safe. See {@code docs/architecture/UNIFIED_SMART_CARD_ARCHITECTURE.md}.
 */
@Component
public class VitoCardEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(VitoCardEventConsumer.class);

    private final CardService cardService;
    private final ObjectMapper objectMapper;

    public VitoCardEventConsumer(CardService cardService, ObjectMapper objectMapper) {
        this.cardService = cardService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"vito.cards", "impilo.vito.cards"},
            groupId = "${spring.kafka.consumer.group-id:mushe-wallet-service}")
    public void onVitoCardEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            JsonNode payload = node.has("payload") && !node.get("payload").isNull() ? node.get("payload") : node;

            String eventType = firstNonBlank(
                    node.path("event_type").asText(null),
                    node.path("eventType").asText(null)).toUpperCase();

            String vitoCardNumber = firstNonBlank(
                    payload.path("cardNumber").asText(null),
                    node.path("cardNumber").asText(null));
            if (vitoCardNumber == null || vitoCardNumber.isBlank()) {
                return; // not attributable to a money card
            }

            // Resilient to raw (CARD_REVOKED) or v1.1 (impilo.vito.cards.revoked.v1) type strings.
            if (eventType.contains("REVOK") || eventType.contains("SUSPEND")) {
                cardService.freezeForVitoCard(vitoCardNumber,
                        "VITO SMART card " + (eventType.contains("REVOK") ? "revoked" : "suspended"));
                return;
            }

            // Non-revocation events: cache the card's device/SE public key so mushe can verify
            // offline transactions (Phase 3). Only takes effect for an already-linked money card.
            String publicKey = firstNonBlank(
                    payload.path("publicKey").asText(null),
                    node.path("publicKey").asText(null));
            if (!publicKey.isBlank()) {
                cardService.cacheVitoCardKey(vitoCardNumber, publicKey);
            }
        } catch (Exception e) {
            log.warn("Failed to process VITO card event: {}", e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null ? b : "";
    }
}
