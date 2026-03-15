package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkEntity;
import zw.gov.mohcc.impilo.vito.events.persistence.ControlChannelWatermarkRepository;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VITO consumer for federation pod revocation events.
 *
 * When a pod is revoked, VITO must:
 * 1. Add the pod to a local deny list to reject any merge/linkage
 *    operations referencing data originating from the revoked pod.
 * 2. Log the revocation for audit trail.
 *
 * This is the second consumer (alongside TSHEPO) that reacts to pod
 * revocation, proving the high-priority revocation channel reaches
 * multiple service boundaries.
 */
@Component
public class FederationPodRevocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(FederationPodRevocationConsumer.class);

    private final Set<String> localDenyList = ConcurrentHashMap.newKeySet();
    private final ControlChannelWatermarkRepository watermarkRepository;
    private final ObjectMapper objectMapper;

    public FederationPodRevocationConsumer(ControlChannelWatermarkRepository watermarkRepository,
                                            ObjectMapper objectMapper) {
        this.watermarkRepository = watermarkRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "impilo.federation.pod.revoked.v1",
            groupId = "vito-federation-revocation",
            containerFactory = "controlChannelListenerFactory"
    )
    @Transactional
    public void consumePodRevocationEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String podId = getTextField(event, "pod_id");
            String revokedBy = getTextField(event, "revoked_by");
            String reason = getTextField(event, "reason");

            if (podId == null) {
                log.warn("VITO: Pod revocation event missing pod_id, skipping");
                return;
            }

            // Idempotency: use pod_id as event_id for dedup
            String eventId = "pod-revoked-" + podId;
            if (watermarkRepository.existsByEventId(eventId)) {
                log.debug("VITO: Pod revocation for {} already processed, skipping", podId);
                return;
            }

            // Add to local deny list
            localDenyList.add(podId);

            log.info("VITO: Pod revoked and added to deny list: pod_id={}, revoked_by={}, reason={}",
                    podId, revokedBy, reason);

            // Record watermark
            ControlChannelWatermarkEntity watermark = new ControlChannelWatermarkEntity();
            watermark.setEventId(eventId);
            watermark.setEventType("POD_REVOKED");
            watermark.setSubjectId(podId);
            watermark.setProcessedAt(Instant.now());
            watermark.setCorrelationId("federation-revocation");
            watermarkRepository.save(watermark);

        } catch (JsonProcessingException e) {
            log.error("VITO: Failed to parse pod revocation event: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if a pod is on the local deny list.
     */
    public boolean isDenied(String podId) {
        return localDenyList.contains(podId);
    }

    /**
     * Remove a pod from the deny list (reinstatement).
     */
    public void clearDenial(String podId) {
        localDenyList.remove(podId);
    }

    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }
}
