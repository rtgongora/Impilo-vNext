package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;

import java.util.Map;

/**
 * Mobile provider reports controller backed by Reporting service APIs.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/reports")
public class ProviderReportsController {

    private final ReportingServiceClient reportingClient;

    public ProviderReportsController(ReportingServiceClient reportingClient) {
        this.reportingClient = reportingClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listReports(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        try {
            return ResponseEntity.ok(envelope(reportingClient.listTenantReportRuns(0, 20), requestId, correlationId));
        } catch (RestClientException e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "REPORTING_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/{reportId}/data")
    public ResponseEntity<Map<String, Object>> reportData(
            @PathVariable String reportId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        try {
            return ResponseEntity.ok(envelope(reportingClient.runReport(reportId, Map.of()), requestId, correlationId));
        } catch (RestClientException e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "REPORTING_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        return Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        );
    }
}

