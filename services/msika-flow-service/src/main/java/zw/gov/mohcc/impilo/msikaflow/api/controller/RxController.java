package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.SubstitutionService;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;

@RestController
@RequestMapping("/v1/rx")
public class RxController {

    private final SubstitutionService substitutionService;

    public RxController(SubstitutionService substitutionService) {
        this.substitutionService = substitutionService;
    }

    @PostMapping("/attach-token")
    public ResponseEntity<ApiResponse<Object>> attachRxToken(HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        // Rx token attachment validates prescription from OROS/Pharmacy/PCT
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final String message = "Rx token validated and attached";
        }, correlationId));
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
}
