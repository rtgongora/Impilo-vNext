package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

/**
 * Partograph — pure proxy to PCT.
 */
@RestController
@RequestMapping("/internal/v1/maternity/partograph")
public class MaternityPartographController {

    private static final Logger log = LoggerFactory.getLogger(MaternityPartographController.class);

    private final PctServiceClient pctClient;

    public MaternityPartographController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            JsonNode created = pctClient.createPartographSession(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT createPartographSession failed: {}", e.getMessage());
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
            JsonNode pctData = pctClient.getActivePartographSession(patientId, encounterId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getActivePartographSession failed: {}", e.getMessage());
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
            JsonNode pctData = pctClient.getPartographSession(sessionId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getPartographSession failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/sessions/{sessionId}/points")
    public ResponseEntity<Map<String, Object>> addPoint(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            JsonNode created = pctClient.addPartographPoint(sessionId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT addPartographPoint failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PatchMapping("/sessions/{sessionId}/close")
    public ResponseEntity<Map<String, Object>> closeSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        try {
            JsonNode closed = pctClient.closePartographSession(sessionId, request != null ? request : Map.of());
            return ResponseEntity.ok(Map.of(
                    "data", closed != null ? closed : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT closePartographSession failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}

