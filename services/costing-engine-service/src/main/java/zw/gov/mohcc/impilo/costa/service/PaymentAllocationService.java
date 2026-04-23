package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Persists MusheX-aligned payment allocations against COSTA invoices and refreshes
 * invoice settled / outstanding amounts.
 */
@Service
public class PaymentAllocationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAllocationService.class);

    private final PaymentAllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentAllocationService(PaymentAllocationRepository allocationRepository,
                                    InvoiceRepository invoiceRepository,
                                    EventOutboxRepository outboxRepository,
                                    ObjectMapper objectMapper) {
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordFromMushexCapture(PaymentEntity payment,
                                        BillHeaderEntity bill,
                                        BigDecimal grossAmount,
                                        String mushexPaymentIntentId) {
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        InvoiceEntity invoice = invoiceRepository.findByBillId(bill.getBillId()).orElse(null);
        if (invoice == null) {
            log.debug("No invoice for bill {}; skipping payment allocation row", bill.getBillId());
            return;
        }
        if (allocationRepository.existsByCostaPaymentIdAndInvoiceId(payment.getId(), invoice.getInvoiceId())) {
            return;
        }

        PaymentAllocationEntity row = new PaymentAllocationEntity();
        row.setTenantId(bill.getTenantId());
        row.setInvoiceId(invoice.getInvoiceId());
        row.setCostaPaymentId(payment.getId());
        row.setMushexPaymentIntentId(mushexPaymentIntentId);
        row.setAllocatedAmount(grossAmount);
        row.setAllocationStatus("SETTLED");
        allocationRepository.save(row);

        BigDecimal settled = nz(invoice.getSettledAmount());
        invoice.setSettledAmount(settled.add(grossAmount));
        BigDecimal total = invoice.getTotal() != null ? invoice.getTotal() : bill.getTotalPayable();
        invoice.setTotal(total);
        invoice.setOutstandingAmount(total.subtract(nz(invoice.getSettledAmount())));
        if (invoice.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setInvoiceStatus("PAID");
        } else {
            invoice.setInvoiceStatus("PARTIALLY_PAID");
        }
        invoiceRepository.save(invoice);

        publishOutbox("PAYMENT_ALLOCATION", row.getAllocationId().toString(), "PAYMENT_ALLOCATED",
                Map.of(
                        "invoiceId", invoice.getInvoiceId(),
                        "billId", bill.getBillId(),
                        "paymentId", payment.getId(),
                        "mushexPaymentIntentId", mushexPaymentIntentId != null ? mushexPaymentIntentId : "",
                        "allocatedAmount", grossAmount
                ),
                bill.getTenantId());
    }

    /**
     * When a MusheX refund completes, unwind settled allocations (LIFO) and reopen invoice balances.
     */
    @Transactional
    public void reverseInvoiceForRefund(String billId, BigDecimal refundAmount, Long costaRefundId) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        InvoiceEntity invoice = invoiceRepository.findByBillId(billId).orElse(null);
        if (invoice == null) {
            log.debug("No invoice for bill {}; skipping refund allocation reversal", billId);
            return;
        }
        List<PaymentAllocationEntity> rows = new ArrayList<>(
                allocationRepository.findByInvoiceIdOrderByAllocatedAtAsc(invoice.getInvoiceId()));
        Collections.reverse(rows);
        BigDecimal remaining = refundAmount;
        for (PaymentAllocationEntity row : rows) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (!"SETTLED".equalsIgnoreCase(row.getAllocationStatus())) {
                continue;
            }
            BigDecimal amt = nz(row.getAllocatedAmount());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (amt.compareTo(remaining) <= 0) {
                row.setAllocationStatus("REVERSED");
                remaining = remaining.subtract(amt);
            } else {
                row.setAllocatedAmount(amt.subtract(remaining));
                remaining = BigDecimal.ZERO;
            }
            allocationRepository.save(row);
        }
        BigDecimal newSettled = allocationRepository.findByInvoiceIdOrderByAllocatedAtAsc(invoice.getInvoiceId())
                .stream()
                .filter(r -> "SETTLED".equalsIgnoreCase(r.getAllocationStatus()))
                .map(this::nzAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setSettledAmount(newSettled);
        BigDecimal total = invoice.getTotal() != null ? invoice.getTotal() : BigDecimal.ZERO;
        invoice.setOutstandingAmount(total.subtract(newSettled));
        if (invoice.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setInvoiceStatus("PAID");
        } else if (newSettled.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setInvoiceStatus("ISSUED");
        } else {
            invoice.setInvoiceStatus("PARTIALLY_PAID");
        }
        invoiceRepository.save(invoice);
        publishOutbox("INVOICE", invoice.getInvoiceId(), "INVOICE_REFUND_APPLIED",
                Map.of(
                        "invoiceId", invoice.getInvoiceId(),
                        "billId", billId,
                        "refundAmount", refundAmount,
                        "costaRefundId", costaRefundId != null ? costaRefundId : 0
                ),
                invoice.getTenantId());
        log.info("Applied refund reversal for bill {} invoice {} refundId={} amount={}",
                billId, invoice.getInvoiceId(), costaRefundId, refundAmount);
    }

    private BigDecimal nzAllocated(PaymentAllocationEntity row) {
        return nz(row.getAllocatedAmount());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType,
                               Map<String, Object> payload, java.util.UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event {}", eventType, e);
        }
    }
}
