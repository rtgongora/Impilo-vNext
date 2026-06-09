package zw.gov.mohcc.impilo.analyticspipeline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.analyticspipeline.persistence.TelemedicineEventEntity;
import zw.gov.mohcc.impilo.analyticspipeline.persistence.TelemedicineEventRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TelemedicineAnalyticsService {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TelemedicineEventRepository repository;
    private final ObjectMapper objectMapper;

    public TelemedicineAnalyticsService(
            TelemedicineEventRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> slaAggregates(String facilityId, String from, String to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", facilityId);
        body.put("from", from);
        body.put("to", to);
        body.put("eventsAccepted", repository.countByTenantId(DEFAULT_TENANT));
        Map<String, Long> byType = new LinkedHashMap<>();
        for (TelemedicineEventRepository.EventTypeCount row : repository.countGroupedByEventType(DEFAULT_TENANT)) {
            byType.put(row.getEventType(), row.getEventCount());
        }
        body.put("eventsByType", byType);
        body.put("generatedAt", Instant.now().toString());
        body.put("storage", "postgres");
        return body;
    }

    @Transactional
    public Map<String, Object> ingestEvent(Map<String, Object> event) {
        UUID eventId = UUID.randomUUID();
        String eventType = String.valueOf(event.getOrDefault("eventType", "UNKNOWN"));
        String facilityId = event.get("facilityId") != null ? event.get("facilityId").toString() : null;
        String payloadJson = toJson(event);

        repository.save(new TelemedicineEventEntity(
                eventId,
                DEFAULT_TENANT,
                eventType,
                facilityId,
                payloadJson));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("status", "ACCEPTED");
        body.put("receivedAt", Instant.now().toString());
        return body;
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
