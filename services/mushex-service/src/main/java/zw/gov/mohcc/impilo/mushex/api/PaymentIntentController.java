package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.CreateIntentRequest;
import zw.gov.mohcc.impilo.mushex.api.dto.RefundRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReceiptEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RefundEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.mushex.service.ReceiptService;
import zw.gov.mohcc.impilo.mushex.service.RefundService;
import zw.gov.mohcc.impilo.mushex.service.RemittanceService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mushex/v1/payment-intents")
public class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;
    private final RemittanceService remittanceService;
    private final RefundService refundService;
    private final ReceiptService receiptService;

    public PaymentIntentController(PaymentIntentService paymentIntentService,
                                   RemittanceService remittanceService,
                                   RefundService refundService,
                                   ReceiptService receiptService) {
        this.paymentIntentService = paymentIntentService;
        this.remittanceService = remittanceService;
        this.refundService = refundService;
        this.receiptService = receiptService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> createIntent(
            @Valid @RequestBody CreateIntentRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        SourceType sourceType = SourceType.valueOf(request.sourceType());
        UUID facilityId = request.facilityId() != null
                ? UUID.fromString(request.facilityId())
                : ctx.facilityId();

        PaymentIntentEntity intent = paymentIntentService.createIntent(
                sourceType,
                request.sourceId(),
                request.amount(),
                request.currency(),
                facilityId,
                request.idempotencyKey(),
                request.metadata()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(intent, correlationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> getIntent(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        PaymentIntentEntity intent = paymentIntentService.getIntent(id);

        return ResponseEntity.ok(ApiResponse.ok(intent, correlationId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> cancelIntent(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        PaymentIntentEntity intent = paymentIntentService.cancelIntent(id);

        return ResponseEntity.ok(ApiResponse.ok(intent, correlationId));
    }

    @PostMapping("/{id}/issue-remittance-slip")
    public ResponseEntity<ApiResponse<RemittanceTokenEntity>> issueRemittanceSlip(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        RemittanceTokenEntity token = remittanceService.issueRemittanceSlip(id);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(token, correlationId));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<RefundEntity>> refund(
            @PathVariable String id,
            @Valid @RequestBody RefundRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        RefundEntity refund = refundService.requestRefund(id, request.amount(), request.reason());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(refund, correlationId));
    }

    @GetMapping("/{id}/receipts")
    public ResponseEntity<ApiResponse<List<ReceiptEntity>>> getReceipts(@PathVariable String id) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<ReceiptEntity> receipts = receiptService.getReceiptsByIntentId(id);

        return ResponseEntity.ok(ApiResponse.ok(receipts, correlationId));
    }
}
