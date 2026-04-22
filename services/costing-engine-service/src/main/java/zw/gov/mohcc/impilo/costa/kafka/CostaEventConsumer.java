package zw.gov.mohcc.impilo.costa.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.*;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.IdempotencyRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.RefundRepository;

import java.util.List;
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
    private final RefundRepository refundRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public CostaEventConsumer(BillService billService,
                              InpatientCostingService inpatientCostingService,
                              PaymentIntegrationService paymentService,
                              EncounterRepository encounterRepository,
                              RefundRepository refundRepository,
                              IdempotencyRepository idempotencyRepository,
                              ObjectMapper objectMapper) {
        this.billService = billService;
        this.inpatientCostingService = inpatientCostingService;
        this.paymentService = paymentService;
        this.encounterRepository = encounterRepository;
        this.refundRepository = refundRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"pct.encounter.started", "impilo.pct.encounter"}, groupId = "costa-costing-engine")
    @Transactional
    public void onEncounterStarted(String message, Acknowledgment ack) {
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

            // Auto-create a DRAFT bill for the encounter so order/dispense events
            // can post line items as they arrive (before discharge)
            try {
                BillHeaderEntity bill = billService.createDraft(
                        encounter.getEncounterId(), null, BillType.ENCOUNTER);
                log.info("Auto-created DRAFT bill {} for encounter {}",
                        bill.getBillId(), encounter.getEncounterId());
            } catch (Exception billErr) {
                log.warn("Failed to auto-create bill for encounter {} (non-blocking): {}",
                        encounter.getEncounterId(), billErr.getMessage());
            }

            markProcessed(eventId, "PCT");
            ack.acknowledge();
            log.info("Created encounter {} for PCT journey {}", encounter.getEncounterId(), journeyId);
        } catch (Exception e) {
            log.error("Failed to process pct.encounter.started", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = {"pct.encounter.completed", "impilo.pct.encounter"}, groupId = "costa-costing-engine")
    @Transactional
    public void onEncounterCompleted(String message, Acknowledgment ack) {
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
            log.error("Failed to process pct.encounter.completed", e);
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
            String encounterRef = text(event, "encounterRef");
            if (encounterRef == null) encounterRef = text(event, "encounterId");
            String orderType = text(event, "orderType");
            String patientCpid = text(event, "patientCpid");

            // Extract billable code: prefer ziboOrderCode, fall back to orderType
            String msikaCode = text(event, "ziboOrderCode");
            if (msikaCode == null || msikaCode.isBlank()) {
                msikaCode = orderType != null ? orderType : "ORDER";
            }

            // Build description from available fields
            String description = text(event, "clinicalNotes");
            if (description == null || description.isBlank()) {
                description = (orderType != null ? orderType : "Order") + " - " + orderId;
            }

            // Map order type to bill line kind
            BillLineKind kind = mapOrderTypeToLineKind(orderType);

            // Find COSTA encounter for this order
            EncounterEntity encounter = resolveEncounter(encounterRef, patientCpid);
            if (encounter == null) {
                log.warn("OROS order {}: no COSTA encounter found for ref={}, patient={}",
                        orderId, encounterRef, patientCpid);
                markProcessed(eventId, "OROS");
                ack.acknowledge();
                return;
            }

            // Find the active bill for this encounter
            BillHeaderEntity bill = billService.findActiveBillForEncounter(encounter.getEncounterId());
            if (bill == null) {
                log.warn("OROS order {}: no active bill for encounter {}, skipping line posting",
                        orderId, encounter.getEncounterId());
                markProcessed(eventId, "OROS");
                ack.acknowledge();
                return;
            }

            // Post the bill line
            BillLineEntity line = billService.postLine(
                    bill.getBillId(), msikaCode, description, kind,
                    BigDecimal.ONE, CostMethodType.TARIFF,
                    "OROS", orderId, Map.of());

            if (line != null) {
                log.info("OROS order {} posted as bill line {} on bill {}",
                        orderId, line.getLineId(), bill.getBillId());
            } else {
                log.info("OROS order {} excluded by charging rules for bill {}",
                        orderId, bill.getBillId());
            }

            markProcessed(eventId, "OROS");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process oros.order.placed", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "pharmacy.dispense.complete", groupId = "costa-costing-engine")
    @Transactional
    public void onDispenseCompleted(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "PHARMACY")) { ack.acknowledge(); return; }

            String dispenseId = text(event, "orderId");
            if (dispenseId == null) dispenseId = text(event, "dispenseId");
            String patientCpid = text(event, "patientCpid");
            String orosOrderId = text(event, "orosOrderId");

            // Extract drug info: try drugCode/drugDisplay, fall back to msikaCode
            String msikaCode = text(event, "drugCode");
            if (msikaCode == null) msikaCode = text(event, "msikaCode");
            if (msikaCode == null) msikaCode = "DISPENSE";

            String description = text(event, "drugDisplay");
            if (description == null || description.isBlank()) {
                description = "Pharmacy dispense - " + (dispenseId != null ? dispenseId : "unknown");
            }

            BigDecimal qty = BigDecimal.ONE;
            if (event.has("qtyDispensed")) {
                qty = new BigDecimal(event.get("qtyDispensed").asText());
            } else if (event.has("qty")) {
                qty = new BigDecimal(event.get("qty").asText());
            }

            // Determine cost method: use STOCK_AVG if unitCost provided
            CostMethodType costMethod = CostMethodType.TARIFF;
            Map<String, Object> context = new java.util.LinkedHashMap<>();
            if (event.has("unitCost") && !event.get("unitCost").isNull()) {
                costMethod = CostMethodType.STOCK_AVG;
                context.put("unit_cost", event.get("unitCost").asText());
            }

            // Find encounter: try via OROS order reference, then by patient
            EncounterEntity encounter = null;
            if (orosOrderId != null) {
                encounter = resolveEncounter(orosOrderId, patientCpid);
            }
            if (encounter == null && patientCpid != null) {
                encounter = resolveEncounter(null, patientCpid);
            }

            if (encounter == null) {
                log.warn("Pharmacy dispense {}: no COSTA encounter found for patient={}",
                        dispenseId, patientCpid);
                markProcessed(eventId, "PHARMACY");
                ack.acknowledge();
                return;
            }

            // Find the active bill for this encounter
            BillHeaderEntity bill = billService.findActiveBillForEncounter(encounter.getEncounterId());
            if (bill == null) {
                log.warn("Pharmacy dispense {}: no active bill for encounter {}, skipping",
                        dispenseId, encounter.getEncounterId());
                markProcessed(eventId, "PHARMACY");
                ack.acknowledge();
                return;
            }

            // Post the bill line as PRODUCT
            BillLineEntity line = billService.postLine(
                    bill.getBillId(), msikaCode, description, BillLineKind.PRODUCT,
                    qty, costMethod,
                    "PHARMACY", dispenseId != null ? dispenseId : "unknown",
                    context);

            if (line != null) {
                log.info("Pharmacy dispense {} posted as bill line {} on bill {}",
                        dispenseId, line.getLineId(), bill.getBillId());
            } else {
                log.info("Pharmacy dispense {} excluded by charging rules for bill {}",
                        dispenseId, bill.getBillId());
            }

            markProcessed(eventId, "PHARMACY");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process pharmacy.dispense.complete", e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "inventory.ledger.event.created", groupId = "costa-costing-engine")
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

    @KafkaListener(topics = "mushex.payment.status.changed", groupId = "costa-costing-engine")
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
            log.error("Failed to process mushex.payment.status.changed", e);
            ack.acknowledge();
        }
    }

    /**
     * When MusheX processes or fails a refund, update the corresponding
     * COSTA RefundEntity status and link the mushexRefundId.
     */
    @KafkaListener(topics = "mushex.refund.status.changed", groupId = "costa-costing-engine")
    @Transactional
    public void onRefundStatusChanged(String message, Acknowledgment ack) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventId = text(event, "eventId");
            if (isProcessed(eventId, "MUSHEX_REFUND")) { ack.acknowledge(); return; }

            String mushexRefundId = text(event, "refundId");
            String intentId = text(event, "intentId");
            String status = text(event, "status");
            String billId = text(event, "billId");
            BigDecimal eventAmount = event.has("amount")
                    ? new BigDecimal(event.get("amount").asText()) : null;

            // Try to find the COSTA refund to update
            // Strategy 1: match by mushexRefundId (already linked)
            // Strategy 2: match by billId + PENDING status + amount
            if (billId != null) {
                List<RefundEntity> costaRefunds = refundRepository.findByBillId(billId);

                // Prefer already-linked refund
                RefundEntity target = costaRefunds.stream()
                        .filter(r -> mushexRefundId != null && mushexRefundId.equals(r.getMushexRefundId()))
                        .findFirst()
                        .orElse(null);

                // Fall back to PENDING refund matched by amount
                if (target == null) {
                    target = costaRefunds.stream()
                            .filter(r -> r.getStatus() == RefundStatus.PENDING)
                            .filter(r -> eventAmount == null || r.getAmount().compareTo(eventAmount) == 0)
                            .findFirst()
                            .orElse(null);
                }

                // Last resort: any PENDING refund
                if (target == null) {
                    target = costaRefunds.stream()
                            .filter(r -> r.getStatus() == RefundStatus.PENDING)
                            .findFirst()
                            .orElse(null);
                }

                if (target != null) {
                    target.setMushexRefundId(mushexRefundId);

                    if ("COMPLETED".equals(status)) {
                        target.setStatus(RefundStatus.PROCESSED);
                        target.setProcessedAt(OffsetDateTime.now());
                        log.info("COSTA refund {} marked PROCESSED (MusheX refund {})",
                                target.getId(), mushexRefundId);
                    } else if ("FAILED".equals(status)) {
                        target.setStatus(RefundStatus.FAILED);
                        target.setProcessedAt(OffsetDateTime.now());
                        log.info("COSTA refund {} marked FAILED (MusheX refund {})",
                                target.getId(), mushexRefundId);
                    }

                    refundRepository.save(target);
                } else {
                    log.warn("No PENDING COSTA refund found for bill {} to match MusheX refund {}",
                            billId, mushexRefundId);
                }
            }

            markProcessed(eventId, "MUSHEX_REFUND");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process mushex.refund.status.changed", e);
            ack.acknowledge();
        }
    }

    /**
     * Resolve a COSTA EncounterEntity from an encounter reference and/or patient CPID.
     * Tries: pctJourneyId lookup, direct encounterId lookup, then most recent by patient.
     */
    private EncounterEntity resolveEncounter(String encounterRef, String patientCpid) {
        if (encounterRef != null) {
            var byJourney = encounterRepository.findByPctJourneyId(encounterRef);
            if (byJourney.isPresent()) return byJourney.get();

            var byId = encounterRepository.findById(encounterRef);
            if (byId.isPresent()) return byId.get();
        }
        if (patientCpid != null) {
            var byPatient = encounterRepository.findByPatientCpidOrderByOpenedAtDesc(patientCpid);
            if (!byPatient.isEmpty()) return byPatient.get(0);
        }
        return null;
    }

    /**
     * Map OROS order type to the appropriate BillLineKind.
     */
    private BillLineKind mapOrderTypeToLineKind(String orderType) {
        if (orderType == null) return BillLineKind.SERVICE;
        return switch (orderType) {
            case "LAB", "IMAGING", "PROCEDURE" -> BillLineKind.SERVICE;
            case "PHARMACY" -> BillLineKind.PRODUCT;
            default -> BillLineKind.SERVICE;
        };
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
