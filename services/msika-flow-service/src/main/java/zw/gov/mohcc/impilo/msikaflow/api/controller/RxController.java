package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.RxTokenService;
import zw.gov.mohcc.impilo.msikaflow.core.SubstitutionService;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;

import java.util.List;

@RestController
@RequestMapping("/v1/rx")
public class RxController {

    private final SubstitutionService substitutionService;
    private final RxTokenService rxTokenService;

    public RxController(SubstitutionService substitutionService, RxTokenService rxTokenService) {
        this.substitutionService = substitutionService;
        this.rxTokenService = rxTokenService;
    }

    /**
     * Verify a share-slip Rx token and attach it to the order. Real seam:
     * {@link RxTokenService} → share-slip-service verify. Invalid token ⇒ 422,
     * unreachable verifier ⇒ 502 — never a fabricated success.
     */
    @PostMapping("/attach-token")
    public ResponseEntity<ApiResponse<Object>> attachRxToken(
            @Valid @RequestBody AttachRxTokenRequest req, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderEntity order = rxTokenService.attachToken(req.orderId(), req.token(), actorId, actorType);

        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final String orderId = order.getOrderId();
            public final String rxTokenRef = order.getRxTokenRef();
            public final String rxTokenSubjectType = order.getRxTokenSubjectType();
        }, correlationId));
    }

    @GetMapping("/substitutions")
    public ResponseEntity<ApiResponse<List<SubstitutionView>>> listSubstitutions(
            @RequestParam String orderId,
            @RequestParam(required = false) String status,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(
                substitutionService.listSubstitutions(orderId, status), correlationId));
    }

    @PostMapping("/{orderId}/substitution/propose")
    public ResponseEntity<ApiResponse<OrderLineView>> proposeSubstitution(
            @PathVariable String orderId,
            @Valid @RequestBody SubstitutionProposeRequest req,
            HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderLineEntity line = substitutionService.proposeSubstitution(
                orderId, req.lineId(), req.substituteCode(), req.substitutePrice(), req.reason(), actorId);

        return ResponseEntity.ok(ApiResponse.ok(OrderLineView.from(line), correlationId));
    }

    @PostMapping("/{orderId}/substitution/approve")
    public ResponseEntity<ApiResponse<OrderLineView>> approveSubstitution(
            @PathVariable String orderId,
            @Valid @RequestBody SubstitutionApproveRequest req,
            HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderLineEntity line = substitutionService.approveSubstitution(orderId, req.lineId(), actorId);

        return ResponseEntity.ok(ApiResponse.ok(OrderLineView.from(line), correlationId));
    }

    @PostMapping("/{orderId}/substitution/reject")
    public ResponseEntity<ApiResponse<OrderLineView>> rejectSubstitution(
            @PathVariable String orderId,
            @Valid @RequestBody SubstitutionApproveRequest req,
            HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderLineEntity line = substitutionService.rejectSubstitution(orderId, req.lineId(), actorId, null);

        return ResponseEntity.ok(ApiResponse.ok(OrderLineView.from(line), correlationId));
    }
}
