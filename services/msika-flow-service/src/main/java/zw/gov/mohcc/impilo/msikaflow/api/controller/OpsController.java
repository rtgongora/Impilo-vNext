package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.OpsService;
import zw.gov.mohcc.impilo.msikaflow.core.FulfillmentService;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/ops")
public class OpsController {

    private final OpsService opsService;
    private final FulfillmentService fulfillmentService;

    public OpsController(OpsService opsService, FulfillmentService fulfillmentService) {
        this.opsService = opsService;
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping("/reviews/pending")
    public ResponseEntity<ApiResponse<Page<OpsReviewEntity>>> getPendingReviews(
            Pageable pageable, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        Page<OpsReviewEntity> reviews = opsService.getPendingReviews(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(reviews, correlationId));
    }

    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<ApiResponse<OpsReviewEntity>> approveReview(
            @PathVariable String id, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OpsReviewEntity review = opsService.approveReview(id, actorId, tenantId);
        return ResponseEntity.ok(ApiResponse.ok(review, correlationId));
    }

    @PostMapping("/reviews/{id}/reject")
    public ResponseEntity<ApiResponse<OpsReviewEntity>> rejectReview(
            @PathVariable String id,
            @Valid @RequestBody(required = false) ReviewActionRequest req,
            HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        String notes = req != null ? req.notes() : null;
        OpsReviewEntity review = opsService.rejectReview(id, actorId, notes);
        return ResponseEntity.ok(ApiResponse.ok(review, correlationId));
    }

    @GetMapping("/stuck-orders")
    public ResponseEntity<ApiResponse<List<FulfillmentRouteEntity>>> getStuckOrders(HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        List<FulfillmentRouteEntity> stuck = fulfillmentService.getStuckRoutes();
        return ResponseEntity.ok(ApiResponse.ok(stuck, correlationId));
    }
}
