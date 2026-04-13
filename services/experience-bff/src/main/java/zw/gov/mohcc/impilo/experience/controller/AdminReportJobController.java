package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
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
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "page", page, "size", size)));
    }
}
