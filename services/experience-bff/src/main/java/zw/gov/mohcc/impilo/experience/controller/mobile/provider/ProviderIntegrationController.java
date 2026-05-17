package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

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
import zw.gov.mohcc.impilo.experience.controller.mobile.citizen.IntegrationStatusMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider mobile-app surface for Integration Service status.
 *
 * <p>Same wire shape as the citizen surface, plus privileged fields (vendor,
 * correlation id, retry counters) since providers may need to escalate
 * incidents via Comms Hub or open a support ticket. The actual mapping logic
 * lives in {@link IntegrationStatusMapper}.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/integration")
public class ProviderIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(ProviderIntegrationController.class);

    private final RestTemplate restTemplate;
    private final String integrationHubBaseUrl;

    public ProviderIntegrationController(
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
            List<Map<String, Object>> mapped = IntegrationStatusMapper.mapForProvider(resp.getBody());
            return ResponseEntity.ok(envelope(mapped, requestId, correlationId));
        } catch (Exception e) {
            log.warn("provider integration statuses failed: {}", e.getMessage());
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
            Map<String, Object> mapped = IntegrationStatusMapper.mapOneForProvider(resp.getBody());
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
