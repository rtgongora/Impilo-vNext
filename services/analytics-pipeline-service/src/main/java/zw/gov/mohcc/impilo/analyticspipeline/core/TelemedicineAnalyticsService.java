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
        body.put("eventsAccepted", repository.count());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (TelemedicineEventRepository.EventTypeCount row : repository.countGroupedByEventTypeAll()) {
            byType.put(row.getEventType(), row.getEventCount());
        }
        body.put("eventsByType", byType);
        // TM-B17 (G3/G5): §24 aggregates derived from the event stream itself.
        body.put("outcomeDistribution", outcomeDistribution());
        body.put("avgTimeToAcceptanceMinutes", avgTimeToAcceptanceMinutes());
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

        // TM-B17: honour the envelope tenant (the hardcoded default was invisible-making — live
        // events carry the platform tenant, not 0000-…-0001).
        UUID tenantId = DEFAULT_TENANT;
        Object envelopeTenant = event.get("tenantId");
        if (envelopeTenant != null) {
            try {
                tenantId = UUID.fromString(String.valueOf(envelopeTenant));
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        repository.save(new TelemedicineEventEntity(
                eventId,
                tenantId,
                eventType,
                facilityId,
                payloadJson));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("status", "ACCEPTED");
        body.put("receivedAt", Instant.now().toString());
        return body;
    }

    /** Outcome distribution from completed-event payloads (completion notes → analytics, G5). */
    private Map<String, Long> outcomeDistribution() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (TelemedicineEventEntity e : repository.findByEventTypeStartingWith("telemedicine.session.completed")) {
            String outcome = extract(e.getPayloadJson(), "patientOutcome", "outcome");
            String key = outcome == null || outcome.isBlank() ? "UNSPECIFIED" : outcome.trim();
            dist.merge(key, 1L, Long::sum);
        }
        return dist;
    }

    /** Mean created→accepted interval derived purely from event occurredAt stamps (G3). */
    private Double avgTimeToAcceptanceMinutes() {
        Map<String, Instant> created = new LinkedHashMap<>();
        Map<String, Instant> accepted = new LinkedHashMap<>();
        collectOccurrences("telemedicine.session.referral_created", created);
        collectOccurrences("telemedicine.session.created", created);
        collectOccurrences("telemedicine.session.routed", accepted);
        long totalSeconds = 0;
        int pairs = 0;
        for (Map.Entry<String, Instant> entry : accepted.entrySet()) {
            Instant start = created.get(entry.getKey());
            if (start != null && entry.getValue().isAfter(start)) {
                totalSeconds += entry.getValue().getEpochSecond() - start.getEpochSecond();
                pairs++;
            }
        }
        return pairs == 0 ? null : Math.round(totalSeconds / 60.0 / pairs * 10.0) / 10.0;
    }

    private void collectOccurrences(String typePrefix, Map<String, Instant> sink) {
        for (TelemedicineEventEntity e : repository.findByEventTypeStartingWith(typePrefix)) {
            String aggregateId = extract(e.getPayloadJson(), "aggregateId");
            String occurredAt = extract(e.getPayloadJson(), "occurredAt");
            if (aggregateId == null || occurredAt == null) {
                continue;
            }
            try {
                sink.putIfAbsent(aggregateId, Instant.parse(occurredAt));
            } catch (Exception ignored) {
                // unparseable stamp — skip
            }
        }
    }

    /** Pull a string field from the stored envelope (top level or nested payload). */
    private String extract(String payloadJson, String... keys) {
        try {
            Map<String, Object> root = objectMapper.readValue(payloadJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            for (String key : keys) {
                Object v = root.get(key);
                if (v == null && root.get("payload") instanceof Map<?, ?> inner) {
                    v = ((Map<?, ?>) inner).get(key);
                }
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v);
                }
            }
        } catch (Exception ignored) {
            // corrupt/legacy payload — skip
        }
        return null;
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
