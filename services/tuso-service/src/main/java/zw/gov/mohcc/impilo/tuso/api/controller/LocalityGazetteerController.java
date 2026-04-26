package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.LocalityGazetteerService;

import java.util.List;
import java.util.Map;

/**
 * Tuso-backed locality gazetteer: search approved localities and manage proposal workflow.
 */
@RestController
@RequestMapping("/v1/internal/localities")
public class LocalityGazetteerController {

    private static final Logger log = LoggerFactory.getLogger(LocalityGazetteerController.class);

    private final LocalityGazetteerService gazetteerService;

    public LocalityGazetteerController(LocalityGazetteerService gazetteerService) {
        this.gazetteerService = gazetteerService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> search(
            @RequestParam String districtCode,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Locality search districtCode={} q={} correlationId={}", districtCode, q, ctx.correlationId());
        List<Map<String, Object>> rows = gazetteerService.search(districtCode, q, limit);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/proposals/pending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pending() {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = gazetteerService.listPendingProposals();
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @PostMapping("/proposals")
    public ResponseEntity<ApiResponse<Map<String, Object>>> propose(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        try {
            Map<String, Object> row = gazetteerService.propose(
                    str(body.get("districtCode")),
                    str(body.get("wardCode")),
                    str(body.get("proposedName")));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(row, ctx.correlationId().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            new ApiError(ApiError.VALIDATION_FAILED, e.getMessage(), 400),
                            ctx.correlationId().toString()));
        }
    }

    @PostMapping("/proposals/{id}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String note = body != null ? str(body.get("reviewerNote")) : null;
        try {
            Map<String, Object> row = gazetteerService.approveProposal(id, note);
            return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            new ApiError(ApiError.VALIDATION_FAILED, e.getMessage(), 400),
                            ctx.correlationId().toString()));
        }
    }

    @PostMapping("/proposals/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String note = body != null ? str(body.get("reviewerNote")) : null;
        try {
            Map<String, Object> row = gazetteerService.rejectProposal(id, note);
            return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            new ApiError(ApiError.VALIDATION_FAILED, e.getMessage(), 400),
                            ctx.correlationId().toString()));
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
