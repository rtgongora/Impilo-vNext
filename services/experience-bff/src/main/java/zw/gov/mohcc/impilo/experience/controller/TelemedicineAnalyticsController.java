package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.AnalyticsPipelineServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for telemedicine lifecycle analytics (analytics-pipeline-service).
 */
@RestController
@RequestMapping("/internal/v1/telemedicine")
public class TelemedicineAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineAnalyticsController.class);

    private final AnalyticsPipelineServiceClient analyticsClient;

    public TelemedicineAnalyticsController(AnalyticsPipelineServiceClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    @GetMapping("/sla")
    public ResponseEntity<Map<String, Object>> slaAggregates(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode payload = analyticsClient.getTelemedicineSlaAggregates(facilityId, from, to);
            return ResponseEntity.ok(Map.of(
                    "data", payload != null ? payload : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Telemedicine SLA aggregates proxy failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "ANALYTICS_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingestEvent(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode payload = analyticsClient.ingestTelemedicineEvent(body);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "data", payload != null ? payload : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Telemedicine event ingest proxy failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "ANALYTICS_INGEST_FAILED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
