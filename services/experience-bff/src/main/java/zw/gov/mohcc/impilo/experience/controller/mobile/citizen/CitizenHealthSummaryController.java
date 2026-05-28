package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.CitizenHealthSummaryService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen health summary endpoint — aggregated health overview.
 *
 * <p>GET /internal/v1/mobile/citizen/summary</p>
 */
@RestController
public class CitizenHealthSummaryController {

    private final CitizenHealthSummaryService summaryService;

    public CitizenHealthSummaryController(CitizenHealthSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/internal/v1/mobile/citizen/summary")
    public ResponseEntity<Map<String, Object>> getHealthSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", summaryService.buildSummary(actorId));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
