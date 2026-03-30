package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DecisionEvidenceDto;

/**
 * Publishes {@link DecisionEvidenceDto} records to the {@code trust.decision_evidence} topic.
 *
 * <p>Uses the trust-channel-grade {@link KafkaTemplate} (acks=all, idempotent) so every
 * authorization decision is durably recorded on the trust evidence channel.</p>
 */
@Service
public class DecisionEvidencePublisher {

    private static final Logger log = LoggerFactory.getLogger(DecisionEvidencePublisher.class);

    public static final String TOPIC = "trust.decision_evidence";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DecisionEvidencePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish a decision evidence record.
     *
     * @param evidence the populated evidence DTO; must not be null
     */
    public void publish(DecisionEvidenceDto evidence) {
        String key = evidence.actorId() != null ? evidence.actorId() : "unknown";

        kafkaTemplate.send(TOPIC, key, evidence).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish DecisionEvidence to {}: actor={} decision={} error={}",
                        TOPIC, evidence.actorId(), evidence.decision(), ex.getMessage(), ex);
            } else {
                log.debug("Published DecisionEvidence to {} [partition={}, offset={}] actor={} decision={}",
                        TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        evidence.actorId(),
                        evidence.decision());
            }
        });
    }
}
