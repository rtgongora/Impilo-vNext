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
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceLineEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceLineRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.InvoiceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.PaymentAllocationRepository;
import zw.gov.mohcc.impilo.costa.service.ChargeRecordService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

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
    private final ChargeRecordService chargeRecordService;

    public FinancialLifecycleController(InvoiceRepository invoiceRepository,
                                        InvoiceLineRepository invoiceLineRepository,
                                        PaymentAllocationRepository paymentAllocationRepository,
                                        ChargeRecordRepository chargeRecordRepository,
                                        ChargeRecordService chargeRecordService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.chargeRecordRepository = chargeRecordRepository;
        this.chargeRecordService = chargeRecordService;
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
