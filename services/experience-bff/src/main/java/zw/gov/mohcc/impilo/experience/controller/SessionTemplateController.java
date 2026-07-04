package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.sessiontemplates.SessionMode;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplate;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only session-template doctrine for experience surfaces: which roles a
 * mode allows, join-flow/lobby behaviour, recording rules, fallback rules.
 * Templates are versioned code artifacts (no tenant data) served straight from
 * the registry — the same documents rtc-gateway enforces token grants with.
 */
@RestController
@RequestMapping("/internal/v1/session-templates")
public class SessionTemplateController {

    private final SessionTemplateRegistry registry;

    public SessionTemplateController(SessionTemplateRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        List<String> modes = registry.modes().stream().map(SessionMode::name).sorted().toList();
        return ok(requestId, correlationId, Map.of("modes", modes));
    }

    @GetMapping("/{mode}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String mode,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        SessionTemplate template = registry.find(mode);
        if (template == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of(
                            "code", "SESSION_MODE_UNKNOWN",
                            "message", "Unknown session mode: " + mode),
                    "meta", meta(requestId, correlationId)));
        }
        return ok(requestId, correlationId, template);
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("type", "session-template", "attributes", attributes));
        response.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(response);
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of(
                "request_id", requestId != null ? requestId : UUID.randomUUID().toString(),
                "correlation_id", correlationId != null ? correlationId : UUID.randomUUID().toString());
    }
}
