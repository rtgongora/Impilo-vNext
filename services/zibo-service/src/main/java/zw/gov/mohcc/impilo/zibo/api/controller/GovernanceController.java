package zw.gov.mohcc.impilo.zibo.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.zibo.core.GovernanceService;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for terminology governance analytics.
 *
 * <p>Provides endpoints for querying validation logs, top failures,
 * and aggregate validation statistics.</p>
 */
@RestController
@RequestMapping("/v1/governance")
public class GovernanceController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceController.class);

    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * Get paginated validation logs with optional filters.
     */
    @GetMapping("/validation-logs")
    public ResponseEntity<ApiResponse<PagedResponse<ValidationLogEntity>>> getValidationLogs(
            @RequestParam(required = false) ValidationSeverity severity,
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<ValidationLogEntity> result = governanceService.getValidationLogs(
                severity, facilityId, PageRequest.of(page, size));

        PagedResponse<ValidationLogEntity> paged = PagedResponse.of(
                result.getContent(), page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    /**
     * Get the most frequent validation failures.
     */
    @GetMapping("/top-failures")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopFailures(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) OffsetDateTime since) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusDays(30);

        List<Map<String, Object>> failures = governanceService.getTopValidationFailures(
                topN, effectiveSince);

        return ResponseEntity.ok(ApiResponse.ok(failures, correlationId));
    }

    /**
     * Get validation statistics aggregated by severity.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getValidationStats(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) OffsetDateTime since) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusDays(30);

        Map<String, Long> stats = governanceService.getValidationStats(facilityId, effectiveSince);

        return ResponseEntity.ok(ApiResponse.ok(stats, correlationId));
    }
}
