package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassCreateRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassReviewRequest;
import zw.gov.mohcc.impilo.tshepo.authz.service.BreakGlassService;

import java.util.List;
import java.util.UUID;

/**
 * Break-glass emergency access override endpoints.
 *
 * <p>Break-glass is used when a clinician needs emergency access to patient
 * data that would normally be denied by policy rules. The flow is:
 * <ol>
 *   <li>POST /v1/break-glass — request emergency access (with reason)</li>
 *   <li>Complete a step-up challenge</li>
 *   <li>Retry the original request with purpose=BREAK_GLASS</li>
 *   <li>A supervisor reviews the request via /v1/break-glass/review</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/v1/break-glass")
public class BreakGlassController {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassController.class);

    private final BreakGlassService breakGlassService;

    public BreakGlassController(BreakGlassService breakGlassService) {
        this.breakGlassService = breakGlassService;
    }

    /**
     * Request break-glass emergency access.
     */
    @PostMapping
    public ResponseEntity<BreakGlassResponse> requestBreakGlass(
            @Valid @RequestBody BreakGlassCreateRequest request) {
        BreakGlassResponse response = breakGlassService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List pending break-glass reviews for a tenant.
     */
    @GetMapping("/review")
    public ResponseEntity<List<BreakGlassResponse>> getPendingReviews(
            @RequestHeader("x-tenant-id") UUID tenantId) {
        List<BreakGlassResponse> reviews = breakGlassService.getPendingReviews(tenantId);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Approve or reject a break-glass request.
     */
    @PostMapping("/review/{id}")
    public ResponseEntity<BreakGlassResponse> reviewRequest(
            @PathVariable UUID id,
            @Valid @RequestBody BreakGlassReviewRequest request) {
        try {
            BreakGlassResponse response = breakGlassService.reviewRequest(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Break-glass review failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Break-glass review conflict: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
