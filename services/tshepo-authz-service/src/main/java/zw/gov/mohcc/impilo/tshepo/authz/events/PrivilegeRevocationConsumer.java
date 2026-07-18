package zw.gov.mohcc.impilo.tshepo.authz.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.authz.service.PolicyCacheService;
import zw.gov.mohcc.impilo.tshepo.authz.service.ProviderPrivilegeRevocationStore;

/**
 * Consumes VARAPI provider / credential domain events and reacts to privilege
 * lifecycle changes by invalidating policy rule caches and recording revocation
 * markers for immediate ext_authz denial.
 */
@Component
public class PrivilegeRevocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PrivilegeRevocationConsumer.class);

    private final ObjectMapper objectMapper;
    private final PolicyCacheService policyCacheService;
    private final ProviderPrivilegeRevocationStore revocationStore;

    public PrivilegeRevocationConsumer(ObjectMapper objectMapper,
                                       PolicyCacheService policyCacheService,
                                       ProviderPrivilegeRevocationStore revocationStore) {
        this.objectMapper = objectMapper;
        this.policyCacheService = policyCacheService;
        this.revocationStore = revocationStore;
    }

    @KafkaListener(
            topics = {"varapi.provider", "varapi.credential", "impilo.varapi.provider"},
            groupId = "tshepo-authz")
    public void onVarapiEvent(ConsumerRecord<String, String> record) {
        try {
            if (record.value() == null || record.value().isBlank()) {
                log.debug("Skipping empty VARAPI event from topic={}", record.topic());
                return;
            }
            JsonNode root = objectMapper.readTree(record.value());

            String eventType = textOrEmpty(root, "event_type");
            JsonNode payloadNode = root.path("payload");
            boolean v11Envelope = root.hasNonNull("event_type") && !payloadNode.isMissingNode() && payloadNode.isObject();

            if (!v11Envelope) {
                payloadNode = root;
                eventType = "";
            }

            String correlationId = firstNonBlank(
                    textOrNull(root, "correlation_id"),
                    textOrNull(payloadNode, "correlation_id"));

            String providerId = firstNonBlank(
                    textOrNull(payloadNode, "providerPublicId"),
                    textOrNull(payloadNode, "provider_id"),
                    textOrNull(payloadNode, "providerId"),
                    nullable(record.key()));

            String tenantId = firstNonBlank(
                    textOrNull(root, "tenant_id"),
                    textOrNull(payloadNode, "tenant_id"),
                    textOrNull(payloadNode, "tenantId"));

            if (providerId == null || providerId.isBlank()) {
                log.debug("VARAPI event on {} has no resolvable provider id; correlation={}",
                        record.topic(), correlationId);
                return;
            }

            String eventLower = eventType.toLowerCase();
            String newStatus = textOrNull(payloadNode, "newStatus");
            String status = textOrNull(payloadNode, "status");
            String previousStatus = textOrNull(payloadNode, "previousStatus");
            // PJ15 fix: licence FSM events (varapi.provider.lapsed / .suspended) carry
            // newLifecycle/previousLifecycle, NOT newStatus/status — so a lapsed licence
            // was silently NOT revoking privileges. Include the lifecycle fields.
            String newLifecycle = textOrNull(payloadNode, "newLifecycle");
            String previousLifecycle = textOrNull(payloadNode, "previousLifecycle");

            boolean revocation = isRevocation(eventLower, newStatus, status, newLifecycle);
            boolean reactivation = isReactivation(newStatus, status, previousStatus,
                    newLifecycle, previousLifecycle);

            if (revocation) {
                revocationStore.markRevoked(providerId);
                policyCacheService.evictForProvider(tenantId, providerId);
                log.warn("VARAPI provider privilege revocation applied: topic={}, provider_id={}, tenant_id={}, correlation_id={}",
                        record.topic(), providerId, tenantId == null ? "-" : tenantId, correlationId == null ? "-" : correlationId);
                return;
            }

            if (reactivation) {
                revocationStore.clearRevoked(providerId);
                policyCacheService.evictForProvider(tenantId, providerId);
                log.info("VARAPI provider reactivation — cache evicted: topic={}, provider_id={}, tenant_id={}, correlation_id={}",
                        record.topic(), providerId, tenantId == null ? "-" : tenantId, correlationId == null ? "-" : correlationId);
            }

        } catch (JsonProcessingException e) {
            log.error("Malformed VARAPI Kafka event on topic {}: {}", record.topic(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing VARAPI event on topic {}: {}", record.topic(), e.getMessage(), e);
        }
    }

    private static boolean isRevocation(String eventLower, String newStatus, String status, String newLifecycle) {
        // Licence FSM terminal transitions (PJ15): a lapsed/suspended/retired/expired
        // licence revokes practice privileges.
        if (eventLower.contains("revoked") || eventLower.contains("suspended")
                || eventLower.contains("deactivated") || eventLower.contains("lapsed")
                || eventLower.contains("retired") || eventLower.contains("expired")) {
            return true;
        }
        // A non-active target lifecycle revokes regardless of event-type wording.
        if (newLifecycle != null && !isActiveLifecycle(newLifecycle)) {
            return true;
        }
        if (eventLower.contains("status_changed")) {
            if (newStatus != null) {
                return !isActive(newStatus);
            }
            if (status != null) {
                return !isActive(status);
            }
            return false;
        }
        if (newStatus != null && !isActive(newStatus)) {
            return true;
        }
        return status != null && !isActive(status);
    }

    private static boolean isReactivation(String newStatus, String status, String previousStatus,
                                          String newLifecycle, String previousLifecycle) {
        // Licence FSM reactivation: returning to an active lifecycle from a non-active one.
        if (newLifecycle != null && isActiveLifecycle(newLifecycle)) {
            return previousLifecycle == null || !isActiveLifecycle(previousLifecycle);
        }
        if (newStatus != null && isActive(newStatus)) {
            return previousStatus == null || !isActive(previousStatus);
        }
        if (status != null && isActive(status) && previousStatus != null && !isActive(previousStatus)) {
            return true;
        }
        return false;
    }

    /**
     * A provider lifecycle state that permits practice. LICENCED_ACTIVE and the
     * still-practising warning state LICENCE_DUE_FOR_RENEWAL are active; LAPSED,
     * SUSPENDED, RETIRED, RESTRICTED (and any non-licenced state) are not.
     */
    private static boolean isActiveLifecycle(String lifecycle) {
        if (lifecycle == null) {
            return false;
        }
        String l = lifecycle.trim().toUpperCase();
        return l.equals("LICENCED_ACTIVE") || l.equals("LICENSED_ACTIVE")
                || l.equals("LICENCE_DUE_FOR_RENEWAL") || l.equals("LICENSE_DUE_FOR_RENEWAL");
    }

    private static boolean isActive(String s) {
        return s != null && "ACTIVE".equalsIgnoreCase(s.trim());
    }

    private static String textOrEmpty(JsonNode node, String field) {
        String t = textOrNull(node, field);
        return t == null ? "" : t;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v.isBlank() ? null : v;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String nullable(String key) {
        return (key == null || key.isBlank()) ? null : key;
    }
}
