package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.trust.TrustBreakGlassResourceMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-facing trust endpoints (break-glass request) for clinical/emergency shells.
 */
@RestController
@RequestMapping("/internal/v1/trust")
public class TrustProviderController {

    private static final Logger log = LoggerFactory.getLogger(TrustProviderController.class);

    private final TshepoAuthzServiceClient authzClient;

    public TrustProviderController(TshepoAuthzServiceClient authzClient) {
        this.authzClient = authzClient;
    }

    /**
     * POST /internal/v1/trust/break-glass — request emergency access override via TSHEPO Authz.
     */
    @PostMapping("/break-glass")
    public ResponseEntity<Map<String, Object>> requestBreakGlass(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        String reason = stringVal(body, "reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "REASON_REQUIRED", "message", "Break-glass reason is mandatory")));
        }
        String resolvedActor = actorId != null && !actorId.isBlank()
                ? actorId
                : stringVal(body, "actorId", "actor_id");
        if (resolvedActor == null || resolvedActor.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "ACTOR_REQUIRED", "message", "Actor identity is required")));
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
                    "data", TrustBreakGlassResourceMapper.toRequestResource(data),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust provider break-glass request failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "BREAK_GLASS_REQUEST_FAILED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
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
