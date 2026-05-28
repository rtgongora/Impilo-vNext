package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.CitizenHealthSummaryService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web-facing alias for citizen health summary (same composition as mobile citizen summary).
 */
@RestController
@RequestMapping("/internal/v1/citizen")
public class CitizenHealthSummaryWebController {

    private final CitizenHealthSummaryService summaryService;

    public CitizenHealthSummaryWebController(CitizenHealthSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/health-summary")
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
