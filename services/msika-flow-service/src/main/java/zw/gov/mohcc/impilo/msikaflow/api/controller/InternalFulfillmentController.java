package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.core.FulfillmentService;
import zw.gov.mohcc.impilo.msikaflow.core.OrderStateMachine;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;

/**
 * Service-to-service callback surface for Nhume dispatch status updates.
 *
 * <p>Nhume (the dispatch SoR) calls this back with SYSTEM-actor trust headers as its
 * courier-side lifecycle advances (already built + committed on the Nhume side).
 * {@code /internal/v1/**} is exempted from the OAuth2 resource-server gate in
 * {@code SecurityConfig} — the caller is authenticated at the Envoy ext_authz mesh
 * edge, matching the existing {@code /internal/v1/claims/*} precedent in
 * mushex-service.</p>
 */
@RestController
@RequestMapping("/internal/v1/msika-flow/orders")
public class InternalFulfillmentController {

    private final FulfillmentService fulfillmentService;
    private final OrderStateMachine stateMachine;

    public InternalFulfillmentController(FulfillmentService fulfillmentService, OrderStateMachine stateMachine) {
        this.fulfillmentService = fulfillmentService;
        this.stateMachine = stateMachine;
    }

    public record DispatchStatusRequest(String status, String deliveryId, String proofRef) {}

    @PostMapping("/{orderId}/dispatch-status")
    public ResponseEntity<ApiResponse<Object>> dispatchStatus(@PathVariable String orderId,
                                                               @RequestBody DispatchStatusRequest req,
                                                               HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        fulfillmentService.handleDispatchStatus(orderId, req.status(), req.deliveryId(), req.proofRef(), actorId, actorType);

        OrderEntity order = stateMachine.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final String orderIdOut = order.getOrderId();
            public final String status = order.getStatus().name();
            public final String nhumeDeliveryId = req.deliveryId();
        }, correlationId));
    }
}
