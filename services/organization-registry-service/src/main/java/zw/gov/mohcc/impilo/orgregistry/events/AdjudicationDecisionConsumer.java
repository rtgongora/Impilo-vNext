package zw.gov.mohcc.impilo.orgregistry.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.orgregistry.core.ClaimSubmissionService;

import java.util.UUID;

/**
 * DECISION-FEEDBACK ARC consumer.
 *
 * <p>Consumes the workforce-governance governance outbox stream and resolves the
 * Channel-C claim linked to a recorded IATG adjudication decision. This is the
 * decoupled seam chosen over a WGV→org-registry REST push: workforce-governance
 * <em>already</em> emits {@code impilo.governance.adjudication.decision.recorded}
 * to Kafka (append-only, direction-correct), so consuming it here closes the loop
 * with zero changes to WGV and without WGV having to know about org claims.</p>
 *
 * <p>Gated by {@code impilo.orgregistry.kafka-events-enabled=true} — the same gate
 * as the org-registry outbox publisher. In the default/test profile (false, and
 * KafkaAutoConfiguration excluded) this bean is not created, so resolution logic
 * is exercised directly against {@link ClaimSubmissionService} in unit tests.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.orgregistry.kafka-events-enabled", havingValue = "true")
public class AdjudicationDecisionConsumer {

    private static final Logger log = LoggerFactory.getLogger(AdjudicationDecisionConsumer.class);

    /** Event type emitted by workforce-governance AdjudicationDecisionService. */
    static final String EVENT_DECISION_RECORDED = "impilo.governance.adjudication.decision.recorded";

    private final ClaimSubmissionService claimSubmissionService;
    private final ObjectMapper objectMapper;

    public AdjudicationDecisionConsumer(ClaimSubmissionService claimSubmissionService,
                                        ObjectMapper objectMapper) {
        this.claimSubmissionService = claimSubmissionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${impilo.governance.outbox-topic:impilo.governance.events}",
            groupId = "organization-registry-service")
    public void onGovernanceEvent(String message, Acknowledgment acknowledgment) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = text(envelope, "eventType");
            if (!EVENT_DECISION_RECORDED.equals(eventType)) {
                return; // not our event — ignore quietly
            }
            JsonNode payload = envelope.has("payload") ? envelope.get("payload") : envelope;

            UUID tenantId = uuid(text(envelope, "tenantId"));
            if (tenantId == null) {
                tenantId = uuid(text(payload, "tenantId"));
            }
            UUID workflowInstanceId = uuid(text(payload, "workflowInstanceId"));
            String subjectRef = text(payload, "subjectRef");
            String decision = text(payload, "decision");
            String decisionId = text(payload, "decisionId");
            String correlationId = text(envelope, "correlationId");

            if (tenantId == null || decision == null || decisionId == null
                    || (workflowInstanceId == null && subjectRef == null)) {
                log.warn("Adjudication decision event missing routing fields "
                        + "[tenantId={}, decisionId={}, decision={}, workflowInstanceId={}, subjectRef={}] — dropping",
                        tenantId, decisionId, decision, workflowInstanceId, subjectRef);
                return;
            }

            claimSubmissionService.resolveFromDecision(
                    tenantId, workflowInstanceId, subjectRef, decision, decisionId, correlationId);
        } catch (Exception e) {
            // Honest: log and ack — a malformed/unroutable event is dropped, never crashes the loop.
            log.error("Failed to process governance adjudication decision event: {}", e.getMessage(), e);
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && !node.get(field).asText().isBlank()
                ? node.get(field).asText() : null;
    }

    private static UUID uuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
