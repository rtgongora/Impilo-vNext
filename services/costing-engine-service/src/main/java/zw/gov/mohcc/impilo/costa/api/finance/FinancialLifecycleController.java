package zw.gov.mohcc.impilo.costa.api.finance;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.costa.api.dto.CreateChargeRecordRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeRecordEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaPaymentHandoffEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceLineEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BillHeaderRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.CostaPaymentHandoffRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentAllocationRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentRepository;
import zw.gov.mohcc.impilo.costa.service.ChargeRecordService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read APIs for enriched invoices, invoice lines, and MusheX-linked payment allocations.
 */
@RestController
@RequestMapping("/costa/v1/finance/lifecycle")
public class FinancialLifecycleController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ChargeRecordRepository chargeRecordRepository;
    private final BillHeaderRepository billHeaderRepository;
    private final CostaPaymentHandoffRepository costaPaymentHandoffRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeRecordService chargeRecordService;

    public FinancialLifecycleController(InvoiceRepository invoiceRepository,
                                        InvoiceLineRepository invoiceLineRepository,
                                        PaymentAllocationRepository paymentAllocationRepository,
                                        ChargeRecordRepository chargeRecordRepository,
                                        BillHeaderRepository billHeaderRepository,
                                        CostaPaymentHandoffRepository costaPaymentHandoffRepository,
                                        PaymentRepository paymentRepository,
                                        ChargeRecordService chargeRecordService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.chargeRecordRepository = chargeRecordRepository;
        this.billHeaderRepository = billHeaderRepository;
        this.costaPaymentHandoffRepository = costaPaymentHandoffRepository;
        this.paymentRepository = paymentRepository;
        this.chargeRecordService = chargeRecordService;
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listInvoicesByEncounter(
            @RequestParam(name = "encounter_id") String encounterId) {
        var ctx = TrustContextHolder.require();
        if (encounterId == null || encounterId.isBlank()) {
            throw new IllegalArgumentException("encounter_id is required");
        }

        var bills = billHeaderRepository.findByEncounterId(encounterId).stream()
                .filter(b -> ctx.tenantId().equals(b.getTenantId()))
                .toList();

        if (bills.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(List.of(), ctx.correlationId().toString()));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> invoiceIds = new ArrayList<>();
        Map<String, String> billByInvoiceId = new HashMap<>();
        for (var bill : bills) {
            Optional<InvoiceEntity> invoiceOpt = invoiceRepository.findByBillId(bill.getBillId());
            if (invoiceOpt.isEmpty()) {
                continue;
            }
            InvoiceEntity invoice = invoiceOpt.get();
            if (!ctx.tenantId().equals(invoice.getTenantId())) {
                continue;
            }
            invoiceIds.add(invoice.getInvoiceId());
            billByInvoiceId.put(invoice.getInvoiceId(), bill.getBillId());
        }

        Map<String, CostaPaymentHandoffEntity> handoffByInvoiceId = new HashMap<>();
        if (!invoiceIds.isEmpty()) {
            for (var handoff : costaPaymentHandoffRepository.findByTenantIdAndInvoiceIdIn(ctx.tenantId(), invoiceIds)) {
                if (handoff.getInvoiceId() != null && !handoff.getInvoiceId().isBlank()) {
                    handoffByInvoiceId.putIfAbsent(handoff.getInvoiceId(), handoff);
                }
            }
        }

        for (var invoiceId : invoiceIds) {
            String billId = billByInvoiceId.get(invoiceId);
            InvoiceEntity invoice = invoiceRepository.findById(invoiceId).orElse(null);
            if (invoice == null) {
                continue;
            }
            CostaPaymentHandoffEntity handoff = handoffByInvoiceId.get(invoiceId);
            List<PaymentEntity> payments = paymentRepository.findByBillId(billId);
            PaymentEntity latestPayment = payments.stream()
                    .max(Comparator.comparing(PaymentEntity::getCreatedAt))
                    .orElse(null);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoice", invoice);
            row.put("invoice_id", invoice.getInvoiceId());
            row.put("bill_id", billId);
            row.put("encounter_id", encounterId);
            row.put("mushex_intent_id", handoff != null ? handoff.getMushexIntentId() : null);
            row.put("handoff_status", handoff != null ? handoff.getStatus() : null);
            row.put("payment_status", latestPayment != null ? latestPayment.getStatus() : null);
            row.put("paid_at", latestPayment != null ? latestPayment.getPaidAt() : null);
            rows.add(row);
        }

        rows.sort(Comparator.comparing(
                r -> ((InvoiceEntity) r.get("invoice")).getIssuedAt(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<ApiResponse<InvoiceEntity>> getInvoice(@PathVariable String invoiceId) {
        var ctx = TrustContextHolder.require();
        InvoiceEntity inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (!inv.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Invoice not found");
        }
        return ResponseEntity.ok(ApiResponse.ok(inv, ctx.correlationId().toString()));
    }

    @GetMapping("/invoices/{invoiceId}/lines")
    public ResponseEntity<ApiResponse<List<InvoiceLineEntity>>> listInvoiceLines(@PathVariable String invoiceId) {
        var ctx = TrustContextHolder.require();
        InvoiceEntity inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (!inv.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Invoice not found");
        }
        List<InvoiceLineEntity> lines = invoiceLineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
        return ResponseEntity.ok(ApiResponse.ok(lines, ctx.correlationId().toString()));
    }

    @GetMapping("/invoices/{invoiceId}/allocations")
    public ResponseEntity<ApiResponse<List<PaymentAllocationEntity>>> listAllocations(@PathVariable String invoiceId) {
        var ctx = TrustContextHolder.require();
        InvoiceEntity inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (!inv.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Invoice not found");
        }
        List<PaymentAllocationEntity> rows = paymentAllocationRepository.findByTenantIdAndInvoiceId(
                ctx.tenantId(), invoiceId);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @PostMapping("/charges")
    public ResponseEntity<ApiResponse<ChargeRecordEntity>> createCharge(@Valid @RequestBody CreateChargeRecordRequest body) {
        var ctx = TrustContextHolder.require();
        ChargeRecordEntity saved = chargeRecordService.createFromRequest(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saved, ctx.correlationId().toString()));
    }

    @GetMapping("/charges")
    public ResponseEntity<ApiResponse<List<ChargeRecordEntity>>> listChargesForBill(
            @RequestParam(required = false) String billId) {
        var ctx = TrustContextHolder.require();
        if (billId == null || billId.isBlank()) {
            throw new IllegalArgumentException("billId is required");
        }
        List<ChargeRecordEntity> rows = chargeRecordRepository.findByTenantIdAndBillIdOrderByCreatedAtDesc(
                ctx.tenantId(), billId);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }
}
