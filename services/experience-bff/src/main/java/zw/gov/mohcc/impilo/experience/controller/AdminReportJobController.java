package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

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

    public AdminReportJobController() {
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
    throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
}
}
