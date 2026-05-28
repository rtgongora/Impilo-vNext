package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;

import java.util.Map;

/**
 * Typed registry-admin entry points for Mvumo consent orchestration.
 */
@RestController
@RequestMapping("/internal/v1/mvumo-admin")
public class MvumoAdminController {

    private static final Logger log = LoggerFactory.getLogger(MvumoAdminController.class);

    private final MvumoServiceClient mvumoClient;

    public MvumoAdminController(MvumoServiceClient mvumoClient) {
        this.mvumoClient = mvumoClient;
    }

    @GetMapping("/templates")
    public ResponseEntity<Map<String, Object>> listTemplates(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = mvumoClient.listTemplates();
            return ok(requestId, correlationId, data != null ? data : new Object[0]);
        } catch (Exception e) {
            log.warn("Mvumo template list failed: {}", e.getMessage());
            return unavailable(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/templates")
    public ResponseEntity<Map<String, Object>> createTemplate(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = mvumoClient.createTemplate(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(requestId, correlationId, data != null ? data : Map.of()));
        } catch (Exception e) {
            log.warn("Mvumo template create failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MVUMO_TEMPLATE_CREATE_FAILED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PutMapping("/templates/{templateId}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = mvumoClient.updateTemplate(templateId, body);
            return ok(requestId, correlationId, data != null ? data : Map.of());
        } catch (Exception e) {
            log.warn("Mvumo template update failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MVUMO_TEMPLATE_UPDATE_FAILED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/consent-requests")
    public ResponseEntity<Map<String, Object>> createConsentRequest(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = mvumoClient.createConsentRequest(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(requestId, correlationId, data != null ? data : Map.of()));
        } catch (Exception e) {
            log.warn("Mvumo consent request create failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MVUMO_CONSENT_REQUEST_FAILED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object data) {
        return ResponseEntity.ok(envelope(requestId, correlationId, data));
    }

    private ResponseEntity<Map<String, Object>> unavailable(String requestId, String correlationId, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "MVUMO_UNAVAILABLE", "message", message != null ? message : "Mvumo unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private Map<String, Object> envelope(String requestId, String correlationId, Object data) {
        return Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId));
    }
}
