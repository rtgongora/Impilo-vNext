package zw.gov.mohcc.impilo.tshepo.federation.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for federation pod revocation events on the HIGH_PRI control channel.
 *
 * When a pod is revoked at the spine, this consumer:
 * 1. Updates the local revocation cache for immediate enforcement
 * 2. Logs the revocation for audit
 *
 * This ensures cross-instance consistency: if pod A is revoked via
 * instance 1, instance 2 learns about it within the Kafka propagation
 * latency (target: ≤ 5 seconds).
 *
 * Topic: impilo.federation.pod.revoked.v1 (HIGH_PRI partition)
 */
@Component
public class FederationPodRevocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(FederationPodRevocationConsumer.class);

    private final PodRevocationCacheService revocationCache;
    private final ObjectMapper objectMapper;

    public FederationPodRevocationConsumer(PodRevocationCacheService revocationCache,
                                            ObjectMapper objectMapper) {
        this.revocationCache = revocationCache;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "impilo.federation.pod.revoked.v1",
            groupId = "tshepo-federation-revocation"
    )
    public void consumePodRevocationEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String podId = getTextField(event, "pod_id");
            String revokedBy = getTextField(event, "revoked_by");
            String reason = getTextField(event, "reason");
            String priority = getTextField(event, "priority");

            if (podId == null) {
                log.warn("Pod revocation event missing pod_id, skipping");
                return;
            }

            revocationCache.markRevoked(podId);
            log.info("Pod revocation received via Kafka: pod_id={}, revoked_by={}, reason={}, priority={}",
                    podId, revokedBy, reason, priority);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse pod revocation event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "impilo.federation.pod.reinstated.v1",
            groupId = "tshepo-federation-revocation"
    )
    public void consumePodReinstatedEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String podId = getTextField(event, "pod_id");

            if (podId == null) {
                log.warn("Pod reinstatement event missing pod_id, skipping");
                return;
            }

            revocationCache.clearRevocation(podId);
            log.info("Pod reinstatement received via Kafka: pod_id={}", podId);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse pod reinstatement event: {}", e.getMessage(), e);
        }
    }

    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }
}
