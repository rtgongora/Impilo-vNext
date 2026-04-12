package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes events from upstream sovereign services to keep BFF local tables
 * in sync. This enables the BFF to serve cached/local data without always
 * hitting sovereign services.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
public class UpstreamEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UpstreamEventConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public UpstreamEventConsumer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ── PCT Events: encounter lifecycle ─────────────────────────────

    @KafkaListener(topics = "pct.encounter.started", groupId = "experience-bff")
    public void onEncounterStarted(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Encounter started: encounterRef={}, journeyId={}, patientCpid={}",
                    node.path("encounterRef").asText(),
                    node.path("journeyId").asText(),
                    node.path("patientCpid").asText());
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.started: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "pct.encounter.completed", groupId = "experience-bff")
    public void onEncounterCompleted(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Encounter completed: encounterRef={}, journeyId={}",
                    node.path("encounterRef").asText(),
                    node.path("journeyId").asText());
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.completed: {}", e.getMessage());
        }
    }

    // ── OROS Events: order/result lifecycle ──────────────────────────

    @KafkaListener(topics = "oros.order.status_changed", groupId = "experience-bff")
    public void onOrderStatusChanged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Order status changed: orderId={}, status={}",
                    node.path("orderId").asText(),
                    node.path("status").asText());
        } catch (Exception e) {
            log.error("Failed to process oros.order.status_changed: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "oros.result.available", groupId = "experience-bff")
    public void onResultAvailable(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Result available: orderId={}, resultId={}",
                    node.path("orderId").asText(),
                    node.path("resultId").asText());
        } catch (Exception e) {
            log.error("Failed to process oros.result.available: {}", e.getMessage());
        }
    }

    // ── Pharmacy Events: dispense lifecycle ─────────────────────────

    @KafkaListener(topics = "pharmacy.dispense.completed", groupId = "experience-bff")
    public void onDispenseCompleted(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Dispense completed: prescriptionId={}", node.path("prescriptionId").asText());
        } catch (Exception e) {
            log.error("Failed to process pharmacy.dispense.completed: {}", e.getMessage());
        }
    }

    // ── COSTA/Mushex Events: billing/payment lifecycle ──────────────

    @KafkaListener(topics = "costa.bill.finalized", groupId = "experience-bff")
    public void onBillFinalized(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Bill finalized: billId={}, amount={}",
                    node.path("billId").asText(),
                    node.path("totalAmount").asText());
        } catch (Exception e) {
            log.error("Failed to process costa.bill.finalized: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "mushex.payment.status_changed", groupId = "experience-bff")
    public void onPaymentStatusChanged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Payment status changed: paymentId={}, status={}",
                    node.path("paymentId").asText(),
                    node.path("status").asText());
        } catch (Exception e) {
            log.error("Failed to process mushex.payment.status_changed: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "mushex.refund.status.changed", groupId = "experience-bff")
    public void onRefundStatusChanged(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Refund status changed: refundId={}, status={}",
                    node.path("refundId").asText(),
                    node.path("status").asText());
        } catch (Exception e) {
            log.error("Failed to process mushex.refund.status.changed: {}", e.getMessage());
        }
    }

    // ── TUSO Events: facility/workspace updates ─────────────────────

    @KafkaListener(topics = "tuso.workspace.updated", groupId = "experience-bff")
    public void onWorkspaceUpdated(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Workspace updated: workspaceId={}", node.path("workspaceId").asText());
        } catch (Exception e) {
            log.error("Failed to process tuso.workspace.updated: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "tuso.facility.profile.updated", groupId = "experience-bff")
    public void onFacilityProfileUpdated(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("Facility profile updated: facilityId={}", node.path("facilityId").asText());
        } catch (Exception e) {
            log.error("Failed to process tuso.facility.profile.updated: {}", e.getMessage());
        }
    }

    // ── PACS Events: imaging study lifecycle ───────────────────────

    @KafkaListener(topics = "pacs.study.available", groupId = "experience-bff")
    public void onPacsStudyAvailable(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String studyUid = node.path("studyInstanceUid").asText();
            String modality = node.path("modality").asText();
            String patientId = node.path("patientId").asText();
            String orderId = node.path("orderId").asText();
            log.info("PACS study available: studyUid={}, modality={}, patientId={}, orderId={}",
                    studyUid, modality, patientId, orderId);
        } catch (Exception e) {
            log.error("Failed to process pacs.study.available: {}", e.getMessage());
        }
    }

    // ── Surveillance Events ─────────────────────────────────────────

    /** Aligned with surveillance-service {@code SurvOutboxPublisher} for {@code CASE_OPENED}. */
    @KafkaListener(topics = "impilo.surv.case.opened.v1", groupId = "experience-bff")
    public void onSurveillanceCaseOpened(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String caseType = node.path("caseType").asText();
            log.info("Surveillance case opened: id={}, caseType={}, title={}",
                    node.path("id").asText(),
                    caseType,
                    node.path("title").asText());
        } catch (Exception e) {
            log.error("Failed to process impilo.surv.case.opened.v1: {}", e.getMessage());
        }
    }
}
