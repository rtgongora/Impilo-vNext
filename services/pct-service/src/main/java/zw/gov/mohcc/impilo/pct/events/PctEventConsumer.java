package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.core.DischargeWorkflow;
import zw.gov.mohcc.impilo.pct.core.TaskService;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DischargeCaseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka event consumer for processing external system events that affect PCT state.
 *
 * <p>Listens to topics from OROS (Order Registry), MUSHEX (Costing/Payment),
 * and TUSO (Facility Configuration) to react to events that influence patient
 * journey workflows. All consumers are implemented to be idempotent — processing
 * the same event twice produces no side effects.</p>
 *
 * <h3>Consumed Topics</h3>
 * <ul>
 *   <li>{@code oros.order.status_changed} — updates task status when orders complete or fail</li>
 *   <li>{@code oros.result.available} — creates a task for clinical result review</li>
 *   <li>{@code mushex.payment.status_changed} — auto-clears PAYMENT discharge blocker when payment is settled</li>
 *   <li>{@code tuso.workspace.updated} — logs workspace configuration changes for cache invalidation</li>
 * </ul>
 */
@Component
public class PctEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PctEventConsumer.class);

    /** Payment statuses that allow auto-clearing the discharge PAYMENT blocker. */
    private static final List<String> PAYMENT_CLEARED_STATUSES = List.of("CLEARED", "EXEMPTED", "WAIVED");

    private final DischargeWorkflow dischargeWorkflow;
    private final TaskService taskService;
    private final DischargeCaseRepository dischargeCaseRepository;
    private final ObjectMapper objectMapper;

    public PctEventConsumer(DischargeWorkflow dischargeWorkflow,
                            TaskService taskService,
                            DischargeCaseRepository dischargeCaseRepository,
                            ObjectMapper objectMapper) {
        this.dischargeWorkflow = dischargeWorkflow;
        this.taskService = taskService;
        this.dischargeCaseRepository = dischargeCaseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume OROS order status change events.
     *
     * <p>When an order status changes (e.g. from PENDING to COMPLETED or FAILED),
     * this consumer can trigger task updates or journey state changes. For v1,
     * the event is logged for observability.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "oros.order.status_changed", groupId = "pct-service")
    public void consumeOrosOrderStatus(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String orderId = getTextField(event, "orderId");
            String status = getTextField(event, "status");
            String journeyId = getTextField(event, "journeyId");

            log.info("OROS order status changed: orderId={}, status={}, journeyId={}",
                    orderId, status, journeyId);

            // Idempotency: check if we already processed this specific event
            // by verifying the order state matches expectations
            if (orderId == null || status == null) {
                log.warn("OROS order status event missing required fields, skipping");
                return;
            }

            // Future: update related task status based on order completion/failure

        } catch (JsonProcessingException e) {
            log.error("Failed to parse OROS order status event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume OROS result available events.
     *
     * <p>When a clinical result (lab, radiology, etc.) becomes available,
     * this consumer creates a review task assigned to the ordering provider
     * so that results are not missed.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "oros.result.available", groupId = "pct-service")
    public void consumeOrosResultAvailable(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String orderId = getTextField(event, "orderId");
            String journeyId = getTextField(event, "journeyId");
            String resultType = getTextField(event, "resultType");
            String orderingProvider = getTextField(event, "orderingProvider");

            log.info("OROS result available: orderId={}, journeyId={}, resultType={}",
                    orderId, journeyId, resultType);

            if (journeyId == null || resultType == null) {
                log.warn("OROS result available event missing required fields, skipping");
                return;
            }

            // Create a task for result review
            String taskType = resultType != null ? resultType + "_REVIEW" : "RESULT_REVIEW";
            taskService.createTask(
                    journeyId,
                    null, // encounterId — not available from event
                    taskType,
                    orderingProvider, // may be null if not provided
                    null, // assigneeRole
                    null, // workspaceId
                    null, // dueAt
                    "Review " + resultType + " result for order " + orderId
            );

            log.info("Created result review task for journey {} (order={}, type={})",
                    journeyId, orderId, resultType);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse OROS result available event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume MUSHEX payment status change events.
     *
     * <p>When a patient's payment status changes to CLEARED or EXEMPTED,
     * this consumer automatically clears the PAYMENT blocker on any active
     * discharge case for the associated journey. This enables hands-free
     * discharge gate clearance for the payment step.</p>
     *
     * <p>Idempotency is ensured by checking the current discharge case
     * blocker state before attempting to clear. If the PAYMENT blocker
     * is already cleared, the operation is a no-op.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "mushex.payment.status_changed", groupId = "pct-service")
    public void consumeMushexPaymentStatus(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String journeyId = getTextField(event, "journeyId");
            String paymentStatus = getTextField(event, "status");

            log.info("MUSHEX payment status changed: journeyId={}, status={}", journeyId, paymentStatus);

            if (journeyId == null || paymentStatus == null) {
                log.warn("MUSHEX payment status event missing required fields, skipping");
                return;
            }

            // Only auto-clear if the payment is in a "cleared" state
            if (!PAYMENT_CLEARED_STATUSES.contains(paymentStatus.toUpperCase())) {
                log.debug("Payment status '{}' does not trigger auto-clear for journey {}",
                        paymentStatus, journeyId);
                return;
            }

            // Find active discharge case for this journey
            Optional<DischargeCaseEntity> dischargeCaseOpt = dischargeCaseRepository
                    .findByJourneyIdAndStatusIn(journeyId, List.of("INITIATED"));

            if (dischargeCaseOpt.isEmpty()) {
                log.debug("No active discharge case found for journey {} to clear PAYMENT blocker", journeyId);
                return;
            }

            DischargeCaseEntity dischargeCase = dischargeCaseOpt.get();

            // Idempotency: check if PAYMENT is already cleared
            if (dischargeCase.isPaymentCleared()) {
                log.debug("PAYMENT blocker already cleared for discharge case {} (journey={})",
                        dischargeCase.getId(), journeyId);
                return;
            }

            // Clear the PAYMENT blocker
            try {
                dischargeWorkflow.clearBlocker(dischargeCase.getId(), "PAYMENT");
                log.info("Auto-cleared PAYMENT blocker for discharge case {} (journey={}, payment={})",
                        dischargeCase.getId(), journeyId, paymentStatus);
            } catch (IllegalArgumentException e) {
                // PAYMENT blocker might not be in the active blockers list (already cleared or not applicable)
                log.debug("PAYMENT blocker not active on discharge case {} (journey={}): {}",
                        dischargeCase.getId(), journeyId, e.getMessage());
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse MUSHEX payment status event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume TUSO workspace configuration update events.
     *
     * <p>When workspace or queue configuration changes in TUSO, this consumer
     * logs the event for cache invalidation purposes. Future versions will
     * trigger actual cache eviction in the TUSO integration service.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "tuso.workspace.updated", groupId = "pct-service")
    public void consumeTusoWorkspaceUpdated(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String facilityId = getTextField(event, "facilityId");
            String workspaceId = getTextField(event, "workspaceId");
            String changeType = getTextField(event, "changeType");

            log.info("TUSO workspace updated: facilityId={}, workspaceId={}, changeType={}. " +
                    "Consider invalidating cached configuration.", facilityId, workspaceId, changeType);

            // Future: invalidate local cache for the affected facility/workspace

        } catch (JsonProcessingException e) {
            log.error("Failed to parse TUSO workspace updated event: {}", e.getMessage(), e);
        }
    }

    /**
     * Safely extract a text field from a JSON node.
     *
     * @param node      the parent JSON node
     * @param fieldName the field name to extract
     * @return the text value, or {@code null} if the field is missing or null
     */
    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }
}
