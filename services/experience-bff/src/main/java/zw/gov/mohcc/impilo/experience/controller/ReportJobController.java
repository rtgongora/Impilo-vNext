package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;
import zw.gov.mohcc.impilo.shared.visibility.ExportVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;

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
    private final ObjectMapper objectMapper;

    public ReportJobController(ReportingServiceClient reportingClient, ObjectMapper objectMapper) {
        this.reportingClient = reportingClient;
        this.objectMapper = objectMapper;
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
        try {
            JsonNode root = reportingClient.listTenantReportRuns(0, 200);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if (id.equals(item.path("runId").asText())) {
                        return ResponseEntity.ok(Map.of(
                                "data", mapRunToJob(item),
                                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                    }
                }
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "REPORT_JOB_NOT_FOUND", "message", "Report job not found for id " + id),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "REPORTING_UNAVAILABLE", "message", "Unable to retrieve report job while reporting-service is unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
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
            if (data == null || data.isNull()) {
                throw new IllegalStateException("reporting-service returned empty report creation payload");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Report generation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "REPORTING_UNAVAILABLE", "message", "Unable to generate report while reporting-service is unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * Run a report by key. First-class BFF proxy for the perioperative (and other) report surfaces
     * that previously reached reporting-service only via raw gateway passthrough. Fails fast at the BFF
     * when the caller's export/visibility policy forbids a row-level run (the sovereign
     * {@code ReportController} re-enforces the same guard); otherwise delegates to reporting-service.
     */
    @PostMapping("/{reportKey}/run")
    public ResponseEntity<Map<String, Object>> runReport(
            @PathVariable String reportKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {
        var visibility = VisibilityHeaderParser.resolve(httpRequest, objectMapper);
        if (ExportVisibilityGuard.deniesReportRun(visibility)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", Map.of(
                            "code", "EXPORT_VISIBILITY_DENIED",
                            "message", "Report execution is not permitted for the current data visibility and export policy.",
                            "report_key", reportKey),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode data = reportingClient.runReport(reportKey, body);
            if (data == null || data.isNull()) {
                throw new IllegalStateException("reporting-service returned empty report run payload");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Report run failed [key={}]: {}", reportKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "REPORTING_UNAVAILABLE", "message", "Unable to run report while reporting-service is unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private Map<String, Object> mapRunToJob(JsonNode it) {
        String runId = text(it, "runId");
        if (runId.isEmpty()) {
            throw new IllegalStateException("reporting-service returned report run without runId");
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("report_type", text(it, "reportKey"));
        attrs.put("status", text(it, "status").toLowerCase(java.util.Locale.ROOT));
        attrs.put("requested_by", text(it, "createdBy"));
        attrs.put("parameters", text(it, "parameters"));
        attrs.put("error_message", text(it, "errorMessage"));
        attrs.put("queued_at", text(it, "createdAt"));
        attrs.put("started_at", text(it, "startedAt"));
        attrs.put("completed_at", text(it, "completedAt"));
        return Map.of(
                "id", runId,
                "type", "report_job",
                "attributes", attrs);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return "";
        }
        return v.isTextual() ? v.asText("").trim() : v.toString().trim();
    }
}
