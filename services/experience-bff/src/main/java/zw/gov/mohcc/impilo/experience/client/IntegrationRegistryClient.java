package zw.gov.mohcc.impilo.experience.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

/**
 * BFF client for the Integration Hub governance registry (external apps,
 * integration contracts, S2S contracts, webhook subscriptions, and the
 * event catalogue). Pairs with {@link MsikaAppsClient} for the marketplace.
 */
@Component
public class IntegrationRegistryClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public IntegrationRegistryClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.integrationHubBaseUrl();
    }

    public ResponseEntity<String> externalApps()       { return get("/internal/v1/external-apps"); }
    public ResponseEntity<String> externalApp(String id) { return get("/internal/v1/external-apps/" + id); }
    public ResponseEntity<String> registerExternalApp(String body) { return post("/internal/v1/external-apps", body); }
    public ResponseEntity<String> suspendExternalApp(String id, String body) { return post("/internal/v1/external-apps/" + id + "/suspend", body); }

    public ResponseEntity<String> integrationContracts(String externalAppId) {
        return getWith("/internal/v1/integration-contracts", "externalAppId", externalAppId);
    }
    public ResponseEntity<String> createIntegrationContract(String body) {
        return post("/internal/v1/integration-contracts", body);
    }

    public ResponseEntity<String> s2sContracts() { return get("/internal/v1/s2s-contracts"); }
    public ResponseEntity<String> createS2SContract(String body) { return post("/internal/v1/s2s-contracts", body); }

    public ResponseEntity<String> webhookSubscriptions(String externalAppId) {
        return getWith("/internal/v1/webhook-subscriptions", "externalAppId", externalAppId);
    }
    public ResponseEntity<String> createWebhookSubscription(String body) {
        return post("/internal/v1/webhook-subscriptions", body);
    }

    public ResponseEntity<String> eventCatalogue(MultiValueMap<String, String> params) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/event-catalogue")
                .queryParams(params).toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity(baseUrl + path, String.class);
    }

    private ResponseEntity<String> getWith(String path, String key, String value) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + path)
                .queryParamIfPresent(key, java.util.Optional.ofNullable(blank(value))).toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    private ResponseEntity<String> post(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
