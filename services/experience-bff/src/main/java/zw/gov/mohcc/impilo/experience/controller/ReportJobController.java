package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint 3: Edge-function-like job trigger — POST /internal/v1/reports/generate
 * Delegates to the Reporting sovereign service.
 *
 * @see AdminReportJobController for paginated admin job listing (GET /admin/reports/jobs)
 */
@RestController
@RequestMapping("/internal/v1/reports")
public class ReportJobController {

    private static final Logger log = LoggerFactory.getLogger(ReportJobController.class);

    private final ReportingServiceClient reportingClient;

    public ReportJobController(ReportingServiceClient reportingClient) {
        this.reportingClient = reportingClient;
    }

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
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GenerateReportRequest request) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reportKey", request.report_type());
            body.put("parameters", request.parameters());
            body.put("requestedBy", request.requested_by());
            JsonNode data = reportingClient.createReport(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of("type", "ReportJob", "attributes", Map.of("status", "QUEUED")),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Report generation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "REPORT_GENERATION_FAILED", "message", e.getMessage())));
        }
    }
}
