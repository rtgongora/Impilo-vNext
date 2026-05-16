package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * BFF client for Msika Apps (Health OS Capability Marketplace) sovereign service.
 *
 * <p>Exposes the read-and-write surfaces needed by the experience layer for
 * the launcher, marketplace catalogue, item detail, activation workflow,
 * installation lifecycle, and audit log.</p>
 */
@Component
public class MsikaAppsClient {

    private static final Logger log = LoggerFactory.getLogger(MsikaAppsClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MsikaAppsClient(RestTemplate serviceRestTemplate,
                           ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.msikaAppsBaseUrl();
    }

    public ResponseEntity<String> launcher(String facilityId, String roles) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/marketplace/launcher")
                .queryParamIfPresent("facilityId", java.util.Optional.ofNullable(blank(facilityId)))
                .queryParamIfPresent("roles", java.util.Optional.ofNullable(blank(roles)))
                .toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> catalogue(MultiValueMap<String, String> params) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/marketplace/items")
                .queryParams(params).toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> item(String id) {
        return restTemplate.getForEntity(baseUrl + "/internal/v1/marketplace/items/" + id, String.class);
    }

    public ResponseEntity<String> publishers() {
        return restTemplate.getForEntity(baseUrl + "/internal/v1/marketplace/publishers", String.class);
    }

    public ResponseEntity<String> requestActivation(String requestBody) {
        return postJson(baseUrl + "/internal/v1/marketplace/activation-requests", requestBody);
    }

    public ResponseEntity<String> listActivationRequests(String status) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/marketplace/activation-requests")
                .queryParamIfPresent("status", java.util.Optional.ofNullable(blank(status))).toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> decideActivation(String id, String requestBody) {
        return postJson(baseUrl + "/internal/v1/marketplace/activation-requests/" + id + "/decision", requestBody);
    }

    public ResponseEntity<String> listInstallations(String lifecycle) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/marketplace/installations")
                .queryParamIfPresent("lifecycle", java.util.Optional.ofNullable(blank(lifecycle))).toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    public ResponseEntity<String> getInstallation(String id) {
        return restTemplate.getForEntity(baseUrl + "/internal/v1/marketplace/installations/" + id, String.class);
    }

    public ResponseEntity<String> configureInstallation(String id, String requestBody) {
        return postJson(baseUrl + "/internal/v1/marketplace/installations/" + id + "/configure", requestBody);
    }

    public ResponseEntity<String> activateInstallation(String id) {
        log.info("Msika Apps: activating installation {}", id);
        return restTemplate.exchange(baseUrl + "/internal/v1/marketplace/installations/" + id + "/activate",
                HttpMethod.POST, HttpEntity.EMPTY, String.class);
    }

    public ResponseEntity<String> suspendInstallation(String id, String requestBody) {
        return postJson(baseUrl + "/internal/v1/marketplace/installations/" + id + "/suspend", requestBody);
    }

    public ResponseEntity<String> auditEvents(String itemId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/marketplace/audit")
                .queryParamIfPresent("itemId", java.util.Optional.ofNullable(blank(itemId))).toUriString();
        return restTemplate.getForEntity(url, String.class);
    }

    private ResponseEntity<String> postJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
