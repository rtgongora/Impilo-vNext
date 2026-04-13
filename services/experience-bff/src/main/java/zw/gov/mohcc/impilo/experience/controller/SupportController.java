package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.util.Map;

/**
 * BFF proxy for the support sovereign service.
 * Provides ticket management and knowledge-base article search.
 */
@RestController
@RequestMapping("/internal/v1/support")
public class SupportController {

    private static final Logger log = LoggerFactory.getLogger(SupportController.class);

    private final SupportServiceClient client;

    public SupportController(SupportServiceClient client) {
        this.client = client;
    }

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> listTickets(
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listTickets(status);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Support ticket list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<Map<String, Object>> getTicket(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.getTicket(id);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Support ticket get failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.createTicket(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Support ticket creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CREATE_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/tickets/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.addComment(id, body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Support comment add failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "COMMENT_FAILED", "message", e.getMessage())));
        }
    }

    @PatchMapping("/tickets/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.updateTicketStatus(id, body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Support ticket status update failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "STATUS_UPDATE_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/articles")
    public ResponseEntity<Map<String, Object>> searchArticles(
            @RequestParam(required = false) String q,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.searchArticles(q);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Support article search failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
