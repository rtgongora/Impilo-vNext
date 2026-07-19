package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AdvancedDtos.FraudFlagView;
import zw.gov.mohcc.impilo.coverage.api.dto.AdvancedDtos.ReviewFlagRequest;
import zw.gov.mohcc.impilo.coverage.core.FraudService;

import java.util.List;
import java.util.UUID;

/** Fraud, waste & abuse controls (spec §25). Automated flags -> governed review. */
@RestController
@RequestMapping("/internal/v1/coverage/fraud-flags")
public class FraudController {

    private final FraudService service;

    public FraudController(FraudService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<FraudFlagView>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.list(UUID.fromString(tenantId), status).stream().map(FraudFlagView::of).toList());
    }

    @PostMapping("/screen-claim/{claimId}")
    public ResponseEntity<List<FraudFlagView>> screen(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID claimId) {
        List<FraudFlagView> raised = service.screenClaim(UUID.fromString(tenantId), podId, claimId,
                CorrelationIds.fromHeader(correlationId)).stream().map(FraudFlagView::of).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(raised);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<FraudFlagView> review(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id,
            @Valid @RequestBody ReviewFlagRequest req) {
        return ResponseEntity.ok(FraudFlagView.of(service.review(UUID.fromString(tenantId), id,
                req.status(), req.reviewerId(), req.resolution())));
    }
}
