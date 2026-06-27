package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;

import java.util.UUID;

/**
 * Facility-scoped billing read views (unbilled charges, invoices, payments) proxied
 * from COSTA for the ops-console BillingPanel. COSTA owns the billing truth.
 */
@RestController
@RequestMapping("/internal/v1/finance/facility")
public class FinanceFacilityBffController {

    private static final Logger log = LoggerFactory.getLogger(FinanceFacilityBffController.class);

    private final CostaServiceClient costaClient;

    public FinanceFacilityBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @GetMapping("/unbilled-charges")
    public ResponseEntity<String> unbilledCharges(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/internal/v1/finance/facility/unbilled-charges", facilityId, limit, requestId, correlationId);
    }

    @GetMapping("/invoices")
    public ResponseEntity<String> invoices(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/internal/v1/finance/facility/invoices", facilityId, limit, requestId, correlationId);
    }

    @GetMapping("/payments")
    public ResponseEntity<String> payments(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forwardGet("/internal/v1/finance/facility/payments", facilityId, limit, requestId, correlationId);
    }

    private ResponseEntity<String> forwardGet(String path, UUID facilityId, int limit,
                                              String requestId, String correlationId) {
        String uri = UriComponentsBuilder.fromPath(path)
                .queryParam("facility_id", facilityId)
                .queryParam("limit", limit)
                .toUriString();
        try {
            ResponseEntity<String> upstream = costaClient.costaInternalGet(uri);
            return withHeaders(upstream.getStatusCode().value(),
                    upstream.getHeaders().getContentType(), upstream.getBody(), requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            log.warn("facility billing GET failed: {}", ex.getStatusCode());
            return withHeaders(ex.getStatusCode().value(),
                    ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null,
                    ex.getResponseBodyAsString(), requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(int status, MediaType ct, String body,
                                               String requestId, String correlationId) {
        return ResponseEntity.status(status)
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(body);
    }
}
