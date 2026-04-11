package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IntegrationHubServiceClient;

import java.util.Map;

/**
 * BFF proxy for integration-hub-service (routes, dispatch, dead letters, mapping templates).
 */
@RestController
@RequestMapping("/internal/v1/integration-hub")
public class IntegrationHubController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationHubController.class);

    private final IntegrationHubServiceClient hubClient;

    public IntegrationHubController(IntegrationHubServiceClient hubClient) {
        this.hubClient = hubClient;
    }

    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> listRoutes(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = hubClient.listRoutes();
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub list routes failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of()));
        }
    }

    @PostMapping("/routes")
    public ResponseEntity<Map<String, Object>> upsertRoute(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = hubClient.upsertRoute(body);
            return ResponseEntity.status(201).body(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub upsert route failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", e.getMessage())));
        }
    }

    @PostMapping("/dispatch")
    public ResponseEntity<Map<String, Object>> dispatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            var upstream = hubClient.dispatch(body);
            return ResponseEntity.status(upstream.getStatusCode().value())
                    .body(Map.of("data", upstream.getBody() != null ? upstream.getBody() : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub dispatch failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", e.getMessage())));
        }
    }

    @GetMapping("/deadletters")
    public ResponseEntity<Map<String, Object>> listDeadLetters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean resolved) {
        try {
            JsonNode data = hubClient.listDeadLetters(page, size, resolved);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub list dead letters failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of()));
        }
    }

    @PostMapping("/deadletters/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayDeadLetter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = hubClient.replayDeadLetter(id);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub replay dead letter failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", e.getMessage())));
        }
    }

    @GetMapping("/mapping-templates")
    public ResponseEntity<Map<String, Object>> listMappingTemplates(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = hubClient.listMappingTemplates();
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub list mapping templates failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of()));
        }
    }

    @PostMapping("/mapping-templates")
    public ResponseEntity<Map<String, Object>> createMappingTemplate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = hubClient.createMappingTemplate(body);
            return ResponseEntity.status(201).body(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Integration hub create mapping template failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", e.getMessage())));
        }
    }
}
