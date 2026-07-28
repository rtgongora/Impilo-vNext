package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityLegitimacyDecisionService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ministry legitimacy verdict (FCV-W1) — the governed act that decides whether a facility may
 * operate on the platform, and the only path that may write the {@code PLATFORM_OPERATIONAL} allow.
 *
 * <p>Authority is enforced in the service against an ACTIVE Ministry verifier appointment covering
 * the facility, so this controller deliberately carries no header check of its own — there is no
 * second, weaker way in.</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityUuid}/legitimacy-decisions")
public class FacilityLegitimacyDecisionController {

    private final FacilityLegitimacyDecisionService decisionService;

    public FacilityLegitimacyDecisionController(FacilityLegitimacyDecisionService decisionService) {
        this.decisionService = decisionService;
    }

    /** Issue a verdict. Supersedes the standing decision; the history is append-only. */
    @PostMapping
    public ResponseEntity<ApiResponse<FacilityLegitimacyDecisionService.DecisionOutcome>> issue(
            @PathVariable UUID facilityUuid,
            @RequestBody FacilityLegitimacyDecisionService.IssueDecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityLegitimacyDecisionService.DecisionOutcome outcome =
                decisionService.issue(facilityUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(outcome, ctx.correlationId().toString()));
    }

    /** Every decision ever made for this facility, newest first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(@PathVariable UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                decisionService.history(facilityUuid), ctx.correlationId().toString()));
    }
}
