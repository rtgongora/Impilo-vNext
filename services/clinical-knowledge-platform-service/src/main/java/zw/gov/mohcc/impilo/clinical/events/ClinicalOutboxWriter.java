package zw.gov.mohcc.impilo.clinical.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalEventOutboxEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ClinicalEventOutboxRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional writes to {@code clinical.event_outbox}. Kafka relay publishes asynchronously.
 */
@Service
public class ClinicalOutboxWriter {

    private final ClinicalEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ClinicalOutboxWriter(ClinicalEventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String eventType, String tenantId, String subjectType, String subjectId, Map<String, Object> payload) {
        ClinicalEventOutboxEntity e = new ClinicalEventOutboxEntity();
        e.setEventType(eventType);
        e.setTenantId(tenantId != null && !tenantId.isBlank() ? tenantId : "default");
        e.setSubjectType(subjectType);
        e.setSubjectId(subjectId);
        e.setCorrelationId(UUID.randomUUID());
        e.setPayload(objectMapper.valueToTree(payload == null ? Map.of() : payload));
        e.setMeta(objectMapper.createObjectNode());
        e.setOccurredAt(Instant.now());
        outboxRepository.save(e);
    }

    @Transactional
    public void enqueueRecommendationTrace(RecommendationTraceEntity trace) {
        List<String> ruleCodes = new ArrayList<>();
        JsonNode rules = trace.getRulesTriggeredJson();
        if (rules != null && rules.path("alerts").isArray()) {
            for (JsonNode n : rules.path("alerts")) {
                if (n.has("code")) {
                    ruleCodes.add(n.get("code").asText());
                }
            }
        }
        String tenantId = trace.getInputContextJson() != null
                ? trace.getInputContextJson().path("tenant_id").asText("default")
                : "default";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trace_id", trace.getId().toString());
        payload.put("support_mode", trace.getSupportMode());
        payload.put("request_type", trace.getRequestType());
        payload.put("knowledge_version", trace.getKnowledgeVersion());
        payload.put("source_version", trace.getSourceVersion());
        payload.put("rule_codes", ruleCodes);
        enqueue("GUIDANCE_RECOMMENDATION_GENERATED", tenantId, "RecommendationTrace", trace.getId().toString(), payload);
    }
}
