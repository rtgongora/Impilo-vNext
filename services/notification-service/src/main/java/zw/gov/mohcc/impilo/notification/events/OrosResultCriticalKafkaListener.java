package zw.gov.mohcc.impilo.notification.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumes OROS {@code oros.result.critical} events and enqueues an urgent in-platform
 * notification to the requesting provider, requiring follow-up acknowledgement (spec §B —
 * critical result workflow). Clinical detail stays in-platform; external channels are notify-only.
 *
 * <p>Enabled by default ({@code impilo.kafka.oros.enabled=true}) — critical lab/imaging alerts must
 * reach clinicians, so the consumer was corrected from its previously-wrong disabled default (W5,
 * G1.9). {@code @ConditionalOnProperty(havingValue="true")} + the yml default {@code true} make it
 * active; set {@code KAFKA_OROS_CONSUMER_ENABLED=false} to opt out (e.g. broker-less test rigs).</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.kafka.oros.enabled", havingValue = "true")
public class OrosResultCriticalKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrosResultCriticalKafkaListener.class);

    /** Template + message kind for critical-result alerts. */
    private static final String TEMPLATE_KEY = "oros.result.critical";
    private static final String MESSAGE_KIND = "CRITICAL";
    private static final String CHANNEL_IN_APP = "IN_APP";

    private final ObjectMapper objectMapper;
    private final NotifyService notifyService;

    public OrosResultCriticalKafkaListener(ObjectMapper objectMapper, NotifyService notifyService) {
        this.objectMapper = objectMapper;
        this.notifyService = notifyService;
    }

    @KafkaListener(
            topics = "${impilo.kafka.oros.critical-topic:oros.result.critical}",
            groupId = "${impilo.kafka.oros.group-id:notification-service-oros}")
    @Transactional
    public void onCriticalResult(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            handle(objectMapper.readTree(json));
        } catch (Exception e) {
            log.warn("Failed to process OROS critical-result message: {}", e.getMessage());
        }
    }

    /** Package-visible for unit testing. */
    void handle(JsonNode root) {
        String tenantId = text(root, "tenantId");
        String requesterId = text(root, "requesterId");
        if (tenantId == null || requesterId == null) {
            log.warn("Skipping critical-result alert: missing tenantId/requesterId in payload");
            return;
        }

        Map<String, String> vars = new LinkedHashMap<>();
        putIfPresent(vars, "orderId", text(root, "orderId"));
        putIfPresent(vars, "resultId", text(root, "resultId"));
        putIfPresent(vars, "accessionNumber", text(root, "accessionNumber"));
        putIfPresent(vars, "criticalReason", text(root, "criticalReason"));
        putIfPresent(vars, "reportedBy", text(root, "reportedBy"));

        NotifyRequest request = new NotifyRequest(
                TEMPLATE_KEY,
                CHANNEL_IN_APP,
                requesterId,
                vars,
                text(root, "patientCpid"),
                MESSAGE_KIND,
                requesterId);

        RequestContext ctx = RequestContext.of(tenantId, null, null, null, null, null, null);
        notifyService.enqueue(request, ctx);
        log.info("Enqueued critical-result alert: requester={}, orderId={}", requesterId, vars.get("orderId"));
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        return n.get(field).asText();
    }
}
