package zw.gov.mohcc.impilo.pacs.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pacs.domain.StudyStatus;
import zw.gov.mohcc.impilo.pacs.events.ImagingPipelineKafkaContracts;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;
import zw.gov.mohcc.impilo.pacs.persistence.repository.ImagingStudyRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumes OROS order topics so the adapter can track expected imaging studies.
 */
@Component
@Profile("!test")
public class PacsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PacsEventConsumer.class);

    private final ImagingStudyRepository imagingStudyRepository;
    private final ObjectMapper objectMapper;

    public PacsEventConsumer(ImagingStudyRepository imagingStudyRepository,
                             ObjectMapper objectMapper) {
        this.imagingStudyRepository = imagingStudyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    ImagingPipelineKafkaContracts.ORDER_CREATED,
                    "oros.order.status_changed",
                    "impilo.oros.order"
            },
            groupId = "pacs-adapter"
    )
    @Transactional
    public void onOrosOrderEvent(String payload, Acknowledgment acknowledgment) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode orderNode = unwrapEnvelope(root);
            if (!isImagingOrder(orderNode)) {
                return;
            }

            String status = text(orderNode, "status");
            String routeStatus = text(orderNode, "routeStatus");
            boolean routedSignal = "ROUTED".equalsIgnoreCase(status)
                    || "SENT".equalsIgnoreCase(routeStatus)
                    || "ROUTED".equalsIgnoreCase(routeStatus);

            if (!"PLACED".equalsIgnoreCase(status) && !routedSignal) {
                return;
            }

            String orderId = text(orderNode, "orderId");
            String patientCpid = text(orderNode, "patientCpid");
            String tenantStr = text(orderNode, "tenantId");

            log.info("OROS imaging order signal: orderId={}, status={}, routeStatus={}",
                    orderId, status, routeStatus);

            if (orderId == null || patientCpid == null || tenantStr == null) {
                log.warn("Skipping imaging order tracking — missing orderId/patientCpid/tenantId");
                return;
            }

            maybeCreatePendingStudy(orderId, patientCpid, tenantStr);
        } catch (Exception e) {
            log.error("Poison or unparseable OROS message on pacs-adapter consumer: {}", e.getMessage(), e);
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }

    private JsonNode unwrapEnvelope(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()
                && (root.has("event_type") || root.has("eventType"))) {
            return root.get("payload");
        }
        return root;
    }

    private boolean isImagingOrder(JsonNode node) {
        String t = firstNonBlank(
                text(node, "orderType"),
                text(node, "order_type"),
                text(node, "type"));
        return "IMAGING".equalsIgnoreCase(t);
    }

    private void maybeCreatePendingStudy(String orderId, String patientCpid, String tenantStr) {
        if (imagingStudyRepository.findByOrosOrderId(orderId).isPresent()) {
            return;
        }
        String syntheticUid = "OROS-PENDING-" + orderId;
        if (imagingStudyRepository.existsByStudyUid(syntheticUid)) {
            return;
        }
        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantStr);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid tenant UUID on OROS order {}, skipping pending study", orderId);
            return;
        }

        ImagingStudyEntity pending = new ImagingStudyEntity();
        pending.setTenantId(tenantId);
        pending.setPatientCpid(patientCpid);
        pending.setStudyUid(syntheticUid);
        pending.setModality("UNKNOWN");
        pending.setDescription("Pending imaging acquisition for OROS order " + orderId);
        pending.setStudyDate(OffsetDateTime.now());
        pending.setStatus(StudyStatus.PENDING_ORDER.name());
        pending.setOrosOrderId(orderId);
        pending.setCreatedAt(OffsetDateTime.now());
        pending.setUpdatedAt(OffsetDateTime.now());
        imagingStudyRepository.save(pending);
        log.info("Created pending imaging study placeholder for OROS order {}", orderId);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
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
}
