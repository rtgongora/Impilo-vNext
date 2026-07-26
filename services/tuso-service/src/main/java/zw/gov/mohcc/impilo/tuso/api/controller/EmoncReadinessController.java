package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.EmoncReadinessService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EmONC signal-function readiness — the answer to "can this facility handle an obstetric emergency",
 * given honestly.
 *
 * <p>Consumers (notably the RMNP clinical domain pack) must read
 * {@code classification == INSUFFICIENT_EVIDENCE} as "we do not know", never as "no". The
 * distinction is the whole design: an unassessed facility that could perform a caesarean, reported
 * as incapable, sends a woman with an obstructed labour past the theatre that could have helped
 * her.</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/emonc-readiness")
public class EmoncReadinessController {

    private final EmoncReadinessService readinessService;

    public EmoncReadinessController(EmoncReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    /** Current readiness for one facility, derived fresh from its latest observations. */
    @GetMapping("/{facilityId}")
    public ResponseEntity<ApiResponse<EmoncReadinessService.FacilityEmoncReadiness>> readiness(
            @PathVariable long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                readinessService.readiness(facilityId), corr(ctx)));
    }

    /**
     * Record an assessment. Append-only — a later assessment supersedes an earlier one by being
     * more recent, and nothing is overwritten.
     */
    @PostMapping("/assessments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordAssessment(
            @RequestBody EmoncReadinessService.RecordAssessmentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        UUID id = readinessService.recordAssessment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("assessmentId", id.toString()), corr(ctx)));
    }

    /**
     * Maternity-capable facilities ordered by how little is known about them. The facilities nobody
     * has assessed are the ones a referral network is most blind about, so they sort first.
     */
    @GetMapping("/worklist")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> worklist(
            @RequestParam(required = false) String province,
            @RequestParam(defaultValue = "100") int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                readinessService.assessmentWorklist(province, limit), corr(ctx)));
    }

    /** How much of the referral network is actually measured, rather than assumed. */
    @GetMapping("/coverage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> coverage() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(readinessService.coverageSummary(), corr(ctx)));
    }

    private static String corr(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
