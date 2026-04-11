package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ButanoServiceClient;

/**
 * Proxies Butano-generated clinical summaries through the Experience BFF.
 *
 * <p>The accepted UI surface is Experience, so sovereign FHIR documents need a
 * canonical /internal/v1 entry point instead of sidecar-only routing.</p>
 */
@RestController
@RequestMapping("/internal/v1/summary")
public class SummaryProxyController {

    private static final Logger log = LoggerFactory.getLogger(SummaryProxyController.class);
    private static final MediaType DEFAULT_FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

    private final ButanoServiceClient butanoClient;

    public SummaryProxyController(ButanoServiceClient butanoClient) {
        this.butanoClient = butanoClient;
    }

    @GetMapping("/ips/{cpid}")
    public ResponseEntity<String> getIpsSummary(
            @PathVariable String cpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = butanoClient.getIpsSummary(cpid);
            return forward(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("IPS summary proxy failed for cpid={} status={}", cpid, exception.getStatusCode());
            return forward(exception, requestId, correlationId);
        }
    }

    @GetMapping("/visit/{encounterId}")
    public ResponseEntity<String> getVisitSummary(
            @PathVariable String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = butanoClient.getVisitSummary(encounterId);
            return forward(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException exception) {
            log.warn("Visit summary proxy failed for encounterId={} status={}", encounterId, exception.getStatusCode());
            return forward(exception, requestId, correlationId);
        }
    }

    private ResponseEntity<String> forward(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : DEFAULT_FHIR_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> forward(HttpStatusCodeException upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getResponseHeaders() != null
                ? upstream.getResponseHeaders().getContentType()
                : null;
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }
}
