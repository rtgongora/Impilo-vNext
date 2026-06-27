package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the OPA decision sidecar ({@code POST /v1/data/impilo/authz/decision}) for the Phase 3
 * strangler. Used in SHADOW mode to compare OPA's verdict to the Java PolicyEngine; OPA is never
 * authoritative here. The {@code input} is a prepared map (no in-rego data fetches), so the call is
 * a fast CPU evaluation well within the ext_authz budget.
 */
@Service
public class OpaDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(OpaDecisionClient.class);

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;

    public OpaDecisionClient(RestTemplate restTemplate, AuthzProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /** Evaluate the gate decision for the prepared input. Returns null on any transport/parse error. */
    public OpaDecision decide(Map<String, Object> input) {
        String url = properties.getOpaUrl().replaceAll("/$", "") + "/v1/data/impilo/authz/decision";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<JsonNode> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(Map.of("input", input), headers), JsonNode.class);
            JsonNode result = response.getBody() == null ? null : response.getBody().get("result");
            if (result == null || result.isNull()) {
                return null; // undefined decision (e.g. missing actor) — caller treats as no-signal
            }
            boolean allow = result.path("allow").asBoolean(false);
            List<String> reasons = new ArrayList<>();
            if (result.has("deny_reasons") && result.get("deny_reasons").isArray()) {
                result.get("deny_reasons").forEach(n -> reasons.add(n.asText()));
            }
            return new OpaDecision(allow, reasons);
        } catch (Exception e) {
            log.debug("OPA decision call failed (shadow, ignored): {}", e.getMessage());
            return null;
        }
    }
}
