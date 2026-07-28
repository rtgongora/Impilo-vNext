package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityProfileSubmissionService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The facility profile submission lane (FCV-W5): a claimant prepares privately, a steward decides.
 *
 * <p>Nothing here touches legitimacy. Accepting a profile changes descriptive fields; it does not
 * verify a facility and cannot make one operational — that stays the Ministry decision rail.</p>
 */
@RestController
public class FacilityProfileSubmissionController {

    private final FacilityProfileSubmissionService submissionService;

    public FacilityProfileSubmissionController(FacilityProfileSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    public record ProposeRequest(String fieldPath, String value) {}
    public record NoteRequest(String note) {}

    /** Record a proposed value in the caller's private draft. */
    @PostMapping("/v1/internal/facilities/{facilityUuid}/profile-submission/fields")
    public ResponseEntity<ApiResponse<Map<String, Object>>> propose(
            @PathVariable UUID facilityUuid, @RequestBody ProposeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        submissionService.proposeField(facilityUuid, request.fieldPath(), request.value());
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("fieldPath", request.fieldPath(), "recorded", true), ctx.correlationId().toString()));
    }

    /** Hand the draft to a steward. */
    @PostMapping("/v1/internal/facilities/{facilityUuid}/profile-submission/submit")
    public ResponseEntity<ApiResponse<FacilityProfileSubmissionService.SubmissionView>> submit(
            @PathVariable UUID facilityUuid) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                submissionService.submit(facilityUuid), ctx.correlationId().toString()));
    }

    @PostMapping("/v1/internal/facility-profile-submissions/{submissionId}/request-correction")
    public ResponseEntity<ApiResponse<FacilityProfileSubmissionService.SubmissionView>> requestCorrection(
            @PathVariable UUID submissionId, @RequestBody NoteRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                submissionService.requestCorrection(submissionId, request.note()),
                ctx.correlationId().toString()));
    }

    @PostMapping("/v1/internal/facility-profile-submissions/{submissionId}/accept")
    public ResponseEntity<ApiResponse<FacilityProfileSubmissionService.SubmissionView>> accept(
            @PathVariable UUID submissionId, @RequestBody(required = false) NoteRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                submissionService.accept(submissionId, request == null ? null : request.note()),
                ctx.correlationId().toString()));
    }

    @PostMapping("/v1/internal/facility-profile-submissions/{submissionId}/reject")
    public ResponseEntity<ApiResponse<FacilityProfileSubmissionService.SubmissionView>> reject(
            @PathVariable UUID submissionId, @RequestBody NoteRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                submissionService.reject(submissionId, request.note()), ctx.correlationId().toString()));
    }

    /** Submissions waiting on a steward, oldest first. */
    @GetMapping("/v1/internal/facility-profile-submissions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> queue() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                submissionService.reviewQueue(), ctx.correlationId().toString()));
    }
}
