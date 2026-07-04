package zw.gov.mohcc.impilo.experience.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * Thin client for the Khuluma comms hub delivery API. Msika REQUESTS outbound
 * confirmations/updates via Khuluma — it never sends messages itself.
 * Khuluma owns delivery; this just forwards a dispatch request.
 */
@Component("khulumaDispatchClient")
public class KhulumaServiceClient {

    private static final Logger log = LoggerFactory.getLogger(KhulumaServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public KhulumaServiceClient(RestTemplate serviceRestTemplate,
                                ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.khulumaBaseUrl();
    }

    /** Request an outbound delivery. Body: { channels:[...], messageRef, recipient }. */
    public ResponseEntity<String> dispatch(String tenantId, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) headers.set("X-Tenant-ID", tenantId);
        log.info("KHULUMA: dispatch request for messageRef={}", body.get("messageRef"));
        return restTemplate.postForEntity(baseUrl + "/internal/v1/khuluma/delivery/dispatch",
                new HttpEntity<>(body, headers), String.class);
    }

    /**
     * On-call roster for a tenant, optionally scoped to a clinical specialty (W2 ON_CALL
     * teleconsult routing). Returns the khuluma payload's {@code data} array:
     * [{actorId, actorType, dutyStatus, specialty}].
     */
    public com.fasterxml.jackson.databind.JsonNode onCallRoster(String tenantId, String specialty) {
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) headers.set("X-Tenant-ID", tenantId);
        org.springframework.web.util.UriComponentsBuilder builder =
                org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/khuluma/on-call");
        if (specialty != null && !specialty.isBlank()) {
            builder.queryParam("specialty", specialty.trim());
        }
        log.info("KHULUMA: on-call roster specialty={}", specialty);
        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = restTemplate.exchange(
                builder.toUriString(), org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}
