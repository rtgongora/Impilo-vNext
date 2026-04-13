package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;

/**
 * CTG (fetal monitoring) — pure proxy to PCT.
 */
@RestController
@RequestMapping("/internal/v1/maternity/ctg")
public class FetalMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(FetalMonitoringController.class);

    private final PctServiceClient pctClient;

    public FetalMonitoringController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            JsonNode created = pctClient.createFetalMonitoringSession(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT createFetalMonitoringSession failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<Map<String, Object>> getActiveSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId
    ) {
        try {
            JsonNode pctData = pctClient.getActiveFetalMonitoringSession(patientId, encounterId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getActiveFetalMonitoringSession failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sessionId
    ) {
        try {
            JsonNode pctData = pctClient.getFetalMonitoringSession(sessionId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getFetalMonitoringSession failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/sessions/{sessionId}/chunks")
    public ResponseEntity<Map<String, Object>> addChunk(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            JsonNode created = pctClient.addFetalMonitoringChunk(sessionId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT addFetalMonitoringChunk failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/sessions/{sessionId}/chunks")
    public ResponseEntity<Map<String, Object>> getChunks(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sessionId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        try {
            JsonNode pctData = pctClient.getFetalMonitoringChunks(sessionId, channel, from, to);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getFetalMonitoringChunks failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/sessions/{sessionId}/annotations")
    public ResponseEntity<Map<String, Object>> addAnnotation(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            JsonNode created = pctClient.addFetalMonitoringAnnotation(sessionId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT addFetalMonitoringAnnotation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}

