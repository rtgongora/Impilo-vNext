package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Surfaces the C2 Vashandi work-context read-model to the shell so the
 * WHERE/WHAT context picker can render active assignments (facility / department /
 * ward / service-point / virtual-pool / above-site × role/workspace), the
 * current check-in state, and whether a context chooser is required.
 *
 * Composition only — the BFF persists nothing. Vashandi is the SoR. Reached
 * behind the Envoy ext_authz → TSHEPO gate (CONTEXT-SELECT specced to track P).
 * Degrades honestly: if upstream is unavailable, returns an empty/unresolved
 * context with {@code integrationStatus=UPSTREAM_UNAVAILABLE} rather than a 5xx.
 */
@RestController
@RequestMapping("/internal/v1/work-context")
public class WorkContextController {

    private static final Logger log = LoggerFactory.getLogger(WorkContextController.class);

    private final VashandiServiceClient vashandiClient;

    public WorkContextController(VashandiServiceClient vashandiClient) {
        this.vashandiClient = vashandiClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWorkContext(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {

        Map<String, Object> attributes;
        String integrationStatus;
        JsonNode upstream = vashandiClient.fetchWorkContext(actorId);
        if (upstream == null || upstream.isNull()) {
            log.debug("work-context: vashandi upstream unavailable for actor request {}", requestId);
            attributes = unresolved();
            integrationStatus = "UPSTREAM_UNAVAILABLE";
        } else {
            attributes = toAttributes(upstream);
            integrationStatus = Boolean.TRUE.equals(attributes.get("resolved")) ? "LIVE" : "NO_WORK_CONTEXT";
        }
        attributes.put("integrationStatus", integrationStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", actorId,
                "type", "work-context",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toAttributes(JsonNode node) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("workforceProfileId", text(node, "workforceProfileId"));
        attrs.put("impiloHealthId", text(node, "impiloHealthId"));
        attrs.put("activeAssignments", arrayOrEmpty(node, "activeAssignments"));
        attrs.put("checkIn", node.has("checkIn") && !node.get("checkIn").isNull()
                ? node.get("checkIn") : Map.of("state", "CHECKED_OUT"));
        attrs.put("affiliations", arrayOrEmpty(node, "affiliations"));
        attrs.put("requiresContextChooser", node.path("requiresContextChooser").asBoolean(false));
        attrs.put("resolved", node.path("resolved").asBoolean(false));
        return attrs;
    }

    private Map<String, Object> unresolved() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("workforceProfileId", null);
        attrs.put("impiloHealthId", null);
        attrs.put("activeAssignments", List.of());
        attrs.put("checkIn", Map.of("state", "CHECKED_OUT"));
        attrs.put("affiliations", List.of());
        attrs.put("requiresContextChooser", false);
        attrs.put("resolved", false);
        return attrs;
    }

    private Object arrayOrEmpty(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isArray() ? v : List.of();
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v == null || v.isBlank() ? null : v;
    }
}
