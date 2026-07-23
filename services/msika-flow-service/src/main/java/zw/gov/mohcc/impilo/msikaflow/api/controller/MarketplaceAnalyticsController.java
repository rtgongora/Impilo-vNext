package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.core.analytics.AnomalyFindingService;
import zw.gov.mohcc.impilo.msikaflow.core.analytics.MarketplaceAnalyticsSweeper;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.AnomalyFindingEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * OF-B29 (Wave OF-D) — marketplace-operations read + review surface for
 * anomaly/fairness findings (Vol II §13.7 worklists, §21.5 named operational
 * duty). Trust rides the platform spine: Envoy ext_authz → TSHEPO in front,
 * tenant + actor from trust headers here; review/dismiss are reason-bound and
 * stamp the accountable reviewer.
 */
@RestController
@RequestMapping("/v1/marketplace-analytics")
public class MarketplaceAnalyticsController {

    private final AnomalyFindingService findingService;
    private final MarketplaceAnalyticsSweeper sweeper;

    public MarketplaceAnalyticsController(AnomalyFindingService findingService,
                                          MarketplaceAnalyticsSweeper sweeper) {
        this.findingService = findingService;
        this.sweeper = sweeper;
    }

    public record ReviewRequest(String reason) {}

    @GetMapping("/findings")
    public ResponseEntity<ApiResponse<Page<AnomalyFindingEntity>>> listFindings(
            @RequestParam(required = false) AnomalyFindingStatus status,
            @RequestParam(required = false) AnomalyFindingType type,
            Pageable pageable, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(
                findingService.list(tenantId, status, type, pageable), correlationId));
    }

    @GetMapping("/findings/{id}")
    public ResponseEntity<ApiResponse<AnomalyFindingEntity>> getFinding(
            @PathVariable String id, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        try {
            return ResponseEntity.ok(ApiResponse.ok(findingService.get(id, tenantId), correlationId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "FINDING_NOT_FOUND", e.getMessage(), 404, correlationId));
        }
    }

    /** OPEN → REVIEWED (reason-bound, reviewer stamped from X-Actor-ID). */
    @PostMapping("/findings/{id}/review")
    public ResponseEntity<ApiResponse<AnomalyFindingEntity>> reviewFinding(
            @PathVariable String id,
            @RequestBody(required = false) ReviewRequest req,
            HttpServletRequest httpReq) {
        return transition(id, req, httpReq, true);
    }

    /** OPEN|REVIEWED → DISMISSED (terminal; reason-bound). */
    @PostMapping("/findings/{id}/dismiss")
    public ResponseEntity<ApiResponse<AnomalyFindingEntity>> dismissFinding(
            @PathVariable String id,
            @RequestBody(required = false) ReviewRequest req,
            HttpServletRequest httpReq) {
        return transition(id, req, httpReq, false);
    }

    /**
     * Ops manual trigger — runs all monitors immediately and returns per-monitor
     * counts (dedup makes re-runs safe). The scheduled sweep remains the
     * steady-state path.
     */
    @PostMapping("/sweeps/run")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> runSweep(HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(sweeper.runOnce(OffsetDateTime.now()), correlationId));
    }

    private ResponseEntity<ApiResponse<AnomalyFindingEntity>> transition(
            String id, ReviewRequest req, HttpServletRequest httpReq, boolean review) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        String reason = req != null ? req.reason() : null;
        try {
            AnomalyFindingEntity finding = review
                    ? findingService.review(id, tenantId, actorId, reason)
                    : findingService.dismiss(id, tenantId, actorId, reason);
            return ResponseEntity.ok(ApiResponse.ok(finding, correlationId));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("FINDING_NOT_FOUND")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(ApiResponse.error(
                    errorCode(e), e.getMessage(), status.value(), correlationId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    errorCode(e), e.getMessage(), 409, correlationId));
        }
    }

    private static String errorCode(Exception e) {
        String message = e.getMessage();
        if (message == null || message.indexOf(':') < 0) {
            return "FINDING_TRANSITION_FAILED";
        }
        return message.substring(0, message.indexOf(':'));
    }
}
