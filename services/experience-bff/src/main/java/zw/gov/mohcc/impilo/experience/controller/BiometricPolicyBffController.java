package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import org.springframework.web.client.RestTemplate;

/**
 * Proxies Tshepo biometric policy evaluation for Experience UI and operators.
 */
@RestController
@RequestMapping("/internal/v1/trust")
public class BiometricPolicyBffController {

    private final RestTemplate restTemplate;
    private final ServiceClientConfig.ServiceEndpoints endpoints;

    public BiometricPolicyBffController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.endpoints = endpoints;
    }

    @PostMapping(value = "/biometric-policy/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> evaluate(
            @RequestBody String body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String url = endpoints.tshepoAuthzBaseUrl() + "/v1/biometric-policy/evaluate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> upstream =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return copyHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(ex.getResponseBodyAsString());
        }
    }

    private static ResponseEntity<String> copyHeaders(
            ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType ct = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }
}
