package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Citizen mobile-app surface for Integration Service status.
 *
 * <p>Mobile {@code @impilo/mobile-integration} consumes this endpoint to
 * render the citizen-friendly status of external-system-backed services
 * (lab, imaging, payment, claim, delivery, comms). The BFF maps raw upstream
 * adapter health into the canonical mobile status enum so the mobile UI never
 * needs to handle vendor jargon.</p>
 *
 * <p>If the integration-hub upstream is unreachable, returns an empty list so
 * the mobile UI degrades to "no integrations to show" rather than failing.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/integration")
public class CitizenIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(CitizenIntegrationController.class);

    private final RestTemplate restTemplate;
    private final String integrationHubBaseUrl;

    public CitizenIntegrationController(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.integration-hub-base-url:http://localhost:8110}")
                    String integrationHubBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.integrationHubBaseUrl = integrationHubBaseUrl;
    }

    @GetMapping("/statuses")
    public ResponseEntity<Map<String, Object>> statuses(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(
                    integrationHubBaseUrl + "/internal/v1/routes", JsonNode.class);
            JsonNode body = resp.getBody();
            List<Map<String, Object>> mapped = IntegrationStatusMapper.mapForCitizen(body);
            return ResponseEntity.ok(envelope(mapped, requestId, correlationId));
        } catch (Exception e) {
            log.warn("citizen integration statuses failed: {}", e.getMessage());
            return ResponseEntity.ok(envelope(Collections.emptyList(), requestId, correlationId));
        }
    }

    @GetMapping("/statuses/{id}")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(
                    integrationHubBaseUrl + "/internal/v1/routes/" + id, JsonNode.class);
            JsonNode body = resp.getBody();
            Map<String, Object> mapped = IntegrationStatusMapper.mapOneForCitizen(body);
            return ResponseEntity.ok(envelope(mapped, requestId, correlationId));
        } catch (Exception e) {
            return ResponseEntity.ok(envelope(Collections.emptyMap(), requestId, correlationId));
        }
    }

    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data != null ? data : new ArrayList<>());
        envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return envelope;
    }
}
