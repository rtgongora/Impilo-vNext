package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineCommsWorkflowService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineWorkflowHistoryStore;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnBean(KafkaTemplate.class)
public class TelemedicineLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineLifecycleConsumer.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final TelemedicineCommsWorkflowService workflowService;
    private final TelemedicineWorkflowHistoryStore workflowHistoryStore;

    public TelemedicineLifecycleConsumer(
            ObjectMapper objectMapper,
            TelemedicineCommsWorkflowService workflowService,
            TelemedicineWorkflowHistoryStore workflowHistoryStore) {
        this.objectMapper = objectMapper;
        this.workflowService = workflowService;
        this.workflowHistoryStore = workflowHistoryStore;
    }

    @KafkaListener(topics = {"clinical.teleconsult.lifecycle", "pct.events"}, groupId = "experience-bff")
    public void onLifecycleEvent(String rawPayload) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawPayload, MAP_TYPE);
            String eventType = string(parsed.get("eventType"));
            Map<String, Object> payload = map(parsed.get("payload"));
            if (payload.isEmpty()) {
                payload = parsed;
            }
            if (eventType == null || eventType.isBlank()) {
                eventType = inferEventType(payload);
            }
            if (eventType == null || !eventType.startsWith("telemedicine.session.")) {
                return;
            }
            workflowService.processLifecycleEvent(eventType, payload);
        } catch (Exception ex) {
            log.warn("Failed to process telemedicine lifecycle event: {}", ex.getMessage());
            workflowHistoryStore.onEventFailed("telemedicine.session.unmapped", Map.of(), ex.getMessage());
            workflowHistoryStore.recordWebhookFailure("telemedicine.session.unmapped", Map.of(), ex.getMessage());
        }
    }

    private String inferEventType(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String status = string(payload.get("status"));
        if ("SCHEDULED".equalsIgnoreCase(status)) return "telemedicine.session.scheduled";
        if ("IN_PROGRESS".equalsIgnoreCase(status)) return "telemedicine.session.started";
        if ("COMPLETED".equalsIgnoreCase(status)) return "telemedicine.session.completed";
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object payload) {
        if (payload instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
