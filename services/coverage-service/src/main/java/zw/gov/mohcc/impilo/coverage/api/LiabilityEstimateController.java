package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.EstimateRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.EstimateView;
import zw.gov.mohcc.impilo.coverage.core.LiabilityEstimateService;

import java.util.List;
import java.util.UUID;

/** Patient-liability estimation (spec §18). */
@RestController
@RequestMapping("/internal/v1/coverage/liability-estimates")
public class LiabilityEstimateController {

    private final LiabilityEstimateService service;

    public LiabilityEstimateController(LiabilityEstimateService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<EstimateView>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(service.forMember(tid, memberCpid).stream().map(EstimateView::of).toList());
    }

    @PostMapping
    public ResponseEntity<EstimateView> estimate(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody EstimateRequest req) {
        UUID tid = UUID.fromString(tenantId);
        EstimateView view = EstimateView.of(service.estimate(tid, podId, req.coverageId(), req.benefitCode(),
                req.medicationCode(), req.codingSystem(),
                req.standardCharge(), req.facilityId(), CorrelationIds.fromHeader(correlationId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }
}
