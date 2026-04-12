package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.InternalEligibilityCheckRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.InternalEligibilityCheckResponse;
import zw.gov.mohcc.impilo.coverage.core.InternalEligibilityCheckService;

import java.util.UUID;

/**
 * Mesh-internal eligibility check for MUSHEX and other rails (no PII beyond CPID in request).
 */
@RestController
@RequestMapping("/internal/v1/eligibility")
public class InternalEligibilityCheckController {

    private final InternalEligibilityCheckService eligibilityCheckService;

    public InternalEligibilityCheckController(InternalEligibilityCheckService eligibilityCheckService) {
        this.eligibilityCheckService = eligibilityCheckService;
    }

    @PostMapping("/check")
    public ResponseEntity<InternalEligibilityCheckResponse> check(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @Valid @RequestBody InternalEligibilityCheckRequest request) {

        UUID tenantId = UUID.fromString(tenantIdHeader);
        InternalEligibilityCheckService.EligibilityCheckOutcome outcome = eligibilityCheckService.evaluate(
                tenantId,
                request.patientCpid(),
                request.planCode(),
                request.serviceCode() != null ? request.serviceCode() : "");
        return ResponseEntity.ok(new InternalEligibilityCheckResponse(
                outcome.eligible(),
                outcome.reason(),
                outcome.utilizationLimit(),
                outcome.utilizationUsed(),
                outcome.remainingAmount()));
    }
}
