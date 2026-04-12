package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.FormsServiceClient;
import zw.gov.mohcc.impilo.experience.client.RulesServiceClient;

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

    private final FormsServiceClient formsClient;
    private final RulesServiceClient rulesClient;

    public ExtensionController(FormsServiceClient formsClient, RulesServiceClient rulesClient) {
        this.formsClient = formsClient;
        this.rulesClient = rulesClient;
    }

    // ── Forms ────────────────────────────────────────────────────────

    @GetMapping("/forms")
    public ResponseEntity<Map<String, Object>> listForms(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = formsClient.listSchemas();
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Forms list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    @PostMapping("/forms")
    public ResponseEntity<Map<String, Object>> createForm(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = formsClient.createSchema(body);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @GetMapping("/forms/{formId}")
    public ResponseEntity<Map<String, Object>> getForm(
            @PathVariable String formId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = formsClient.getSchema(formId);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Form {} fetch failed: {}", formId, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    @PostMapping("/forms/{formId}/versions")
    public ResponseEntity<Map<String, Object>> createFormVersion(
            @PathVariable String formId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = formsClient.createVersion(formId, body);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/forms/{formId}/publish")
    public ResponseEntity<Map<String, Object>> publishForm(
            @PathVariable String formId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = formsClient.publishSchema(formId);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/forms/{formId}/retire")
    public ResponseEntity<Map<String, Object>> retireForm(
            @PathVariable String formId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = formsClient.retireSchema(formId);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/forms/{formId}/validate")
    public ResponseEntity<Map<String, Object>> validateFormPayload(
            @PathVariable String formId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = formsClient.validatePayload(formId, body);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    // ── Rules ────────────────────────────────────────────────────────

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> listRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = rulesClient.listRules();
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Rules list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0]));
        }
    }

    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> createRule(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = rulesClient.createRule(body);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/rules/{key}/versions")
    public ResponseEntity<Map<String, Object>> createRuleVersion(
            @PathVariable String key,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = rulesClient.createVersion(key, body);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/rules/{key}/activate")
    public ResponseEntity<Map<String, Object>> activateRule(
            @PathVariable String key,
            @RequestParam int version,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = rulesClient.activate(key, version);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/rules/{key}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateRule(
            @PathVariable String key,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        JsonNode result = rulesClient.deactivate(key);
        return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
    }

    @PostMapping("/rules/{key}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateRule(
            @PathVariable String key,
            @RequestBody JsonNode requestBody,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = rulesClient.evaluateRaw(key, requestBody);
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
        } catch (Exception e) {
            log.error("Rule {} evaluation failed: {}", key, e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "EVAL_FAILED", "message", e.getMessage())));
        }
    }
}
