package zw.gov.mohcc.impilo.costa.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;
import zw.gov.mohcc.impilo.costa.service.PaymentIntegrationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

/**
 * Payments list endpoint — tenant-scoped view of all payments
 * across bills. Individual bill-scoped payment operations remain
 * on BillController.
 */
@RestController
@RequestMapping("/costa/v1/payments")
public class PaymentController {

    private final PaymentIntegrationService paymentService;

    public PaymentController(PaymentIntegrationService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PaymentEntity>>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var ctx = TrustContextHolder.require();
        Page<PaymentEntity> payments = paymentService.listPayments(
                ctx.tenantId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<PaymentEntity> paged = PagedResponse.of(
                payments.getContent(), page, size, payments.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }
}
