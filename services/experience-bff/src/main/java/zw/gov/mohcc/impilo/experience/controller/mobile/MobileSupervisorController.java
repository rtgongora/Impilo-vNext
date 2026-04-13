package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;

/**
 * Mobile supervisor dashboard endpoints.
 * GET /internal/v1/mobile/provider/supervisor/metrics?facility_id= - facility metrics
 * GET /internal/v1/mobile/provider/supervisor/team?facility_id=    - team members
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/supervisor")
public class MobileSupervisorController {

    private final PctServiceClient pctClient;
    private final TusoServiceClient tusoClient;

    public MobileSupervisorController(PctServiceClient pctClient, TusoServiceClient tusoClient) {
        this.pctClient = pctClient;
        this.tusoClient = tusoClient;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> facilityMetrics(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        Map<String, Object> metricsData = new LinkedHashMap<>();
        metricsData.put("encounters_today", 0L);
        metricsData.put("open_encounters", 0L);
        metricsData.put("patients_seen_today", 0L);
        metricsData.put("pending_tasks", 0L);
        metricsData.put("overdue_tasks", 0L);
        metricsData.put("escalated_tasks", 0L);
        metricsData.put("pending_lab_orders", 0L);
        metricsData.put("pending_referrals", 0L);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", facilityId,
                "type", "FacilityMetrics",
                "attributes", metricsData
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/team")
    public ResponseEntity<Map<String, Object>> teamMembers(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            var shifts = tusoClient.listFacilityShifts(facilityId, "ACTIVE", page, size);
            if (shifts != null) {
                return ResponseEntity.ok(Map.of(
                        "data", shifts,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
