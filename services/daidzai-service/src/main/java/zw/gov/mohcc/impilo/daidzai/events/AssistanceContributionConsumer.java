package zw.gov.mohcc.impilo.daidzai.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.daidzai.core.AssistanceService;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projects mushe contribution events onto assistance cases (raised total, donor count,
 * FULFILLED transition). Mushe is the money SoR — this consumer only mirrors what the
 * ledger already recorded, idempotently on the contribution id, so replays and dual-emit
 * duplicates can never double-count. Non-campaign contribution events are ignored.
 *
 * <p>Listens on mushe's legacy default topic (BILL_CONTRIBUTION aggregates route to
 * {@code mushe.events}) and the v1.1-prefixed variant.</p>
 */
@Component
@ConditionalOnProperty(name = "daidzai.assistance.projection.enabled",
        havingValue = "true", matchIfMissing = true)
public class AssistanceContributionConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssistanceContributionConsumer.class);

    private final AssistanceService assistanceService;
    private final ObjectMapper objectMapper;

    public AssistanceContributionConsumer(AssistanceService assistanceService, ObjectMapper objectMapper) {
        this.assistanceService = assistanceService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"mushe.events", "impilo.mushe.events"},
            groupId = "daidzai-assistance-projection")
    public void onMusheEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = text(event, "eventType", "event_type");
            if (eventType == null || !eventType.startsWith("wallet.bill_contribution.")) {
                return;
            }
            JsonNode payload = event.hasNonNull("payload") ? event.get("payload") : event;
            if (payload.isTextual()) {
                payload = objectMapper.readTree(payload.asText());
            }
            if (!"wallet.bill_contribution.recorded".equals(eventType)) {
                // released/refunded events don't change the raised projection (refund display
                // is served from mushe by reference); recorded is the projection trigger.
                return;
            }
            String requestId = text(payload, "requestId", "request_id");
            String contributionId = text(payload, "contributionId", "contribution_id");
            String amount = text(payload, "amount");
            if (requestId == null || contributionId == null || amount == null) {
                log.warn("bill_contribution.recorded event missing fields; skipping: {}", payload);
                return;
            }
            boolean anonymous = payload.path("isAnonymousHint").asBoolean(
                    payload.path("is_anonymous").asBoolean(false));
            assistanceService.applyContribution(
                    UUID.fromString(requestId),
                    UUID.fromString(contributionId),
                    new BigDecimal(amount),
                    anonymous,
                    text(payload, "contributorName", "contributor_name"),
                    text(payload, "message"));
        } catch (Exception e) {
            // Projection is additive and idempotent — log and move on; the money truth in
            // mushe is unaffected and the row can be re-projected by replay.
            log.warn("Failed to project mushe contribution event: {}", e.toString());
        }
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                String v = node.get(key).asText();
                if (!v.isBlank()) return v;
            }
        }
        return null;
    }
}
