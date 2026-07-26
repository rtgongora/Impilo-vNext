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
import zw.gov.mohcc.impilo.tuso.core.ReadinessAssessmentProgrammeService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The readiness assessment programme — rounds, assignments, and the currency of the register.
 *
 * <p>The headline read is {@code GET /status}, and consumers should lead with <b>currency</b> rather
 * than coverage: a network assessed once a year has 100% coverage and a register that is correct
 * for one quarter in four.</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/readiness-programme")
public class ReadinessProgrammeController {

    private final ReadinessAssessmentProgrammeService programmeService;

    public ReadinessProgrammeController(ReadinessAssessmentProgrammeService programmeService) {
        this.programmeService = programmeService;
    }

    /** Currency vs coverage, with the gap stated in words. */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ReadinessAssessmentProgrammeService.ProgrammeStatus>> status() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(programmeService.status(), corr(ctx)));
    }

    @GetMapping("/rounds")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> rounds() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(programmeService.rounds(), corr(ctx)));
    }

    /** Open a round and generate its assignments over the maternity-capable facilities in scope. */
    @PostMapping("/rounds")
    public ResponseEntity<ApiResponse<ReadinessAssessmentProgrammeService.RoundSummary>> openRound(
            @RequestBody ReadinessAssessmentProgrammeService.OpenRoundRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(programmeService.openRound(request), corr(ctx)));
    }

    /**
     * Close a round. Outstanding assignments stay outstanding and their facilities stay UNKNOWN —
     * running out of time is not a finding about a facility.
     */
    @PostMapping("/rounds/{roundId}/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> closeRound(
            @PathVariable UUID roundId, @RequestBody(required = false) Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                programmeService.closeRound(roundId, body == null ? null : body.get("note")), corr(ctx)));
    }

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> assignments(
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                programmeService.myAssignments(assignedTo, status, limit), corr(ctx)));
    }

    @PostMapping("/assignments/{assignmentId}/assign")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assign(
            @PathVariable UUID assignmentId, @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        programmeService.assign(assignmentId, body.get("assignedTo"), body.get("assignedRole"));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("assignmentId", assignmentId.toString()), corr(ctx)));
    }

    /** Completing requires a recorded assessment; the schema refuses the claim without one. */
    @PostMapping("/assignments/{assignmentId}/complete")
    public ResponseEntity<ApiResponse<Map<String, Object>>> complete(
            @PathVariable UUID assignmentId, @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        String assessmentId = body == null ? null : body.get("assessmentId");
        programmeService.complete(assignmentId,
                assessmentId == null ? null : UUID.fromString(assessmentId));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("assignmentId", assignmentId.toString()), corr(ctx)));
    }

    /** A facility nobody could reach is a fact about the network worth recording. */
    @PostMapping("/assignments/{assignmentId}/unreachable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unreachable(
            @PathVariable UUID assignmentId, @RequestBody(required = false) Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        programmeService.markUnreachable(assignmentId, body == null ? null : body.get("reason"));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("assignmentId", assignmentId.toString()), corr(ctx)));
    }

    private static String corr(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
