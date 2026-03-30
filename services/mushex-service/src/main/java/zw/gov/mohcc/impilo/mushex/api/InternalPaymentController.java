package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.CreateIntentRequest;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.service.PaymentIntentService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/payments")
public class InternalPaymentController {

    private final PaymentIntentService paymentIntentService;

    public InternalPaymentController(PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentIntentEntity>> createPayment(
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
}
