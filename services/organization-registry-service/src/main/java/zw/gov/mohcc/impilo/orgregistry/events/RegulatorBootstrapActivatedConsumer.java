package zw.gov.mohcc.impilo.orgregistry.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.orgregistry.core.RegulatoryAppointmentService;

import java.util.UUID;

/**
 * The org-registry side of the regulator bootstrap handshake (RB-3b).
 *
 * <p>workforce-governance runs a founding-administrator appointment through its four-eyes rail
 * ({@code PlatformOriginService} + {@code TwoPersonApprovalService}) and, on the second approval,
 * emits {@code impilo.governance.regulator_bootstrap.activated}. This consumer turns that approved
 * request into an actual ACTIVE regulatory appointment — the point at which the trust chain
 * (Health ID → verified person → appointment → org → role) first grants anything.</p>
 *
 * <p><b>Separate consumer group by design.</b> {@code AdjudicationDecisionConsumer} already reads
 * this topic under group {@code organization-registry-service}. A second {@code @KafkaListener} in
 * the same group would split the topic's partitions between the two, so a bootstrap event could be
 * assigned to the adjudication listener's partitions and never reach here. A distinct group id makes
 * this an independent subscriber that sees every message and filters for its own event.</p>
 *
 * <p><b>The appointment is not self-made.</b> This consumer does not decide anything — the four-eyes
 * decision was made upstream and is recorded as the activation provenance. It materialises the
 * decision, and the domain guards still apply: the service's bootstrap-only check refuses a founding
 * role when the seat is no longer empty. A malformed or unroutable event is logged and acknowledged,
 * never left to crash the loop.</p>
 */
@Component
public class RegulatorBootstrapActivatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(RegulatorBootstrapActivatedConsumer.class);

    static final String EVENT_ACTIVATED = "impilo.governance.regulator_bootstrap.activated";

    private final RegulatoryAppointmentService appointmentService;
    private final ObjectMapper objectMapper;

    public RegulatorBootstrapActivatedConsumer(RegulatoryAppointmentService appointmentService,
                                               ObjectMapper objectMapper) {
        this.appointmentService = appointmentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${impilo.governance.outbox-topic:impilo.governance.events}",
            groupId = "organization-registry-regulator-bootstrap")
    public void onGovernanceEvent(String message, Acknowledgment acknowledgment) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            if (!EVENT_ACTIVATED.equals(text(envelope, "eventType"))) {
                return; // not our event — ignore quietly
            }
            JsonNode payload = envelope.has("payload") ? envelope.get("payload") : envelope;

            UUID tenantId = uuid(text(envelope, "tenantId"));
            if (tenantId == null) {
                tenantId = uuid(text(payload, "tenantId"));
            }
            UUID organizationId = uuid(text(payload, "organizationId"));
            String personHealthId = text(payload, "personHealthId");
            String roleCode = text(payload, "roleCode");
            String evidenceRef = text(payload, "evidenceRef");
            String bootstrapRequestId = text(payload, "bootstrapRequestId");

            if (tenantId == null || organizationId == null
                    || personHealthId == null || roleCode == null) {
                log.warn("regulator_bootstrap.activated missing routing fields "
                                + "[tenant={}, org={}, person={}, role={}, bootstrapRequestId={}] — dropping",
                        tenantId, organizationId, personHealthId, roleCode, bootstrapRequestId);
                return;
            }

            // Provenance, not authority: the four-eyes decision was made and recorded upstream.
            String activatedBy = "PLATFORM_BOOTSTRAP:" + (bootstrapRequestId == null ? "?" : bootstrapRequestId);
            appointmentService.activateFoundingAppointment(
                    tenantId, organizationId, personHealthId, roleCode, evidenceRef, activatedBy);
            log.info("Founding regulator appointment activated: org={} person={} role={} bootstrapRequestId={}",
                    organizationId, personHealthId, roleCode, bootstrapRequestId);
        } catch (Exception e) {
            // Honest failure: a domain guard rejecting the activation (e.g. the seat is no longer
            // empty) or a malformed event is logged and acked. It must not wedge the consumer loop —
            // and a genuinely retryable fault will re-deliver, where the idempotent activation makes
            // a repeat safe.
            log.error("Failed to process regulator_bootstrap.activated: {}", e.getMessage(), e);
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
