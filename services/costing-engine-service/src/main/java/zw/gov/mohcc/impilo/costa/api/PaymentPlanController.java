package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.finance.CreatePaymentPlanRequest;
import zw.gov.mohcc.impilo.costa.api.dto.finance.PayInstallmentRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentPlanEntity;
import zw.gov.mohcc.impilo.costa.service.PaymentPlanService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/costa/v1/finance/payment-plans")
public class PaymentPlanController {

    private final PaymentPlanService paymentPlanService;

    public PaymentPlanController(PaymentPlanService paymentPlanService) {
        this.paymentPlanService = paymentPlanService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentPlanEntity>> create(@Valid @RequestBody CreatePaymentPlanRequest body) {
        var ctx = TrustContextHolder.require();
        PaymentPlanEntity plan = paymentPlanService.createPlan(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(plan, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentPlanEntity>>> list(
            @RequestParam("patient_cpid") String patientCpid) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(paymentPlanService.listForPatient(patientCpid),
                ctx.correlationId().toString()));
    }

    @PutMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<PaymentPlanEntity>> payInstallment(
            @PathVariable("id") UUID planId,
            @RequestBody(required = false) PayInstallmentRequest body) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(paymentPlanService.recordInstallment(planId, body),
                ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentPlanEntity>> get(@PathVariable("id") UUID planId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(paymentPlanService.getPlan(planId),
                ctx.correlationId().toString()));
    }
}
