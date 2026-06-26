package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.ConfigService;
import zw.gov.mohcc.impilo.rito.persistence.entity.EscalationRuleEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.SlaPolicyEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito/config")
public class RitoConfigController {

    private final ConfigService configService;

    public RitoConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/sla-policies")
    public ResponseEntity<SlaPolicyEntity> createSlaPolicy(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createSlaPolicy(tenantId,
                required(body, "policy_key"), required(body, "name"), str(body, "applies_to_case_type"),
                str(body, "applies_to_severity"), intVal(body, "acknowledge_within_minutes"),
                intVal(body, "resolve_within_minutes"), intVal(body, "escalate_after_minutes")));
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicyEntity> listSlaPolicies(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return configService.listSlaPolicies(tenantId);
    }

    @PutMapping("/sla-policies/{id}")
    public SlaPolicyEntity updateSlaPolicy(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return configService.updateSlaPolicy(tenantId, id, (Boolean) body.get("active"),
                intVal(body, "acknowledge_within_minutes"), intVal(body, "resolve_within_minutes"),
                intVal(body, "escalate_after_minutes"));
    }

    @PostMapping("/escalation-rules")
    @SuppressWarnings("unchecked")
    public ResponseEntity<EscalationRuleEntity> createEscalationRule(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.createEscalationRule(tenantId,
                required(body, "rule_key"), required(body, "name"), required(body, "trigger_type"),
                (Map<String, Object>) body.getOrDefault("condition", java.util.Map.of()),
                str(body, "escalate_to_role"), str(body, "escalate_to_level"),
                str(body, "notify_template_key")));
    }

    @GetMapping("/escalation-rules")
    public List<EscalationRuleEntity> listEscalationRules(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return configService.listEscalationRules(tenantId);
    }

    // ---- request-map helpers ----

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static Integer intVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }
}
