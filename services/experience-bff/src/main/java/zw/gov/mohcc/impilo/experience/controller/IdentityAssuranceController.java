package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identity Assurance BFF Controller — surfaces the caller's assurance state and upgrade
 * pathways (the Health OS "assurance level" access-control dimension).
 *
 * <p>This is a thin proxy over identity-assurance-service, the canonical owner of identity
 * assurance. It previously fabricated a hardcoded LOA2 response; it now returns the real
 * per-actor level + the reviewed upgrade workflow. Trust headers are forwarded to the
 * service by the shared client interceptor.</p>
 */
@RestController
@RequestMapping("/internal/v1/identity/assurance")
public class IdentityAssuranceController {

    private static final Logger log = LoggerFactory.getLogger(IdentityAssuranceController.class);

    private final IdentityAssuranceServiceClient assuranceClient;

    public IdentityAssuranceController(IdentityAssuranceServiceClient assuranceClient) {
        this.assuranceClient = assuranceClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAssuranceStatus(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        log.info("Proxying assurance status, requestId={}", requestId);
        JsonNode status = assuranceClient.getAssuranceStatus();
        return ResponseEntity.ok(envelope(status, requestId, correlationId));
    }

    @PostMapping("/upgrade/request")
    public ResponseEntity<Map<String, Object>> requestUpgrade(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        log.info("Proxying assurance upgrade request, requestId={}", requestId);
        JsonNode upgrade = assuranceClient.requestUpgrade(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(envelope(upgrade, requestId, correlationId));
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }
}
