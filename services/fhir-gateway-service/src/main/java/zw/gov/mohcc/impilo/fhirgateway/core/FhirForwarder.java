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

    /** Read-only use on the response body; ObjectMapper is thread-safe for reads. */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final RestTemplate restTemplate;

    public FhirForwarder(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Outcome of a forward attempt. {@code delivered} is true only on a 2xx downstream response.
     *
     * @param resourceId the logical id the destination assigned, when it told us — null otherwise.
     *                   The three-argument form means "not known", which is the honest state for
     *                   every non-delivery.
     */
    public record ForwardAttempt(boolean delivered, int status, String detail, String resourceId) {

        public ForwardAttempt(boolean delivered, int status, String detail) {
            this(delivered, status, detail, null);
        }
    }

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
                    ok ? "forwarded" : "downstream returned " + response.getStatusCode().value(),
                    ok ? extractResourceId(response) : null);
        } catch (RestClientResponseException e) {
            log.warn("FHIR forward to {} failed: {} {}", targetEndpoint, e.getStatusCode(), e.getMessage());
            return new ForwardAttempt(false, e.getStatusCode().value(), "downstream error");
        } catch (Exception e) {
            log.error("FHIR forward to {} failed (transport)", targetEndpoint, e);
            return new ForwardAttempt(false, 0, "transport error: " + e.getClass().getSimpleName());
        }
    }

    /**
     * The logical id the destination assigned, from {@code Location} if it sent one, else from the
     * returned resource body.
     *
     * <p>The gateway delivered resources and then discarded the one thing the caller needed back.
     * A service that writes through here has no way to reference what it wrote — oros stores a
     * {@code butanoRef} against the order, inpatient links a Procedure, offline-edge reconciles
     * against it. Returning only an audit-log id would have forced each of them to either invent a
     * reference or record none, and "the write succeeded but I cannot name the result" is not a
     * state a clinical record should have.</p>
     *
     * <p>Both sources are read because neither is guaranteed: {@code Prefer: return=minimal}
     * suppresses the body, and a proxy may drop {@code Location}. Null when neither says.</p>
     */
    static String extractResourceId(ResponseEntity<String> response) {
        String fromLocation = idFromLocation(response.getHeaders().getFirst(HttpHeaders.LOCATION));
        if (fromLocation != null) {
            return fromLocation;
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            String id = OBJECT_MAPPER.readTree(body).path("id").asText(null);
            return id == null || id.isBlank() ? null : id;
        } catch (Exception e) {
            log.debug("Downstream response body is not parseable JSON; no resource id recovered");
            return null;
        }
    }

    /** {@code http://host/fhir/Observation/123/_history/1} → {@code 123}. */
    private static String idFromLocation(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String path = location;
        int history = path.indexOf("/_history");
        if (history >= 0) {
            path = path.substring(0, history);
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return null;
        }
        String id = path.substring(lastSlash + 1);
        return id.isBlank() ? null : id;
    }
}
