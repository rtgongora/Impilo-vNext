package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.EligibilityCheckRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.EligibilityResult;
import zw.gov.mohcc.impilo.varapi.core.EligibilityService;

/**
 * Internal REST API for provider eligibility checks within the VARAPI Provider Registry.
 *
 * Used by downstream services (e.g., TUSO, clinical services) to verify
 * whether a provider is eligible to practice at a given facility/workspace.
 *
 * CRITICAL: All responses must be GENERIC — same timing and shape whether
 * the provider exists or not (enumeration resistance). This prevents
 * attackers from probing which provider IDs are valid.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/eligibility")
public class EligibilityController {

    private static final Logger log = LoggerFactory.getLogger(EligibilityController.class);

    private final EligibilityService eligibilityService;

    public EligibilityController(EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<EligibilityResult>> checkEligibility(
            @Valid @RequestBody EligibilityCheckRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Checking eligibility [facilityId={}, workspaceId={}] correlationId={}",
                request.facilityId(), request.workspaceId(), ctx.correlationId());

        // Response is always GENERIC — same structure and timing regardless of
        // whether the provider exists or not, to prevent enumeration attacks.
        EligibilityResult result = eligibilityService.checkEligibility(request);

        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }
}
