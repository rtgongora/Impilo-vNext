package zw.gov.mohcc.impilo.oros.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.DestinationDto;
import zw.gov.mohcc.impilo.oros.core.AdminConfigService;
import zw.gov.mohcc.impilo.oros.core.RoutingDirectoryService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Diagnostics admin configuration (spec §W8): the provider/destinations directory, routing rules,
 * and critical-result escalation rules. Config is tenant/facility-scoped JSON; access is
 * authenticated + policy-governed at the trust edge.
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminConfigService adminConfigService;
    private final RoutingDirectoryService routingDirectoryService;
    private final ObjectMapper objectMapper;

    public AdminController(AdminConfigService adminConfigService,
                          RoutingDirectoryService routingDirectoryService,
                          ObjectMapper objectMapper) {
        this.adminConfigService = adminConfigService;
        this.routingDirectoryService = routingDirectoryService;
        this.objectMapper = objectMapper;
    }

    /** Diagnostic provider/destinations directory. */
    @GetMapping("/providers/directory")
    public ResponseEntity<ApiResponse<List<DestinationDto>>> providerDirectory() {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(routingDirectoryService.destinations(), cid));
    }

    @GetMapping("/routing/rules")
    public ResponseEntity<ApiResponse<Object>> getRoutingRules(
            @RequestParam(name = "facility", required = false) UUID facility) {
        return read(AdminConfigService.ROUTING_RULES, facility);
    }

    @PutMapping("/routing/rules")
    public ResponseEntity<ApiResponse<Object>> putRoutingRules(
            @RequestParam(name = "facility", required = false) UUID facility,
            @RequestBody Map<String, Object> rules) {
        return write(AdminConfigService.ROUTING_RULES, facility, rules);
    }

    @GetMapping("/critical-escalation/rules")
    public ResponseEntity<ApiResponse<Object>> getEscalationRules(
            @RequestParam(name = "facility", required = false) UUID facility) {
        return read(AdminConfigService.CRITICAL_ESCALATION_RULES, facility);
    }

    @PutMapping("/critical-escalation/rules")
    public ResponseEntity<ApiResponse<Object>> putEscalationRules(
            @RequestParam(name = "facility", required = false) UUID facility,
            @RequestBody Map<String, Object> rules) {
        return write(AdminConfigService.CRITICAL_ESCALATION_RULES, facility, rules);
    }

    // ── helpers ──

    private ResponseEntity<ApiResponse<Object>> read(String type, UUID facility) {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(parse(adminConfigService.get(type, facility)), cid));
    }

    private ResponseEntity<ApiResponse<Object>> write(String type, UUID facility, Map<String, Object> rules) {
        String cid = TrustContextHolder.require().correlationId().toString();
        String stored = adminConfigService.put(type, facility, serialize(rules));
        return ResponseEntity.ok(ApiResponse.ok(parse(stored), cid));
    }

    private Object parse(String json) {
        try {
            return objectMapper.readValue(json != null ? json : "{}", Object.class);
        } catch (Exception e) {
            log.warn("Failed to parse admin config JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private String serialize(Map<String, Object> rules) {
        try {
            return objectMapper.writeValueAsString(rules != null ? rules : Map.of());
        } catch (Exception e) {
            log.warn("Failed to serialize admin config: {}", e.getMessage());
            return "{}";
        }
    }
}
