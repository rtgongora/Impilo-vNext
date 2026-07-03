package zw.gov.mohcc.impilo.oros.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.domain.WorkstepStatus;
import zw.gov.mohcc.impilo.oros.domain.WorkstepType;
import zw.gov.mohcc.impilo.oros.persistence.entity.IdempotencyEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.ResultRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.WorkstepRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka event consumer for processing external system events that affect OROS state.
 *
 * <p>Listens to topics from LIMS (Laboratory Information Management System),
 * PACS (Picture Archiving and Communication System), and pharmacy systems
 * to react to order status changes and result delivery. All consumers are
 * implemented to be idempotent using the {@link IdempotencyRepository}.</p>
 *
 * <h3>Consumed Topics</h3>
 * <ul>
 *   <li>{@code lims.order.status_changed} -- updates order status and progresses worksteps</li>
 *   <li>{@code lims.result.available} -- creates lab results and reconciles if needed</li>
 *   <li>{@code pacs.study.available} -- creates imaging results from PACS studies</li>
 *   <li>{@code pharmacy.dispense.complete} -- creates pharmacy results from dispense events</li>
 * </ul>
 */
@Service
public class OrosEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrosEventConsumer.class);

    private final OrderRepository orderRepository;
    private final WorkstepRepository workstepRepository;
    private final ResultRepository resultRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the OrosEventConsumer with all required dependencies.
     *
     * @param orderRepository       repository for order lookups and updates
     * @param workstepRepository    repository for workstep progress updates
     * @param resultRepository      repository for result creation
     * @param idempotencyRepository repository for idempotency checks
     * @param objectMapper          Jackson mapper for JSON deserialization
     */
    public OrosEventConsumer(OrderRepository orderRepository,
                             WorkstepRepository workstepRepository,
                             ResultRepository resultRepository,
                             IdempotencyRepository idempotencyRepository,
                             ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.workstepRepository = workstepRepository;
        this.resultRepository = resultRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume LIMS order status change events.
     *
     * <p>When the LIMS reports an order status change (e.g. specimen received,
     * analysis started, analysis completed), this consumer updates the corresponding
     * workstep and potentially the order status.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "lims.order.status_changed", groupId = "oros-service")
    @Transactional
    public void consumeLimsOrderStatus(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = getTextField(event, "eventId");
            String externalOrderRef = getTextField(event, "orderRef");
            String status = getTextField(event, "status");
            String orderId = getTextField(event, "orderId");

            if (eventId == null || orderId == null) {
                log.warn("LIMS order status event missing required fields, skipping");
                return;
            }

            // Idempotency check
            if (isAlreadyProcessed(eventId, "LIMS")) {
                log.debug("LIMS order status event {} already processed, skipping", eventId);
                return;
            }

            log.info("LIMS order status changed: orderId={}, status={}, ref={}",
                    orderId, status, externalOrderRef);

            Optional<OrderEntity> orderOpt = orderRepository.findByOrderId(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for LIMS status update: {}", orderId);
                markProcessed(eventId, "LIMS");
                return;
            }

            OrderEntity order = orderOpt.get();

            // Progress worksteps based on LIMS status
            if ("SPECIMEN_RECEIVED".equals(status)) {
                progressWorkstep(orderId, WorkstepType.SPECIMEN_COLLECTION, WorkstepType.SPECIMEN_RECEIVED);
            } else if ("IN_ANALYSIS".equals(status)) {
                progressWorkstep(orderId, WorkstepType.SPECIMEN_RECEIVED, WorkstepType.ANALYSIS);
            } else if ("ANALYSIS_COMPLETE".equals(status)) {
                completeWorkstep(orderId, WorkstepType.ANALYSIS);
            } else if ("REJECTED".equals(status)) {
                if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REJECTED) {
                    order.setStatus(OrderStatus.FAILED);
                    order.setClinicalNotes((order.getClinicalNotes() != null ? order.getClinicalNotes() + "\n" : "")
                            + "LIMS rejection: " + getTextField(event, "reason"));
                    orderRepository.save(order);
                }
            }

            markProcessed(eventId, "LIMS");

        } catch (JsonProcessingException e) {
            log.error("Failed to parse LIMS order status event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume LIMS result available events.
     *
     * <p>When the LIMS produces a result for an order, this consumer creates a
     * {@link ResultEntity} and transitions the order to RESULT_AVAILABLE if
     * the order is in an appropriate state. Creates a reconciliation entry
     * if the order cannot be directly matched.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "lims.result.available", groupId = "oros-service")
    @Transactional
    public void consumeLimsResult(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = getTextField(event, "eventId");
            String orderId = getTextField(event, "orderId");
            String summary = getTextField(event, "summary");
            String codes = getTextField(event, "resultCodes");
            boolean critical = getBooleanField(event, "isCritical");

            if (eventId == null) {
                log.warn("LIMS result event missing eventId, skipping");
                return;
            }

            if (isAlreadyProcessed(eventId, "LIMS")) {
                log.debug("LIMS result event {} already processed, skipping", eventId);
                return;
            }

            log.info("LIMS result available: orderId={}, critical={}", orderId, critical);

            if (orderId == null) {
                log.warn("LIMS result event missing orderId, cannot create result");
                markProcessed(eventId, "LIMS");
                return;
            }

            Optional<OrderEntity> orderOpt = orderRepository.findByOrderId(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for LIMS result: {}", orderId);
                markProcessed(eventId, "LIMS");
                return;
            }

            OrderEntity order = orderOpt.get();

            // Create result entity
            ResultEntity result = new ResultEntity();
            result.setResultId(UUID.randomUUID());
            result.setOrderId(orderId);
            result.setKind(ResultKind.LAB);
            result.setSummary(summary);
            result.setZiboResultCodes(codes);
            result.setCritical(critical);
            result.setReportedBy("LIMS-SYSTEM");
            resultRepository.save(result);

            // Transition order if applicable
            OrderStatus currentStatus = order.getStatus();
            if (currentStatus == OrderStatus.IN_PROGRESS || currentStatus == OrderStatus.PARTIAL_RESULT) {
                order.setStatus(OrderStatus.RESULT_AVAILABLE);
                orderRepository.save(order);
            }

            // Complete the REPORTING workstep if it exists
            completeWorkstep(orderId, WorkstepType.REPORTING);

            markProcessed(eventId, "LIMS");

            log.info("LIMS result created: orderId={}, resultId={}, critical={}",
                    orderId, result.getResultId(), critical);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse LIMS result event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume PACS study available events.
     *
     * <p>When a PACS study (imaging result) becomes available, this consumer
     * creates an IMAGING result entity and transitions the order appropriately.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    // Topic set covers every route the PACS adapter actually publishes on:
    // imaging.study.received (legacy imaging-pipeline contract) and
    // impilo.pacs.imaging_study (v1.1 registry topic). The historical
    // pacs.imaging_study/pacs.study.available names never matched a producer —
    // the imaging result loop was silently dead until these were added.
    @KafkaListener(topics = {"pacs.imaging_study", "pacs.study.available",
            "imaging.study.received", "imaging.study.correlated", "impilo.pacs.imaging_study"},
            groupId = "oros-service")
    @Transactional
    public void consumePacsStudy(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode event = extractPayload(root);
            if (event == null || event.isNull()) {
                log.warn("PACS study event missing payload, skipping");
                return;
            }
            String eventId = getTextField(event, "eventId");
            String orderId = getTextField(event, "orderId");
            String studyUid = getTextField(event, "studyInstanceUid");
            String modality = getTextField(event, "modality");
            String reportSummary = getTextField(event, "reportSummary");
            boolean critical = getBooleanField(event, "isCritical");

            if (eventId == null) {
                log.warn("PACS study event missing eventId, skipping");
                return;
            }

            if (isAlreadyProcessed(eventId, "PACS")) {
                log.debug("PACS study event {} already processed, skipping", eventId);
                return;
            }

            log.info("PACS study available: orderId={}, studyUid={}, modality={}",
                    orderId, studyUid, modality);

            if (orderId == null) {
                log.warn("PACS study event missing orderId, cannot process");
                markProcessed(eventId, "PACS");
                return;
            }

            Optional<OrderEntity> orderOpt = orderRepository.findByOrderId(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for PACS study: {}", orderId);
                markProcessed(eventId, "PACS");
                return;
            }

            OrderEntity order = orderOpt.get();

            // Create imaging result
            ResultEntity result = new ResultEntity();
            result.setResultId(UUID.randomUUID());
            result.setOrderId(orderId);
            result.setKind(ResultKind.IMAGING);
            // summary column is jsonb — encode the free-text report summary the
            // same way the REST result path does (plain strings fail the insert).
            result.setSummary(objectMapper.writeValueAsString(
                    reportSummary != null ? reportSummary : "Imaging study available"));
            result.setDocIds(studyUid != null ? "[\"" + studyUid + "\"]" : null);
            result.setCritical(critical);
            result.setReportedBy("PACS-SYSTEM");
            resultRepository.save(result);

            // Transition order if applicable. A PACS study arriving on a
            // not-yet-started order implies acquisition happened outside the
            // system (no modality worklist integration) — walk the legal
            // coarse transitions instead of stranding the order at PLACED
            // with a result attached (mirrors ResultService.postResult).
            OrderStatus currentStatus = order.getStatus();
            if (currentStatus == OrderStatus.PLACED) {
                order.setStatus(OrderStatus.ACCEPTED);
                currentStatus = order.getStatus();
            }
            if (currentStatus == OrderStatus.ACCEPTED || currentStatus == OrderStatus.SCHEDULED) {
                order.setStatus(OrderStatus.IN_PROGRESS);
                currentStatus = order.getStatus();
            }
            if (currentStatus == OrderStatus.IN_PROGRESS || currentStatus == OrderStatus.PARTIAL_RESULT) {
                order.setStatus(OrderStatus.RESULT_AVAILABLE);
            }
            orderRepository.save(order);

            // Complete imaging worksteps
            completeWorkstep(orderId, WorkstepType.IMAGING_REPORTING);

            markProcessed(eventId, "PACS");

            log.info("PACS result created: orderId={}, resultId={}, studyUid={}",
                    orderId, result.getResultId(), studyUid);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse PACS study event: {}", e.getMessage(), e);
        }
    }

    private JsonNode extractPayload(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (!root.has("payload")) {
            return root;
        }
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            return null;
        }
        if (payload.isObject()) {
            return payload;
        }
        if (payload.isTextual()) {
            try {
                return objectMapper.readTree(payload.asText());
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse PACS textual payload envelope: {}", e.getMessage());
                return null;
            }
        }
        return payload;
    }

    /**
     * Consume pharmacy dispense completed events.
     *
     * <p>When a pharmacy system completes dispensing for an order, this consumer
     * creates a PHARMACY result entity and transitions the order.</p>
     *
     * @param message the Kafka message payload (JSON)
     */
    @KafkaListener(topics = "pharmacy.dispense.complete", groupId = "oros-service")
    @Transactional
    public void consumePharmacyDispense(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = getTextField(event, "eventId");
            String orderId = getTextField(event, "orderId");
            String dispenseSummary = getTextField(event, "dispenseSummary");
            String dispensedBy = getTextField(event, "dispensedBy");

            if (eventId == null) {
                log.warn("Pharmacy dispense event missing eventId, skipping");
                return;
            }

            if (isAlreadyProcessed(eventId, "PHARMACY")) {
                log.debug("Pharmacy dispense event {} already processed, skipping", eventId);
                return;
            }

            log.info("Pharmacy dispense completed: orderId={}, dispensedBy={}", orderId, dispensedBy);

            if (orderId == null) {
                log.warn("Pharmacy dispense event missing orderId, cannot process");
                markProcessed(eventId, "PHARMACY");
                return;
            }

            Optional<OrderEntity> orderOpt = orderRepository.findByOrderId(orderId);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for pharmacy dispense: {}", orderId);
                markProcessed(eventId, "PHARMACY");
                return;
            }

            OrderEntity order = orderOpt.get();

            // Create pharmacy result
            ResultEntity result = new ResultEntity();
            result.setResultId(UUID.randomUUID());
            result.setOrderId(orderId);
            result.setKind(ResultKind.PHARMACY);
            result.setSummary(dispenseSummary);
            result.setCritical(false);
            result.setReportedBy(dispensedBy != null ? dispensedBy : "PHARMACY-SYSTEM");
            resultRepository.save(result);

            // Transition order
            OrderStatus currentStatus = order.getStatus();
            if (currentStatus == OrderStatus.IN_PROGRESS || currentStatus == OrderStatus.PARTIAL_RESULT) {
                order.setStatus(OrderStatus.RESULT_AVAILABLE);
                orderRepository.save(order);
            }

            // Complete the DISPENSE workstep
            completeWorkstep(orderId, WorkstepType.DISPENSE);

            markProcessed(eventId, "PHARMACY");

            log.info("Pharmacy result created: orderId={}, resultId={}", orderId, result.getResultId());

        } catch (JsonProcessingException e) {
            log.error("Failed to parse pharmacy dispense event: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Checks whether an event has already been processed (idempotency check).
     */
    private boolean isAlreadyProcessed(String eventId, String sourceSystem) {
        String key = sourceSystem + ":" + eventId;
        return idempotencyRepository.existsById(key);
    }

    /**
     * Marks an event as processed in the idempotency table.
     */
    private void markProcessed(String eventId, String sourceSystem) {
        String key = sourceSystem + ":" + eventId;
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(key);
        entity.setSourceSystem(sourceSystem);
        entity.setProcessedAt(OffsetDateTime.now());
        idempotencyRepository.save(entity);
    }

    /**
     * Completes the current workstep and starts the next one for the given step type.
     */
    private void progressWorkstep(String orderId, WorkstepType currentType, WorkstepType nextType) {
        completeWorkstep(orderId, currentType);
        startWorkstep(orderId, nextType);
    }

    /**
     * Completes a workstep of the specified type for an order.
     */
    private void completeWorkstep(String orderId, WorkstepType stepType) {
        workstepRepository.findByOrderIdAndStepType(orderId, stepType)
                .ifPresent(step -> {
                    if (step.getStatus() == WorkstepStatus.IN_PROGRESS
                            || step.getStatus() == WorkstepStatus.PENDING) {
                        step.setStatus(WorkstepStatus.COMPLETED);
                        step.setCompletedAt(OffsetDateTime.now());
                        workstepRepository.save(step);
                        log.debug("Workstep {} completed for order {} (type={})",
                                step.getWorkstepId(), orderId, stepType);
                    }
                });
    }

    /**
     * Starts a workstep of the specified type for an order.
     */
    private void startWorkstep(String orderId, WorkstepType stepType) {
        workstepRepository.findByOrderIdAndStepType(orderId, stepType)
                .ifPresent(step -> {
                    if (step.getStatus() == WorkstepStatus.PENDING) {
                        step.setStatus(WorkstepStatus.IN_PROGRESS);
                        step.setStartedAt(OffsetDateTime.now());
                        workstepRepository.save(step);
                        log.debug("Workstep {} started for order {} (type={})",
                                step.getWorkstepId(), orderId, stepType);
                    }
                });
    }

    /**
     * Safely extracts a text field from a JSON node.
     */
    private String getTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }

    /**
     * Safely extracts a boolean field from a JSON node (defaults to false).
     */
    private boolean getBooleanField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() && field.asBoolean();
    }
}
