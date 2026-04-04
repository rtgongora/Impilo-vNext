package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.*;
import zw.gov.mohcc.impilo.costa.domain.repository.*;
import zw.gov.mohcc.impilo.costa.engine.CostEngine;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.engine.CostResult;
import zw.gov.mohcc.impilo.costa.exemptions.BillSplitResult;
import zw.gov.mohcc.impilo.costa.exemptions.ExemptionEngine;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;
import zw.gov.mohcc.impilo.costa.rules.RuleContext;
import zw.gov.mohcc.impilo.costa.rules.RuleResult;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private final BillHeaderRepository billHeaderRepository;
    private final BillLineRepository billLineRepository;
    private final BillPartyRepository billPartyRepository;
    private final EncounterRepository encounterRepository;
    private final ApprovalRepository approvalRepository;
    private final EventOutboxRepository outboxRepository;
    private final CostEngineRegistry costEngineRegistry;
    private final ChargingRuleEngine chargingRuleEngine;
    private final ExemptionEngine exemptionEngine;
    private final ObjectMapper objectMapper;

    public BillService(BillHeaderRepository billHeaderRepository,
                       BillLineRepository billLineRepository,
                       BillPartyRepository billPartyRepository,
                       EncounterRepository encounterRepository,
                       ApprovalRepository approvalRepository,
                       EventOutboxRepository outboxRepository,
                       CostEngineRegistry costEngineRegistry,
                       ChargingRuleEngine chargingRuleEngine,
                       ExemptionEngine exemptionEngine,
                       ObjectMapper objectMapper) {
        this.billHeaderRepository = billHeaderRepository;
        this.billLineRepository = billLineRepository;
        this.billPartyRepository = billPartyRepository;
        this.encounterRepository = encounterRepository;
        this.approvalRepository = approvalRepository;
        this.outboxRepository = outboxRepository;
        this.costEngineRegistry = costEngineRegistry;
        this.chargingRuleEngine = chargingRuleEngine;
        this.exemptionEngine = exemptionEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BillHeaderEntity createDraft(String encounterId, String msikaOrderId, BillType billType) {
        TrustContext ctx = TrustContextHolder.require();

        BillHeaderEntity bill = new BillHeaderEntity();
        bill.setBillId(UlidGenerator.generate());
        bill.setTenantId(ctx.tenantId());
        bill.setFacilityId(ctx.facilityId());
        bill.setEncounterId(encounterId);
        bill.setMsikaOrderId(msikaOrderId);
        bill.setBillType(billType);
        bill.setStatus(BillStatus.DRAFT);
        bill.setCurrency("USD");
        bill = billHeaderRepository.save(bill);

        publishEvent("BILL", bill.getBillId(), "BILL_DRAFT_CREATED",
                Map.of("billId", bill.getBillId(), "encounterType", billType.name()),
                ctx.tenantId());

        return bill;
    }

    @Transactional
    public BillLineEntity postLine(String billId, String msikaCode, String description,
                                    BillLineKind kind, BigDecimal qty,
                                    CostMethodType costMethod, String sourceEvent,
                                    String sourceRef, Map<String, Object> context) {
        TrustContext ctx = TrustContextHolder.require();

        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.DRAFT && bill.getStatus() != BillStatus.ACCUMULATING) {
            throw new IllegalStateException("Cannot post lines to bill in status: " + bill.getStatus());
        }

        // Transition to ACCUMULATING
        if (bill.getStatus() == BillStatus.DRAFT) {
            bill.setStatus(BillStatus.ACCUMULATING);
        }

        // Compute cost
        CostMethodType method = costMethod != null ? costMethod : CostMethodType.TARIFF;
        CostEngine engine = costEngineRegistry.resolveOrDefault(method, CostMethodType.TARIFF);
        CostResult costResult = engine.compute(ctx.tenantId(), ctx.facilityId(), msikaCode, qty,
                context != null ? context : Map.of());

        // Apply charging rules
        EncounterEntity encounter = bill.getEncounterId() != null
                ? encounterRepository.findById(bill.getEncounterId()).orElse(null) : null;

        String facilityCategory = context != null ? (String) context.get("facility_category") : null;
        String patientCategory = context != null ? (String) context.get("patient_category") : null;
        String encounterType = encounter != null ? encounter.getEncounterType().name() : "OUTPATIENT";
        boolean isEmergency = encounter != null && encounter.getEncounterType() == EncounterType.EMERGENCY;
        boolean isAdmitted = encounter != null && encounter.getEncounterType() == EncounterType.INPATIENT;

        RuleContext ruleCtx = new RuleContext(ctx.tenantId(), ctx.facilityId(),
                facilityCategory, patientCategory, msikaCode, kind, qty,
                costResult.totalCost(), encounterType, LocalDateTime.now(),
                isEmergency, isAdmitted, context != null ? context : Map.of());

        RuleResult ruleResult = chargingRuleEngine.evaluate(ruleCtx);

        if (ruleResult.excluded()) {
            log.info("Line excluded by charging rules: msikaCode={}", msikaCode);
            return null;
        }

        // Create bill line
        BillLineEntity line = new BillLineEntity();
        line.setLineId(UlidGenerator.generate());
        line.setBillId(billId);
        line.setMsikaCode(msikaCode);
        line.setDescription(description);
        line.setKind(kind);
        line.setQty(qty);
        line.setUnitPrice(ruleResult.chargeAmount().divide(qty, 2, java.math.RoundingMode.HALF_UP));
        line.setAmount(ruleResult.chargeAmount());
        line.setCostAmount(costResult.totalCost());
        line.setCostMethodUsed(costResult.methodUsed().name());
        line.setSourceEvent(sourceEvent);
        line.setSourceRef(sourceRef);

        try {
            line.setCostTrace(objectMapper.writeValueAsString(costResult.trace()));
            line.setChargeTrace(objectMapper.writeValueAsString(ruleResult.trace()));
        } catch (Exception e) {
            log.error("Failed to serialize trace", e);
        }

        line = billLineRepository.save(line);

        // Update bill totals
        recalculateTotals(bill);
        billHeaderRepository.save(bill);

        return line;
    }

    @Transactional
    public BillHeaderEntity recompute(String billId) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        recalculateTotals(bill);
        recomputePartySplit(bill);
        return billHeaderRepository.save(bill);
    }

    @Transactional
    public BillHeaderEntity submitForApproval(String billId) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.ACCUMULATING && bill.getStatus() != BillStatus.DRAFT) {
            throw new IllegalStateException("Cannot submit bill in status: " + bill.getStatus());
        }

        recomputePartySplit(bill);
        bill.setStatus(BillStatus.APPROVAL_PENDING);
        bill = billHeaderRepository.save(bill);

        // Create approval record
        TrustContext ctx = TrustContextHolder.require();
        ApprovalEntity approval = new ApprovalEntity();
        approval.setBillId(billId);
        approval.setStep(ApprovalStep.FINANCE_REVIEW);
        approval.setStatus(ApprovalStatus.PENDING);
        approvalRepository.save(approval);

        publishEvent("BILL", billId, "BILL_APPROVAL_REQUESTED",
                Map.of("billId", billId, "total", bill.getTotalPayable()),
                ctx.tenantId());

        return bill;
    }

    @Transactional
    public BillHeaderEntity approve(String billId, String approverActorId, String note) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.APPROVAL_PENDING) {
            throw new IllegalStateException("Bill not pending approval: " + bill.getStatus());
        }

        List<ApprovalEntity> pending = approvalRepository.findByBillIdAndStatus(billId, ApprovalStatus.PENDING);
        for (ApprovalEntity approval : pending) {
            approval.setStatus(ApprovalStatus.APPROVED);
            approval.setApproverActorId(approverActorId);
            approval.setNote(note);
            approval.setResolvedAt(OffsetDateTime.now());
            approvalRepository.save(approval);
        }

        bill.setStatus(BillStatus.APPROVED);
        bill = billHeaderRepository.save(bill);

        TrustContext ctx = TrustContextHolder.require();
        publishEvent("BILL", billId, "BILL_APPROVED",
                Map.of("billId", billId, "approver", approverActorId),
                ctx.tenantId());

        return bill;
    }

    @Transactional
    public BillHeaderEntity finalize(String billId) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.APPROVED) {
            throw new IllegalStateException("Cannot finalize bill in status: " + bill.getStatus());
        }

        bill.setStatus(BillStatus.FINAL);
        bill.setFinalizedAt(OffsetDateTime.now());
        bill = billHeaderRepository.save(bill);

        TrustContext ctx = TrustContextHolder.require();
        publishEvent("BILL", billId, "BILL_FINALIZED",
                Map.of("billId", billId, "totalPayable", bill.getTotalPayable()),
                ctx.tenantId());

        return bill;
    }

    @Transactional
    public BillHeaderEntity voidBill(String billId, String reason) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        bill.setStatus(BillStatus.VOID);
        bill.setVoidedAt(OffsetDateTime.now());
        bill.setNotes(reason);
        bill = billHeaderRepository.save(bill);

        TrustContext ctx = TrustContextHolder.require();
        publishEvent("BILL", billId, "BILL_VOIDED",
                Map.of("billId", billId, "reason", reason),
                ctx.tenantId());

        return bill;
    }

    public Page<BillHeaderEntity> listBills(UUID tenantId, BillStatus status, Pageable pageable) {
        if (status != null) {
            return billHeaderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return billHeaderRepository.findByTenantId(tenantId, pageable);
    }

    public BillHeaderEntity getBill(String billId) {
        return billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));
    }

    public List<BillLineEntity> getBillLines(String billId) {
        return billLineRepository.findByBillIdAndVoidedFalse(billId);
    }

    public List<BillPartyEntity> getBillParties(String billId) {
        return billPartyRepository.findByBillId(billId);
    }

    private void recalculateTotals(BillHeaderEntity bill) {
        List<BillLineEntity> lines = billLineRepository.findByBillIdAndVoidedFalse(bill.getBillId());
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCharge = BigDecimal.ZERO;

        for (BillLineEntity line : lines) {
            totalCost = totalCost.add(line.getCostAmount());
            totalCharge = totalCharge.add(line.getAmount());
        }

        bill.setTotalCost(totalCost);
        bill.setTotalCharge(totalCharge);
        bill.setTotalPayable(totalCharge);
    }

    private void recomputePartySplit(BillHeaderEntity bill) {
        EncounterEntity encounter = bill.getEncounterId() != null
                ? encounterRepository.findById(bill.getEncounterId()).orElse(null) : null;

        String exemptionCategory = encounter != null ? encounter.getExemptionCategory() : null;
        Long insurancePlanId = encounter != null ? encounter.getInsurancePlanId() : null;

        BillSplitResult split = exemptionEngine.split(bill.getTenantId(),
                bill.getTotalCharge(), exemptionCategory, insurancePlanId, Map.of());

        bill.setTotalDiscount(split.totalDiscount());
        bill.setTotalPayable(split.totalPayable());
        bill.setPatientPayable(split.patientPayable());
        bill.setInsurerPayable(split.insurerPayable());
        bill.setSubsidyPayable(split.subsidyPayable());
        bill.setWriteOff(split.writeOff());

        try {
            bill.setTraceSummary(objectMapper.writeValueAsString(split.trace()));
        } catch (Exception e) {
            log.error("Failed to serialize split trace", e);
        }

        // Persist party allocations
        billPartyRepository.deleteByBillId(bill.getBillId());
        for (BillSplitResult.PartyAllocation alloc : split.allocations()) {
            BillPartyEntity party = new BillPartyEntity();
            party.setBillId(bill.getBillId());
            party.setPartyType(PartyType.valueOf(alloc.partyType()));
            party.setPartyRef(alloc.partyRef());
            party.setAmount(alloc.amount());
            party.setReasonCodes(alloc.reasonCodes());
            try {
                party.setTrace(objectMapper.writeValueAsString(alloc.trace()));
            } catch (Exception e) {
                log.error("Failed to serialize party trace", e);
            }
            billPartyRepository.save(party);
        }
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
