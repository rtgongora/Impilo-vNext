package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin report job listing for observability and export surfaces.
 *
 * <p>GET /internal/v1/admin/reports/jobs — paginated jobs for the tenant, newest first.</p>
 *
 * @see ReportJobController for job trigger (POST /generate) and single-job detail (GET /{id})
 */
@RestController
@RequestMapping("/internal/v1/admin/reports/jobs")
public class AdminReportJobController {

    private static final Logger log = LoggerFactory.getLogger(AdminReportJobController.class);

    private final ReportingServiceClient reportingClient;

    public AdminReportJobController(ReportingServiceClient reportingClient) {
        this.reportingClient = reportingClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<Map<String, Object>> data = new ArrayList<>();
            JsonNode root = reportingClient.listTenantReportRuns(page, size);
            long totalElements = root.path("totalElements").asLong(0);
            int totalPages = root.path("totalPages").asInt(0);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode it : items) {
                    data.add(mapRunToJob(it));
                }
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.put("page", page);
            meta.put("size", size);
            meta.put("total_elements", totalElements);
            meta.put("total_pages", totalPages);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta));
        } catch (Exception e) {
            log.warn("Admin report jobs list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "REPORTING_UNAVAILABLE", "message", "Unable to list report jobs while reporting-service is unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static Map<String, Object> mapRunToJob(JsonNode it) {
        String runId = text(it, "runId");
        if (runId.isEmpty()) {
            throw new IllegalStateException("reporting-service returned report run without runId");
        }
        String reportKey = text(it, "reportKey");
        String status = text(it, "status").toLowerCase(Locale.ROOT);
        String parameters = it.has("parameters") && !it.get("parameters").isNull()
                ? it.get("parameters").asText()
                : "{}";
        String queuedAt = text(it, "createdAt");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("report_type", reportKey);
        attrs.put("status", status.isEmpty() ? "queued" : status);
        attrs.put("requested_by", text(it, "createdBy"));
        attrs.put("parameters", parameters);
        attrs.put("result_url", null);
        attrs.put("error_message", text(it, "errorMessage"));
        attrs.put("queued_at", queuedAt);
        attrs.put("started_at", text(it, "startedAt"));
        attrs.put("completed_at", text(it, "completedAt"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", runId);
        row.put("type", "report_job");
        row.put("attributes", attrs);
        return row;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText("").trim();
        }
        return v.toString().trim();
    }
}
