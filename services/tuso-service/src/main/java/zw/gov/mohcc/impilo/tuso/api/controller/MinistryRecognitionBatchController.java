package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.MinistryRecognitionBatchService;

import java.util.Map;
import java.util.UUID;

/**
 * The governed Ministry recognition batch (FCV-W3) — how the facilities already on the national
 * master list get the Ministry verdict the seed migration deliberately left to a governed process.
 *
 * <p>Dry run first by default: the counts are the preview an administrator approves before anything
 * is written, and every run is reversible by batch.</p>
 */
@RestController
@RequestMapping("/v1/internal/facility-registry/ministry-recognition")
public class MinistryRecognitionBatchController {

    private final MinistryRecognitionBatchService batchService;

    public MinistryRecognitionBatchController(MinistryRecognitionBatchService batchService) {
        this.batchService = batchService;
    }

    public record AssertRequest(String sourceLabel, String reason, Integer limit, Boolean dryRun) {}

    public record RevertRequest(String reason) {}

    @PostMapping
    public ResponseEntity<ApiResponse<MinistryRecognitionBatchService.BatchResult>> assertRecognition(
            @RequestBody AssertRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        // Defaults to a dry run: a bulk assertion is opt-in, never the accident of a missing flag.
        boolean dryRun = request.dryRun() == null || request.dryRun();
        int limit = request.limit() == null ? 100 : request.limit();
        return ResponseEntity.ok(ApiResponse.ok(
                batchService.assertRecognition(request.sourceLabel(), request.reason(), limit, dryRun),
                ctx.correlationId().toString()));
    }

    @PostMapping("/{batchId}/revert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revert(
            @PathVariable UUID batchId, @RequestBody(required = false) RevertRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        int removed = batchService.revert(batchId, request == null ? null : request.reason());
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("batchId", batchId, "legitimacyRowsRemoved", removed),
                ctx.correlationId().toString()));
    }

    /** How many master-list facilities still carry no verdict at all. */
    @GetMapping("/remaining")
    public ResponseEntity<ApiResponse<Map<String, Object>>> remaining() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("remainingUnrecognised", batchService.countRemaining(ctx.tenantId())),
                ctx.correlationId().toString()));
    }
}
