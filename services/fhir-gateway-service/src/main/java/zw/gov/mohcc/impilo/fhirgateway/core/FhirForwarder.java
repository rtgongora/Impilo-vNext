package zw.gov.mohcc.impilo.fhirgateway.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Actually forwards a FHIR payload to a route's target endpoint (BUTANO/HAPI) over HTTP.
 *
 * <p>Replaces the previous behaviour where the gateway recorded SUCCESS without ever delivering
 * the resource. The injected {@link RestTemplate} (the trust-header-forwarding bean) carries the
 * inbound trust context downstream. Fail-closed: any non-2xx response or transport error is a
 * NON-delivery — the caller must not report SUCCESS.</p>
 */
@Component
public class FhirForwarder {

    private static final Logger log = LoggerFactory.getLogger(FhirForwarder.class);

    private final RestTemplate restTemplate;

    public FhirForwarder(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** Outcome of a forward attempt. {@code delivered} is true only on a 2xx downstream response. */
    public record ForwardAttempt(boolean delivered, int status, String detail) {}

    public ForwardAttempt send(String targetEndpoint, String resourceType, String operation, String payload) {
        HttpMethod method = "UPDATE".equalsIgnoreCase(operation) || "PUT".equalsIgnoreCase(operation)
                ? HttpMethod.PUT : HttpMethod.POST;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/fhir+json"));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    targetEndpoint, method, new HttpEntity<>(payload, headers), String.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            if (!ok) {
                log.warn("FHIR forward to {} returned {}", targetEndpoint, response.getStatusCode());
            }
            return new ForwardAttempt(ok, response.getStatusCode().value(),
                    ok ? "forwarded" : "downstream returned " + response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            log.warn("FHIR forward to {} failed: {} {}", targetEndpoint, e.getStatusCode(), e.getMessage());
            return new ForwardAttempt(false, e.getStatusCode().value(), "downstream error");
        } catch (Exception e) {
            log.error("FHIR forward to {} failed (transport)", targetEndpoint, e);
            return new ForwardAttempt(false, 0, "transport error: " + e.getClass().getSimpleName());
        }
    }
}
