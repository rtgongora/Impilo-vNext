package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.EligibilityV2Request;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.EligibilityV2Response;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.VerifyTokenRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.EligibilityV2Dtos.VerifyTokenResponse;
import zw.gov.mohcc.impilo.coverage.core.EligibilityTokenService;
import zw.gov.mohcc.impilo.coverage.core.EligibilityV2Service;

import java.util.UUID;

/**
 * Eligibility v2 (explainable, benefit-aware) + signed eligibility token verification (spec §12).
 */
@RestController
@RequestMapping("/internal/v1/coverage/eligibility")
public class EligibilityV2Controller {

    private final EligibilityV2Service eligibilityV2Service;
    private final EligibilityTokenService tokenService;

    public EligibilityV2Controller(EligibilityV2Service eligibilityV2Service,
                                   EligibilityTokenService tokenService) {
        this.eligibilityV2Service = eligibilityV2Service;
        this.tokenService = tokenService;
    }

    @PostMapping("/v2")
    public ResponseEntity<EligibilityV2Response> checkV2(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody EligibilityV2Request req) {
        EligibilityV2Response resp = eligibilityV2Service.check(UUID.fromString(tenantId), podId, req,
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/tokens/verify")
    public ResponseEntity<VerifyTokenResponse> verifyToken(@Valid @RequestBody VerifyTokenRequest req) {
        EligibilityTokenService.VerifiedToken v = tokenService.verify(req.token());
        return ResponseEntity.ok(new VerifyTokenResponse(v.valid(), v.reason(), v.claims()));
    }
}
