package zw.gov.mohcc.impilo.fhirgateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes consent-domain events from tshepo-consent-service (transactional outbox → Kafka)
 * and maintains a local revocation cache for fail-fast DENY decisions at the FHIR gateway.
 */
@Component
public class ConsentRevocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConsentRevocationConsumer.class);

    public static final String TOPIC = "platform.consent.events";
    public static final String GROUP_ID = "fhir-gateway";

    private final ObjectMapper objectMapper;
    private final ConsentCacheService consentCacheService;

    public ConsentRevocationConsumer(ObjectMapper objectMapper, ConsentCacheService consentCacheService) {
        this.objectMapper = objectMapper;
        this.consentCacheService = consentCacheService;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onConsentEvent(String message) {
        try {
            if (message == null || message.isBlank()) {
                log.warn("Ignoring empty consent event message");
                return;
            }
            JsonNode root = parseRoot(message);
            if (root == null || root.isNull()) {
                log.warn("Ignoring null JSON consent event");
                return;
            }

            JsonNode envelope = root.isObject() ? root : null;
            JsonNode payload = payloadNode(envelope);

            String correlationId = firstText(envelope, "correlation_id", "correlationId", "X-Correlation-ID");
            if (correlationId == null && payload != null && payload.isObject()) {
                correlationId = firstText(payload, "correlation_id", "correlationId");
            }

            if (isRevocation(envelope, payload)) {
                String tenantId = extractTenantId(envelope, payload);
                String cpid = extractSubjectCpid(envelope, payload);
                if (tenantId == null || cpid == null) {
                    log.warn("Consent revocation event missing tenant_id or subject CPID; correlation_id={}",
                            correlationId != null ? correlationId : "n/a");
                    return;
                }
                consentCacheService.recordRevocation(tenantId, cpid);
                log.info("Cached consent revocation for tenant_id={} subject_cpid={} correlation_id={}",
                        tenantId, cpid, correlationId != null ? correlationId : "n/a");
                return;
            }

            if (isRegrantOrActiveConsent(payload)) {
                String tenantId = extractTenantId(envelope, payload);
                String cpid = extractSubjectCpid(envelope, payload);
                if (tenantId != null && cpid != null) {
                    consentCacheService.clearCache(tenantId, cpid);
                    log.info("Cleared consent revocation cache after re-grant tenant_id={} subject_cpid={} correlation_id={}",
                            tenantId, cpid, correlationId != null ? correlationId : "n/a");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process consent Kafka message (poison-safe); err={}", e.getMessage(), e);
        }
    }

    private JsonNode parseRoot(String message) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(message);
        if (root.isTextual()) {
            try {
                return objectMapper.readTree(root.asText());
            } catch (JsonProcessingException ignored) {
                return root;
            }
        }
        return root;
    }

    private static JsonNode payloadNode(JsonNode envelope) {
        if (envelope == null || !envelope.isObject()) {
            return null;
        }
        JsonNode p = envelope.get("payload");
        return p != null && p.isObject() ? p : envelope;
    }

    private static boolean isRevocation(JsonNode envelope, JsonNode payload) {
        String eventType = combinedEventType(envelope, payload);
        if (eventType != null) {
            String lower = eventType.toLowerCase();
            if (lower.contains("revoked")) {
                return true;
            }
        }
        JsonNode opHolder = payload != null && payload.isObject() ? payload : envelope;
        if (opHolder != null && opHolder.isObject()) {
            JsonNode op = opHolder.get("op");
            if (op != null && !op.isNull() && "REVOKE".equalsIgnoreCase(op.asText())) {
                return true;
            }
        }
        String status = textStatus(payload != null && payload.isObject() ? payload : envelope);
        return status != null && "REVOKED".equalsIgnoreCase(status);
    }

    private static boolean isRegrantOrActiveConsent(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        if (!payload.has("consentId")) {
            return false;
        }
        String status = textStatus(payload);
        if (status == null || status.isBlank()) {
            return false;
        }
        return !"REVOKED".equalsIgnoreCase(status);
    }

    private static String combinedEventType(JsonNode envelope, JsonNode payload) {
        String t = firstText(envelope, "event_type", "eventType");
        if (t != null) {
            return t;
        }
        return firstText(payload, "event_type", "eventType");
    }

    private static String textStatus(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode s = node.get("status");
        return (s != null && !s.isNull()) ? s.asText() : null;
    }

    private static String extractTenantId(JsonNode envelope, JsonNode payload) {
        String v = firstText(envelope, "tenant_id", "tenantId");
        if (v != null) {
            return v;
        }
        return firstText(payload, "tenant_id", "tenantId");
    }

    private static String extractSubjectCpid(JsonNode envelope, JsonNode payload) {
        String v = firstText(envelope, "subject_cpid", "subjectCpid");
        if (v != null) {
            return v;
        }
        v = firstText(payload, "subject_cpid", "subjectCpid", "patientRef", "patient_ref");
        return v;
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String name : fieldNames) {
            JsonNode f = node.get(name);
            if (f != null && !f.isNull() && f.isTextual()) {
                String t = f.asText();
                if (t != null && !t.isBlank()) {
                    return t;
                }
            }
        }
        return null;
    }
}
