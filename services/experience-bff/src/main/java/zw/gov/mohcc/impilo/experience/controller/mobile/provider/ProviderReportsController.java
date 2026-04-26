package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gap 3 vertical: facility reports workflow (Tier-3 ops+reports) backed by a stable mobile contract.
 *
 * Note: This controller intentionally owns the mobile-facing report list + report data shape.
 * It can later delegate computation/materialization to the Reporting sovereign service without
 * changing the mobile response contract.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/reports")
public class ProviderReportsController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> listReports(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        List<Map<String, Object>> items = List.of(
                report("facility-kpis", "Facility KPI Summary", "Operational",
                        "Utilization, throughput, and site KPIs (mobile vertical).", null),
                report("clinical-quality", "Clinical Quality Snapshot", "Clinical",
                        "High-level clinical quality indicators (stubbed).", null),
                report("revenue-summary", "Revenue Summary", "Financial",
                        "Monthly revenue and expenditure overview (stubbed).", null)
        );
        return ResponseEntity.ok(envelope(items, requestId, correlationId));
    }

    @GetMapping("/{reportId}/data")
    public ResponseEntity<Map<String, Object>> reportData(
            @PathVariable String reportId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> payload;
        if ("facility-kpis".equals(reportId)) {
            payload = Map.of(
                    "report_id", reportId,
                    "report_name", "Facility KPI Summary",
                    "generated_at", now.toString(),
                    "entries", List.of(
                            entry("Outpatients today", 84, "visits"),
                            entry("Avg wait time", 37, "min"),
                            entry("Beds occupied", 61, "%")
                    )
            );
        } else if ("clinical-quality".equals(reportId)) {
            payload = Map.of(
                    "report_id", reportId,
                    "report_name", "Clinical Quality Snapshot",
                    "generated_at", now.toString(),
                    "entries", List.of(
                            entry("Protocols adherence", 92, "%"),
                            entry("Adverse events", 0, "count")
                    )
            );
        } else if ("revenue-summary".equals(reportId)) {
            payload = Map.of(
                    "report_id", reportId,
                    "report_name", "Revenue Summary",
                    "generated_at", now.toString(),
                    "entries", List.of(
                            entry("Revenue", 12500, "USD"),
                            entry("Expenditure", 7700, "USD")
                    )
            );
        } else {
            payload = Map.of(
                    "report_id", reportId,
                    "report_name", "Unknown report",
                    "generated_at", now.toString(),
                    "entries", List.of()
            );
        }

        return ResponseEntity.ok(envelope(payload, requestId, correlationId));
    }

    private static Map<String, Object> report(String id, String name, String category, String description, String lastRunAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("description", description);
        m.put("last_run_at", lastRunAt);
        return m;
    }

    private static Map<String, Object> entry(String key, Object value, String unitOrNull) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("value", value);
        if (unitOrNull != null) {
            m.put("unit", unitOrNull);
        }
        return m;
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        return Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        );
    }
}

