package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.EncounterEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.IdempotencyEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.*;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.costa.service.BillService;
import zw.gov.mohcc.impilo.costa.service.InpatientCostingService;
import zw.gov.mohcc.impilo.costa.service.PaymentIntegrationService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class CostaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostaEventConsumer.class);

    private final BillService billService;
    private final InpatientCostingService inpatientCostingService;
    private final PaymentIntegrationService paymentService;
    private final EncounterRepository encounterRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public CostaEventConsumer(BillService billService,
                              InpatientCostingService inpatientCostingService,
                              PaymentIntegrationService paymentService,
                              EncounterRepository encounterRepository,
                              IdempotencyRepository idempotencyRepository,
                              ObjectMapper objectMapper) {
        this.billService = billService;
        this.inpatientCostingService = inpatientCostingService;
        this.paymentService = paymentService;
        this.encounterRepository = encounterRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "pct.encounter.opened", groupId = "costa-costing-engine")
    @Transactional
    public void onEncounterOpened(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "PCT")) { ack.acknowledge(); return; }

            String journeyId = text(event, "journeyId");
            String tenantId = text(event, "tenantId");
            String facilityId = text(event, "facilityId");
            String patientCpid = text(event, "patientCpid");
            String encounterType = text(event, "encounterType");

            EncounterEntity encounter = new EncounterEntity();
            encounter.setEncounterId(zw.gov.mohcc.impilo.costa.service.UlidGenerator.generate());
            encounter.setTenantId(UUID.fromString(tenantId));
            encounter.setFacilityId(UUID.fromString(facilityId));
            encounter.setPatientCpid(patientCpid);
            encounter.setPctJourneyId(journeyId);
            encounter.setEncounterType(encounterType != null ? EncounterType.valueOf(encounterType) : EncounterType.OUTPATIENT);
            encounter.setStatus(EncounterStatus.OPEN);
            encounterRepository.save(encounter);

            markProcessed(eventId, "PCT");
            ack.acknowledge();
            log.info("Created encounter {} for PCT journey {}", encounter.getEncounterId(), journeyId);
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.opened", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "pct.encounter.closed", groupId = "costa-costing-engine")
    @Transactional
    public void onEncounterClosed(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "PCT")) { ack.acknowledge(); return; }

            String journeyId = text(event, "journeyId");
            encounterRepository.findByPctJourneyId(journeyId).ifPresent(enc -> {
                enc.setStatus(EncounterStatus.CLOSED);
                enc.setClosedAt(OffsetDateTime.now());
                encounterRepository.save(enc);
            });

            markProcessed(eventId, "PCT");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.closed", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "oros.order.placed", groupId = "costa-costing-engine")
    @Transactional
    public void onOrderPlaced(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "OROS")) { ack.acknowledge(); return; }

            String orderId = text(event, "orderId");
            String encounterRef = text(event, "encounterId");
            String msikaCode = text(event, "msikaCode");
            String description = text(event, "description");
            String orderType = text(event, "orderType");
            BigDecimal qty = event.has("qty") ? new BigDecimal(event.get("qty").asText()) : BigDecimal.ONE;

            // Find or create bill for this encounter
            if (encounterRef != null) {
                EncounterEntity encounter = encounterRepository.findByPctJourneyId(encounterRef)
                        .or(() -> encounterRepository.findById(encounterRef))
                        .orElse(null);

                if (encounter != null) {
                    var bills = billService.getBill(encounter.getEncounterId());
                    // Post line to existing bill or create new one
                    // Simplified: find existing accumulating bill
                    BillLineKind kind = "LAB".equals(orderType) || "IMAGING".equals(orderType)
                            ? BillLineKind.SERVICE : BillLineKind.SERVICE;

                    // This would normally find the active bill for the encounter
                    log.info("OROS order {} received for encounter {}", orderId, encounterRef);
                }
            }

            markProcessed(eventId, "OROS");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process oros.order.placed", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "pharmacy.dispense.completed", groupId = "costa-costing-engine")
    @Transactional
    public void onDispenseCompleted(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "PHARMACY")) { ack.acknowledge(); return; }

            String dispenseId = text(event, "dispenseId");
            String msikaCode = text(event, "msikaCode");
            BigDecimal qty = event.has("qty") ? new BigDecimal(event.get("qty").asText()) : BigDecimal.ONE;
            BigDecimal unitCost = event.has("unitCost") ? new BigDecimal(event.get("unitCost").asText()) : null;

            log.info("Pharmacy dispense {} completed: {} x {}", dispenseId, msikaCode, qty);

            markProcessed(eventId, "PHARMACY");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process pharmacy.dispense.completed", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "inventory.ledger.event_posted", groupId = "costa-costing-engine")
    @Transactional
    public void onInventoryEvent(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "INVENTORY")) { ack.acknowledge(); return; }

            String eventType = text(event, "eventType");
            String itemCode = text(event, "itemCode");
            BigDecimal qty = event.has("qtyDelta") ? new BigDecimal(event.get("qtyDelta").asText()).abs() : BigDecimal.ONE;
            BigDecimal unitCost = event.has("unitCost") ? new BigDecimal(event.get("unitCost").asText()) : null;

            if ("ISSUE".equals(eventType) || "WASTAGE".equals(eventType)) {
                log.info("Inventory {} for item {}: qty={}", eventType, itemCode, qty);
            }

            markProcessed(eventId, "INVENTORY");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process inventory event", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "mushex.payment.status_changed", groupId = "costa-costing-engine")
    @Transactional
    public void onPaymentStatusChanged(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "MUSHEX")) { ack.acknowledge(); return; }

            String paymentIntentId = text(event, "paymentIntentId");
            String status = text(event, "status");
            BigDecimal paidAmount = event.has("paidAmount")
                    ? new BigDecimal(event.get("paidAmount").asText()) : null;

            paymentService.handlePaymentStatusUpdate(paymentIntentId, status, paidAmount);

            markProcessed(eventId, "MUSHEX");
            ack.acknowledge();
            log.info("MUSHEX payment {} -> {}", paymentIntentId, status);
        } catch (Exception e) {
            log.error("Failed to process mushex.payment.status_changed", e);
            ack.acknowledge();
        }
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private boolean isProcessed(String eventId, String source) {
        if (eventId == null) return false;
        return idempotencyRepository.existsById(source + ":" + eventId);
    }

    private void markProcessed(String eventId, String source) {
        if (eventId == null) return;
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(source + ":" + eventId);
        entity.setSourceSystem(source);
        entity.setProcessedAt(OffsetDateTime.now());
        idempotencyRepository.save(entity);
    }
}
