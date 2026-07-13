package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.core.TelemedicineOrchestrationService;

/**
 * Consumes {@code impilo.rtc.recording.available.v1} from rtc-gateway and writes the recording
 * reference back onto the owning teleconsult referral (G20). PCT provisions its rtc sessions with
 * {@code owningService=PCT} + {@code owningRef=referralId}, which rtc-gateway echoes on the recording
 * event — so a PCT-owned recording is matchable to its referral. Foreign recordings
 * ({@code owningService != PCT}) are ignored; the listener is poison-message-safe.
 */
@Component
public class PctRecordingConsumer {

    private static final Logger log = LoggerFactory.getLogger(PctRecordingConsumer.class);

    private final TelemedicineOrchestrationService orchestration;
    private final ObjectMapper objectMapper;

    public PctRecordingConsumer(TelemedicineOrchestrationService orchestration, ObjectMapper objectMapper) {
        this.orchestration = orchestration;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${pct.rtc.recording-available-topic:impilo.rtc.recording.available.v1}",
            groupId = "${spring.kafka.consumer.group-id:pct-service}")
    public void onRecordingAvailable(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.has("payload") && !root.get("payload").isNull() ? root.get("payload") : root;

            String owningService = payload.path("owningService").asText(root.path("owningService").asText(""));
            if (!"PCT".equalsIgnoreCase(owningService)) {
                return; // not a PCT-owned recording (e.g. live-service events)
            }
            String owningRef = payload.path("owningRef").asText(root.path("owningRef").asText(null));
            if (owningRef == null || owningRef.isBlank()) {
                log.warn("PCT recording event has no owningRef — cannot attribute to a referral");
                return;
            }
            // Prefer the egress id as the durable recording handle; fall back to the session id.
            String recordingRef = firstNonBlank(
                    payload.path("egressId").asText(null),
                    payload.path("sessionId").asText(null));
            String storageKey = payload.path("storageKey").asText(null);

            orchestration.attachRecording(owningRef, recordingRef, storageKey);
        } catch (Exception e) {
            log.warn("Failed to process rtc recording-available event: {}", e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
