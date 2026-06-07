package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.trust.TrustBreakGlassResourceMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile provider-facing API endpoints.
 * Serves clinician/provider workflows from the mobile provider app.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileProviderController {

    private static final Logger log = LoggerFactory.getLogger(MobileProviderController.class);

    private final PctServiceClient pctClient;
    private final VarapiServiceClient varapiClient;
    private final TshepoAuthzServiceClient authzClient;

    public MobileProviderController(PctServiceClient pctClient,
                                    VarapiServiceClient varapiClient,
                                    TshepoAuthzServiceClient authzClient) {
        this.pctClient = pctClient;
        this.varapiClient = varapiClient;
        this.authzClient = authzClient;
    }

    @GetMapping("/entitlement/verify")
    public ResponseEntity<Map<String, Object>> verifyEntitlement(
            @RequestParam("cpid") String cpid,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                "cpid", cpid, "eligible", true, "schemes", List.of())));
    }

    /**
     * Provider break-glass activation — same trust chain as web {@code POST /internal/v1/trust/break-glass}.
     */
    @PostMapping("/break-glass/activate")
    public ResponseEntity<Map<String, Object>> activateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        String reason = stringVal(body, "reason", "justification");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", Map.of("code", "REASON_REQUIRED", "message", "Break-glass reason is mandatory")));
        }
        String resolvedActor = actorId != null && !actorId.isBlank()
                ? actorId
                : stringVal(body, "actorId", "actor_id", "providerId", "provider_id");
        if (resolvedActor == null || resolvedActor.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", Map.of("code", "ACTOR_REQUIRED", "message", "Provider actor identity is required")));
        }

        Map<String, Object> upstream = new LinkedHashMap<>();
        upstream.put("tenantId", UUID.fromString(tenantId));
        upstream.put("actorId", resolvedActor);
        upstream.put("reason", reason.trim());
        putIfPresent(upstream, "resourceType", body, "resourceType", "resource_type");
        putIfPresent(upstream, "resourceId", body, "resourceId", "resource_id", "patientId", "patient_id");

        try {
            JsonNode data = authzClient.requestBreakGlass(upstream);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "data", TrustBreakGlassResourceMapper.toRequestResource(data)));
        } catch (Exception e) {
            log.error("Mobile provider break-glass activation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success", false,
                    "error", Map.of("code", "BREAK_GLASS_REQUEST_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * Break-glass deactivation is client-side only; sovereign access expires by TSHEPO TTL.
     */
    @PostMapping("/break-glass/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateBreakGlass(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "active", false,
                        "message", "Local break-glass flag cleared; TSHEPO override remains until TTL expiry")));
    }

    private static String stringVal(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String targetKey,
                                     Map<String, Object> body, String... keys) {
        String value = stringVal(body, keys);
        if (value != null && !value.isBlank()) {
            target.put(targetKey, value);
        }
    }
}
