package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the identity-assurance-service — the canonical owner of identity assurance
 * (assurance level + upgrade-request workflow). Trust headers (tenant/actor) are forwarded by
 * the shared {@link ServiceClientConfig} interceptor, so the service resolves the caller's
 * identity itself. Replaces the BFF's previous fabricated assurance responses.
 */
@Component
public class IdentityAssuranceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityAssuranceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IdentityAssuranceServiceClient(RestTemplate serviceRestTemplate,
                                          ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.identityAssuranceBaseUrl();
    }

    /** The caller's assurance status (level, permissions, upgrade pathways). */
    public JsonNode getAssuranceStatus() {
        String url = baseUrl + "/internal/v1/assurance/status";
        log.info("IDENTITY-ASSURANCE: getting assurance status");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Raise an assurance upgrade request for the caller. */
    public JsonNode requestUpgrade(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/assurance/upgrade/request";
        log.info("IDENTITY-ASSURANCE: requesting assurance upgrade");
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
