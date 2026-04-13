package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.Map;

/**
 * Endpoint 3: Edge-function-like job trigger — POST /internal/v1/reports/generate
 * Enqueues a report generation job and writes an outbox event.
 *
 * @see AdminReportJobController for paginated admin job listing (GET /admin/reports/jobs)
 */
@RestController
@RequestMapping("/internal/v1/reports")
public class ReportJobController {

    public record GenerateReportRequest(
            @NotBlank String report_type,
            Map<String, Object> parameters,
            @NotBlank String requested_by
    ) {}

    /**
     * Tenant-scoped job detail — same resource shape as admin list rows.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getJob(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GenerateReportRequest request) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }
}
