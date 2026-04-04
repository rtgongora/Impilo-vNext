package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.*;
import zw.gov.mohcc.impilo.costa.domain.entity.*;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.BillType;
import zw.gov.mohcc.impilo.costa.service.BillService;
import zw.gov.mohcc.impilo.costa.service.PaymentIntegrationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/costa/v1/bills")
public class BillController {

    private final BillService billService;
    private final PaymentIntegrationService paymentService;

    public BillController(BillService billService, PaymentIntegrationService paymentService) {
        this.billService = billService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<BillHeaderEntity>>> listBills(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {
        var ctx = TrustContextHolder.require();
        BillStatus billStatus = status != null ? BillStatus.valueOf(status) : null;
        Page<BillHeaderEntity> bills = billService.listBills(
                ctx.tenantId(), billStatus,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<BillHeaderEntity> paged = PagedResponse.of(
                bills.getContent(), page, size, bills.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @PostMapping("/draft")
    public ResponseEntity<ApiResponse<BillHeaderEntity>> createDraft(@RequestBody BillDraftRequest request) {
        var ctx = TrustContextHolder.require();
        BillType type = request.billType() != null ? request.billType() : BillType.ENCOUNTER;
        BillHeaderEntity bill = billService.createDraft(request.encounterId(), request.msikaOrderId(), type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(bill, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBill(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billService.getBill(id);
        List<BillLineEntity> lines = billService.getBillLines(id);
        List<BillPartyEntity> parties = billService.getBillParties(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bill", bill);
        result.put("lines", lines);
        result.put("parties", parties);

        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<ApiResponse<BillLineEntity>> postLine(@PathVariable String id,
                                                                 @Valid @RequestBody PostLineRequest request) {
        var ctx = TrustContextHolder.require();
        BillLineEntity line = billService.postLine(id, request.msikaCode(), request.description(),
                request.kind(), request.qty(), request.costMethod(),
                request.sourceEvent(), request.sourceRef(), request.context());

        if (line == null) {
            return ResponseEntity.ok(ApiResponse.ok(null, ctx.correlationId().toString()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(line, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/recompute")
    public ResponseEntity<ApiResponse<BillHeaderEntity>> recompute(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billService.recompute(id);
        return ResponseEntity.ok(ApiResponse.ok(bill, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/submit-approval")
    public ResponseEntity<ApiResponse<BillHeaderEntity>> submitApproval(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billService.submitForApproval(id);
        return ResponseEntity.ok(ApiResponse.ok(bill, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<BillHeaderEntity>> approve(@PathVariable String id,
                                                                  @RequestBody ApprovalRequest request) {
        var ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billService.approve(id, ctx.actorId(), request.note());
        return ResponseEntity.ok(ApiResponse.ok(bill, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ApiResponse<BillHeaderEntity>> finalizeBill(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        BillHeaderEntity bill = billService.finalize(id);
        return ResponseEntity.ok(ApiResponse.ok(bill, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/issue-invoice")
    public ResponseEntity<ApiResponse<InvoiceEntity>> issueInvoice(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        InvoiceEntity invoice = paymentService.issueInvoice(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(invoice, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/create-payment-intent")
    public ResponseEntity<ApiResponse<PaymentEntity>> createPaymentIntent(@PathVariable String id,
                                                                          @Valid @RequestBody PaymentIntentRequest request) {
        var ctx = TrustContextHolder.require();
        PaymentEntity payment = paymentService.createPaymentIntent(id, request.paymentType(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(payment, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}/refunds")
    public ResponseEntity<ApiResponse<List<RefundEntity>>> getBillRefunds(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        List<RefundEntity> refunds = paymentService.getRefundsForBill(id);
        return ResponseEntity.ok(ApiResponse.ok(refunds, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<RefundEntity>> refund(@PathVariable String id,
                                                             @Valid @RequestBody RefundRequest request) {
        var ctx = TrustContextHolder.require();
        RefundEntity refund = paymentService.createRefund(id, request.amount(),
                request.reason(), request.reasonCode(), request.refundType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(refund, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<List<PaymentEntity>>> getBillPayments(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        List<PaymentEntity> payments = paymentService.getPaymentsForBill(id);
        return ResponseEntity.ok(ApiResponse.ok(payments, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/payments/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentEntity>> cancelPayment(
            @PathVariable String id, @PathVariable Long paymentId) {
        var ctx = TrustContextHolder.require();
        PaymentEntity payment = paymentService.cancelPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.ok(payment, ctx.correlationId().toString()));
    }
}
