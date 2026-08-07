package zw.gov.mohcc.impilo.experience.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

    private final ObjectMapper objectMapper;

    public UpstreamEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── PCT Events: encounter lifecycle ─────────────────────────────

    @KafkaListener(topics = {"pct.encounter.started", "impilo.pct.encounter"}, groupId = "experience-bff")
    public void onEncounterStarted(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Encounter started: encounterRef={}, journeyId={}, patientCpid={}",
                    node.path("encounterRef").asText(),
                    node.path("journeyId").asText(),
                    node.path("patientCpid").asText());
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.started: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = {"pct.encounter.completed", "impilo.pct.encounter"}, groupId = "experience-bff")
    public void onEncounterCompleted(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Encounter completed: encounterRef={}, journeyId={}",
                    node.path("encounterRef").asText(),
                    node.path("journeyId").asText());
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.completed: {}", e.getMessage());
        }
    }

    // ── OROS Events: order/result lifecycle ──────────────────────────

    @KafkaListener(topics = {"oros.order.status_changed", "impilo.oros.order"}, groupId = "experience-bff")
    public void onOrderStatusChanged(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Order status changed: orderId={}, status={}",
                    node.path("orderId").asText(),
                    node.path("status").asText());
        } catch (Exception e) {
            log.error("Failed to process oros.order.status_changed: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = {"oros.result.available", "impilo.oros.result"}, groupId = "experience-bff")
    public void onResultAvailable(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Result available: orderId={}, resultId={}",
                    node.path("orderId").asText(),
                    node.path("resultId").asText());
        } catch (Exception e) {
            log.error("Failed to process oros.result.available: {}", e.getMessage());
        }
    }

    // ── Pharmacy Events: dispense lifecycle ─────────────────────────

    /** Matches pharmacy-service {@code OutboxPublisher} ({@code DISPENSE_COMPLETED} → {@code pharmacy.dispense.complete}). */
    @KafkaListener(topics = {"pharmacy.dispense.complete", "impilo.pharmacy.dispense"}, groupId = "experience-bff")
    public void onDispenseCompleted(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Dispense completed: prescriptionId={}", node.path("prescriptionId").asText());
        } catch (Exception e) {
            log.error("Failed to process pharmacy.dispense.complete: {}", e.getMessage());
        }
    }

    // ── COSTA/Mushex Events: billing/payment lifecycle ──────────────

    @KafkaListener(topics = {"costa.bill.finalized", "impilo.costa.bill"}, groupId = "experience-bff")
    public void onBillFinalized(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Bill finalized: billId={}, amount={}",
                    node.path("billId").asText(),
                    node.path("totalAmount").asText());
        } catch (Exception e) {
            log.error("Failed to process costa.bill.finalized: {}", e.getMessage());
        }
    }

    /** Matches mushex-service {@code OutboxPublisher} ({@code STATUS_CHANGED} → {@code mushex.payment.status.changed}). */
    @KafkaListener(topics = {"mushex.payment.status.changed", "impilo.mushex.payment"}, groupId = "experience-bff")
    public void onPaymentStatusChanged(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Payment status changed: paymentId={}, status={}",
                    node.path("paymentId").asText(),
                    node.path("status").asText());
        } catch (Exception e) {
            log.error("Failed to process mushex.payment.status.changed: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = {"mushex.refund.status.changed", "impilo.mushex.refund"}, groupId = "experience-bff")
    public void onRefundStatusChanged(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Refund status changed: refundId={}, status={}",
                    node.path("refundId").asText(),
                    node.path("status").asText());
        } catch (Exception e) {
            log.error("Failed to process mushex.refund.status.changed: {}", e.getMessage());
        }
    }

    // ── TUSO Events: facility/workspace updates ─────────────────────

    @KafkaListener(topics = {"tuso.workspace.updated", "impilo.tuso.workspace"}, groupId = "experience-bff")
    public void onWorkspaceUpdated(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Workspace updated: workspaceId={}", node.path("workspaceId").asText());
        } catch (Exception e) {
            log.error("Failed to process tuso.workspace.updated: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = {"tuso.facility.profile.updated", "impilo.tuso.facility"}, groupId = "experience-bff")
    public void onFacilityProfileUpdated(String payload) {
        try {
            JsonNode node = domainBody(payload);
            log.info("Facility profile updated: facilityId={}", node.path("facilityId").asText());
        } catch (Exception e) {
            log.error("Failed to process tuso.facility.profile.updated: {}", e.getMessage());
        }
    }

    // ── PACS Events: imaging study lifecycle ───────────────────────

    @KafkaListener(topics = {"pacs.imaging_study", "pacs.study.available"}, groupId = "experience-bff")
    public void onPacsStudyAvailable(String payload) {
        try {
            JsonNode node = domainBody(payload);
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
            JsonNode node = domainBody(payload);
            String caseType = node.path("caseType").asText();
            log.info("Surveillance case opened: id={}, caseType={}, title={}",
                    node.path("id").asText(),
                    caseType,
                    node.path("title").asText());
        } catch (Exception e) {
            log.error("Failed to process impilo.surv.case.opened.v1: {}", e.getMessage());
        }
    }

    /**
     * The domain fields, whichever wire shape they arrive in.
     *
     * <p>Every listener here subscribes to a legacy topic <em>and</em> its v1.1 counterpart.
     * Legacy topics carry the raw domain payload with the fields at the root; v1.1 topics
     * carry an EventEnvelope with the same fields nested under {@code payload}. Reading the
     * root unconditionally worked only while the v1.1 topics had no producer — as services
     * convert to CompanionOutboxPublisher they acquire one, and every {@code path(...)} read
     * then silently resolves to an empty string rather than failing.</p>
     */
    private JsonNode domainBody(String message) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode root = objectMapper.readTree(message);
        JsonNode payload = root.get("payload");
        return payload != null && payload.isObject() ? payload : root;
    }

}
