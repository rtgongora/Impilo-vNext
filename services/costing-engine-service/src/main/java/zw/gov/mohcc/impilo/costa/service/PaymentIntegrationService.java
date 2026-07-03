package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.*;
import zw.gov.mohcc.impilo.costa.domain.repository.BillHeaderRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BillLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ClaimPackRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EncounterRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.RefundRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntegrationService.class);

    private final BillHeaderRepository billHeaderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClaimPackRepository claimPackRepository;
    private final EventOutboxRepository outboxRepository;
    private final ReceivablesService receivablesService;
    private final ObjectMapper objectMapper;
    private final EncounterRepository encounterRepository;
    private final PatientAccountService patientAccountService;
    private final BillLineRepository billLineRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentAllocationService paymentAllocationService;

    private final zw.gov.mohcc.impilo.costa.integration.MushexPaymentIntentClient mushexPaymentIntentClient;

    public PaymentIntegrationService(BillHeaderRepository billHeaderRepository,
                                     EncounterRepository encounterRepository,
                                     PaymentRepository paymentRepository,
                                     RefundRepository refundRepository,
                                     InvoiceRepository invoiceRepository,
                                     ClaimPackRepository claimPackRepository,
                                     EventOutboxRepository outboxRepository,
                                     ReceivablesService receivablesService,
                                     ObjectMapper objectMapper,
                                     PatientAccountService patientAccountService,
                                     BillLineRepository billLineRepository,
                                     InvoiceLineRepository invoiceLineRepository,
                                     PaymentAllocationService paymentAllocationService,
            zw.gov.mohcc.impilo.costa.integration.MushexPaymentIntentClient mushexPaymentIntentClient) {
        this.billHeaderRepository = billHeaderRepository;
        this.encounterRepository = encounterRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.invoiceRepository = invoiceRepository;
        this.claimPackRepository = claimPackRepository;
        this.outboxRepository = outboxRepository;
        this.receivablesService = receivablesService;
        this.objectMapper = objectMapper;
        this.patientAccountService = patientAccountService;
        this.billLineRepository = billLineRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.paymentAllocationService = paymentAllocationService;
        this.mushexPaymentIntentClient = mushexPaymentIntentClient;
    }

    public List<PaymentEntity> getPaymentsForBill(String billId) {
        return paymentRepository.findByBillId(billId);
    }

    public Page<PaymentEntity> listPayments(UUID tenantId, Pageable pageable) {
        return paymentRepository.findByTenantId(tenantId, pageable);
    }

    public List<RefundEntity> getRefundsForBill(String billId) {
        return refundRepository.findByBillId(billId);
    }

    @Transactional
    public InvoiceEntity issueInvoice(String billId) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        if (bill.getStatus() != BillStatus.FINAL) {
            throw new IllegalStateException("Cannot issue invoice for non-final bill: " + bill.getStatus());
        }

        String invoiceNumber = "INV-" + bill.getFacilityId().toString().substring(0, 8).toUpperCase()
                + "-" + System.currentTimeMillis();

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setInvoiceId(UlidGenerator.generate());
        invoice.setBillId(billId);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTenantId(bill.getTenantId());
        invoice.setCurrency(bill.getCurrency());
        invoice.setClientRef(getPatientCpid(bill));
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setInvoiceStatus("ISSUED");
        invoice.setSettledAmount(BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        List<BillLineEntity> billLines = billLineRepository.findByBillIdAndVoidedFalse(billId);
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BillLineEntity bl : billLines) {
            InvoiceLineEntity line = new InvoiceLineEntity();
            line.setLineId(UlidGenerator.generate());
            line.setInvoiceId(invoice.getInvoiceId());
            line.setBillLineId(bl.getLineId());
            line.setDescription(bl.getDescription());
            line.setQuantity(bl.getQty());
            line.setUnitPrice(bl.getUnitPrice());
            line.setLineAmount(bl.getAmount());
            invoiceLineRepository.save(line);
            subtotal = subtotal.add(bl.getAmount());
        }

        BigDecimal total = bill.getTotalPayable() != null ? bill.getTotalPayable() : subtotal;
        invoice.setSubtotal(subtotal);
        invoice.setTotal(total);
        invoice.setOutstandingAmount(total);
        invoice = invoiceRepository.save(invoice);

        receivablesService.attachInvoice(bill.getTenantId(), billId, invoice.getInvoiceId());

        TrustContext ctx = TrustContextHolder.require();
        publishEvent("INVOICE", invoice.getInvoiceId(), "INVOICE_ISSUED",
                Map.of("invoiceId", invoice.getInvoiceId(), "billId", billId,
                        "invoiceNumber", invoiceNumber, "amount", total),
                ctx.tenantId());

        return invoice;
    }

    /**
     * Cancel a PENDING payment. Only payments in PENDING status can be cancelled.
     */
    @Transactional
    public PaymentEntity cancelPayment(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot cancel payment in status: " + payment.getStatus() + ". Only PENDING payments can be cancelled.");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        BillHeaderEntity bill = billHeaderRepository.findById(payment.getBillId()).orElse(null);
        UUID tenantId = bill != null ? bill.getTenantId() : null;

        if (tenantId != null) {
            publishEvent("PAYMENT", payment.getId().toString(), "PAYMENT_CANCELLED",
                    Map.of("billId", payment.getBillId(), "paymentId", payment.getId(),
                            "amount", payment.getAmount()),
                    tenantId);
        }

        log.info("Payment {} cancelled for bill {}", paymentId, payment.getBillId());
        return payment;
    }

    @Transactional
    public PaymentEntity createPaymentIntent(String billId, PaymentType paymentType, BigDecimal amount) {
        return createPaymentIntent(billId, paymentType, amount, null);
    }

    /**
     * Creates the COSTA payment record AND the corresponding MusheX payment
     * intent in one step, persisting the intent id so MusheX status events
     * (mushex.payment.status.changed) can find their payment — without the
     * linkage the status consumer's lookup always missed and the money loop
     * never closed.
     */
    @Transactional
    public PaymentEntity createPaymentIntent(String billId, PaymentType paymentType, BigDecimal amount,
                                             jakarta.servlet.http.HttpServletRequest inboundRequest) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        PaymentEntity payment = new PaymentEntity();
        payment.setBillId(billId);
        payment.setPaymentType(paymentType);
        payment.setAmount(amount);
        payment.setCurrency(bill.getCurrency());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        // Sovereign rail handoff — fail loud: a payment record without a rail
        // intent is exactly the dormant state this integration replaces.
        String metadata = "{\"costa_payment_id\":\"" + payment.getId()
                + "\",\"patient_cpid\":\"" + String.valueOf(getPatientCpid(bill)) + "\"}";
        zw.gov.mohcc.impilo.costa.integration.MushexPaymentIntentClient.MushexIntentCreated intent = mushexPaymentIntentClient.createPaymentIntent(
                inboundRequest,
                "COSTA_BILL",
                billId,
                amount.toPlainString(),
                bill.getCurrency(),
                bill.getFacilityId() != null ? bill.getFacilityId().toString() : null,
                "COSTA_PAYMENT:" + payment.getId(),
                metadata);
        payment.setMushexPaymentIntentId(intent.intentId());
        payment = paymentRepository.save(payment);

        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "COSTA_PAYMENT_INTENT:" + payment.getId());
        payload.put("tenantId", ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        payload.put("billId", billId);
        payload.put("paymentId", payment.getId());
        payload.put("mushexIntentId", intent.intentId());
        payload.put("amount", amount);
        payload.put("currency", bill.getCurrency());
        payload.put("paymentType", paymentType.name());
        payload.put("facilityId", bill.getFacilityId() != null ? bill.getFacilityId().toString() : null);
        payload.put("patientCpid", getPatientCpid(bill));

        publishEvent("PAYMENT", payment.getId().toString(), "PAYMENT_INTENT_CREATED", payload, ctx.tenantId());

        return payment;
    }

    @Transactional
    public void handlePaymentStatusUpdate(String mushexPaymentIntentId, String mushexToStatus, BigDecimal paidAmount) {
        PaymentEntity payment = paymentRepository.findByMushexPaymentIntentId(mushexPaymentIntentId)
                .orElse(null);

        if (payment == null) {
            log.warn("Payment not found for MUSHEX intent: {}", mushexPaymentIntentId);
            return;
        }

        Optional<PaymentStatus> mapped = mapMushexIntentStatus(mushexToStatus);
        if (mapped.isEmpty()) {
            log.debug("No COSTA payment status mapping for MusheX intent status {}", mushexToStatus);
            return;
        }

        PaymentStatus newStatus = mapped.get();
        PaymentStatus previous = payment.getStatus();

        payment.setStatus(newStatus);
        if (paidAmount != null) {
            payment.setPaidAmount(paidAmount);
        } else if (newStatus == PaymentStatus.PAID && payment.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
            payment.setPaidAmount(payment.getAmount());
        }
        if (newStatus == PaymentStatus.PAID) {
            payment.setPaidAt(OffsetDateTime.now());
        }
        paymentRepository.save(payment);

        BillHeaderEntity bill = billHeaderRepository.findById(payment.getBillId()).orElse(null);
        if (bill != null) {
            BigDecimal applied = paidAmount != null ? paidAmount : payment.getPaidAmount();
            if (newStatus == PaymentStatus.PAID && applied != null && previous != PaymentStatus.PAID) {
                receivablesService.applyPaymentToBill(bill, applied);
                paymentAllocationService.recordFromMushexCapture(payment, bill, applied, mushexPaymentIntentId);
            }
            publishEvent("PAYMENT", payment.getId().toString(), "PAYMENT_STATUS_CHANGED",
                    Map.of("billId", payment.getBillId(), "status", newStatus.name(), "paidAmount", applied),
                    bill.getTenantId());
            if (newStatus == PaymentStatus.PAID && previous != PaymentStatus.PAID) {
                try {
                    patientAccountService.recordPatientPaymentCaptured(payment, bill);
                } catch (Exception e) {
                    log.warn("Patient account payment not recorded for payment {}: {}",
                            payment.getId(), e.getMessage());
                }
            }
        }
    }

    /** Maps MusheX payment intent status names (e.g. PAID, AUTHORIZED) to COSTA {@link PaymentStatus}. */
    static Optional<PaymentStatus> mapMushexIntentStatus(String mushexStatus) {
        if (mushexStatus == null || mushexStatus.isBlank()) {
            return Optional.empty();
        }
        return switch (mushexStatus) {
            case "PAID" -> Optional.of(PaymentStatus.PAID);
            case "FAILED" -> Optional.of(PaymentStatus.FAILED);
            case "CANCELLED" -> Optional.of(PaymentStatus.CANCELLED);
            case "CREATED", "PENDING", "AUTHORIZED" -> Optional.of(PaymentStatus.PENDING);
            default -> Optional.empty();
        };
    }

    @Transactional
    public RefundEntity createRefund(String billId, BigDecimal amount, String reason,
                                     String reasonCode, String refundType) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        // Validate that at least one PAID payment exists for this bill
        List<PaymentEntity> payments = paymentRepository.findByBillId(billId);
        BigDecimal totalPaid = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(PaymentEntity::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Cannot create refund for bill " + billId + ": no PAID payments exist");
        }

        // Validate refund amount against refundable balance
        List<RefundEntity> existingRefunds = refundRepository.findByBillId(billId);
        BigDecimal alreadyRefunded = existingRefunds.stream()
                .filter(r -> r.getStatus() != RefundStatus.FAILED)
                .map(RefundEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundableBalance = totalPaid.subtract(alreadyRefunded);
        if (amount.compareTo(refundableBalance) > 0) {
            throw new IllegalStateException(
                    "Refund amount " + amount + " exceeds refundable balance " + refundableBalance
                    + " for bill " + billId);
        }

        TrustContext ctx = TrustContextHolder.require();

        RefundEntity refund = new RefundEntity();
        refund.setBillId(billId);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setReasonCode(reasonCode);
        refund.setRefundType(refundType != null ? refundType : "PARTIAL");
        refund.setApproverActorId(ctx.actorId());
        refund.setStatus(RefundStatus.PENDING);
        refund = refundRepository.save(refund);

        publishEvent("REFUND", refund.getId().toString(), "REFUND_CREATED",
                Map.of("billId", billId, "refundId", refund.getId(),
                        "amount", amount, "reason", reason),
                ctx.tenantId());

        return refund;
    }

    @Transactional
    public ClaimPackEntity createClaimPack(String billId, String insurerRef) {
        BillHeaderEntity bill = billHeaderRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found: " + billId));

        Map<String, Object> claimPayload = new LinkedHashMap<>();
        claimPayload.put("billId", billId);
        claimPayload.put("totalCharge", bill.getTotalCharge());
        claimPayload.put("insurerPayable", bill.getInsurerPayable());
        claimPayload.put("facilityId", bill.getFacilityId());

        ClaimPackEntity claim = new ClaimPackEntity();
        claim.setBillId(billId);
        claim.setInsurerRef(insurerRef);
        claim.setClaimType("INITIAL");
        try {
            claim.setPayload(objectMapper.writeValueAsString(claimPayload));
        } catch (Exception e) {
            claim.setPayload("{}");
        }
        claim.setStatus(ClaimPackStatus.PENDING);
        claim = claimPackRepository.save(claim);

        TrustContext ctx = TrustContextHolder.require();
        publishEvent("CLAIM", claim.getId().toString(), "CLAIM_PACK_CREATED",
                Map.of("billId", billId, "claimId", claim.getId(), "insurerRef", insurerRef),
                ctx.tenantId());

        return claim;
    }

    private String getPatientCpid(BillHeaderEntity bill) {
        if (bill.getEncounterId() == null) {
            return "";
        }
        return encounterRepository.findById(bill.getEncounterId())
                .map(EncounterEntity::getPatientCpid)
                .orElse("");
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
