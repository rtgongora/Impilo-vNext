package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.core.FulfilmentDispatchService;

/**
 * OF-B17 §12.8 — Nhume → msika-flow delivery-status write-back for marketplace
 * COMMITMENT selections (the selection twin of
 * {@link InternalFulfillmentController}'s legacy-cart order callback).
 *
 * <p>Nhume calls this with SYSTEM-actor trust headers as the courier lifecycle
 * advances (PICKED_UP / DELIVERY_ATTEMPTED / DELIVERED / RETURNED / FAILED).
 * {@code /internal/v1/**} is authenticated at the Envoy ext_authz mesh edge
 * (same precedent as the dispatch-status callback). Reception is fail-closed:
 * the reported delivery id must match the selection's dispatch_ref.</p>
 */
@RestController
@RequestMapping("/internal/v1/msika-flow/selections")
public class InternalSelectionDeliveryController {

    private final FulfilmentDispatchService fulfilmentDispatchService;

    public InternalSelectionDeliveryController(FulfilmentDispatchService fulfilmentDispatchService) {
        this.fulfilmentDispatchService = fulfilmentDispatchService;
    }

    public record SelectionDeliveryStatusRequest(String status, String deliveryId,
                                                 String proofRef, String verificationGrade) {}

    @PostMapping("/{selectionId}/delivery-status")
    public ResponseEntity<ApiResponse<Object>> deliveryStatus(@PathVariable String selectionId,
                                                              @RequestBody SelectionDeliveryStatusRequest req,
                                                              HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return fulfilmentDispatchService.onDeliveryStatus(selectionId, req.status(),
                        req.deliveryId(), req.proofRef(), req.verificationGrade(), httpReq)
                .<ResponseEntity<ApiResponse<Object>>>map(selection -> ResponseEntity.ok(
                        ApiResponse.ok(new Object() {
                            public final String selectionIdOut = selection.getSelectionId();
                            public final String deliveryStatus = selection.getDeliveryStatus();
                            public final String escrowReleaseStatus = selection.getEscrowReleaseStatus();
                        }, correlationId)))
                .orElseGet(() -> ResponseEntity.unprocessableEntity().body(ApiResponse.error(
                        "SELECTION_DELIVERY_MISMATCH",
                        "selection unknown or delivery id does not match its dispatch ref",
                        422, correlationId)));
    }
}
