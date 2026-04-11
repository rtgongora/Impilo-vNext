package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ExtensionServiceClient;

import java.util.Map;

/**
 * Extension Points BFF Controller (Health OS §11: Extension Points).
 *
 * <p>Proxies form definitions and business rules from forms-service and
 * rules-service. These are the governed extension points through which
 * clinical and operational modules run on top of common core services.</p>
 */
@RestController
@RequestMapping("/internal/v1/extensions")
public class ExtensionController {

    private static final Logger log = LoggerFactory.getLogger(ExtensionController.class);

    private final ExtensionServiceClient extensionClient;

    public ExtensionController(ExtensionServiceClient extensionClient) {
        this.extensionClient = extensionClient;
    }

    // ── Forms ────────────────────────────────────────────────────────

    @GetMapping("/forms")
    public ResponseEntity<Map<String, Object>> listForms(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode result = extensionClient.listForms(tenantId, page, size);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Forms list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    @GetMapping("/forms/{formId}")
    public ResponseEntity<Map<String, Object>> getForm(
            @PathVariable String formId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = extensionClient.getForm(formId);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Form {} fetch failed: {}", formId, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    // ── Rules ────────────────────────────────────────────────────────

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> listRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode result = extensionClient.listRules(tenantId, page, size);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Rules list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    @PostMapping("/rules/{ruleId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateRule(
            @PathVariable String ruleId,
            @RequestBody String factsJson,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = extensionClient.evaluateRule(ruleId, factsJson);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Rule {} evaluation failed: {}", ruleId, e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "EVAL_FAILED", "message", e.getMessage())));
        }
    }
}
