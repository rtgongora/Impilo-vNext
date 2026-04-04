package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * HTTP client for the VARAPI (Provider Registry) sovereign service.
 *
 * <p>Provides access to provider profiles, licenses, and council
 * affiliations. VARAPI manages the canonical provider lifecycle
 * with license verification and registration tracking.</p>
 */
@Component
public class VarapiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(VarapiServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VarapiServiceClient(RestTemplate serviceRestTemplate,
                               ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.varapiBaseUrl();
    }

    /**
     * Get licenses for a provider.
     */
    public JsonNode getProviderLicenses(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/licenses";
        log.info("VARAPI: Getting licenses for provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get provider profile.
     */
    public JsonNode getProvider(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId;
        log.info("VARAPI: Getting provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get council affiliations for a provider.
     */
    public JsonNode getProviderCouncilAffiliations(String providerId) {
        String url = baseUrl + "/v1/internal/providers/" + providerId + "/affiliations";
        log.info("VARAPI: Getting affiliations for provider={}", providerId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
