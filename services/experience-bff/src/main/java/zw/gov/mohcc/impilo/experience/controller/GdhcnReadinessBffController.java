package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GDHCN readiness cockpit BFF (Wave B B6). Proxies the readiness assessment owned by
 * tshepo-authz; the authz service enforces admin role + step-up on updates. Trust headers
 * (tenant/actor/JWT) are forwarded to authz by the shared service-client interceptor.
 */
@RestController
@RequestMapping("/internal/v1/admin-governance/gdhcn-readiness")
public class GdhcnReadinessBffController {

    private final TshepoAuthzServiceClient authzClient;

    public GdhcnReadinessBffController(TshepoAuthzServiceClient authzClient) {
        this.authzClient = authzClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode domains = authzClient.listGdhcnReadiness();
        return ok(requestId, correlationId, Map.of("domains", domains));
    }

    @PostMapping("/{domainKey}")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String domainKey,
            @RequestBody Map<String, Object> body) {
        JsonNode updated = authzClient.updateGdhcnReadiness(domainKey, body);
        return ok(requestId, correlationId, Map.of("domain", updated));
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("type", "gdhcn-readiness", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
