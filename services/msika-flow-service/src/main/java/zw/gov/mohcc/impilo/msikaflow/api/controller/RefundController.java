package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.PaymentService;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.RefundEntity;

@RestController
@RequestMapping("/v1/orders")
public class RefundController {

    private final PaymentService paymentService;

    public RefundController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<RefundEntity>> requestRefund(
            @PathVariable String id,
            @Valid @RequestBody RefundRequest req,
            HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        RefundEntity refund = paymentService.requestRefund(id, req.amount(), req.reason(), actorId, actorType, httpReq);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(refund, correlationId));
    }
}
