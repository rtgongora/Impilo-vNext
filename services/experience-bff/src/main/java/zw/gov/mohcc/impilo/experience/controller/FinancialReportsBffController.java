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

import java.util.Map;
import java.util.UUID;

/**
 * Proxies institutional financial reports from COSTA to the Experience UI.
 */
@RestController
@RequestMapping("/internal/v1/finance/reports")
public class FinancialReportsBffController {

    private static final Logger log = LoggerFactory.getLogger(FinancialReportsBffController.class);

    private final CostaServiceClient costaClient;

    public FinancialReportsBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @GetMapping("/revenue-summary")
    public ResponseEntity<String> revenueSummary(
            @RequestParam(name = "facility_id", required = false) UUID facilityId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromPath("/internal/v1/finance/reports/revenue-summary")
                .queryParam("year", year)
                .queryParam("month", month);
        if (facilityId != null) {
            b.queryParam("facility_id", facilityId);
        }
        return forwardGet(b.toUriString(), requestId, correlationId);
    }

    @GetMapping("/claims-expenditure")
    public ResponseEntity<String> claimsExpenditure(
            @RequestParam(name = "payer_id", required = false) String payerId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromPath("/internal/v1/finance/reports/claims-expenditure")
                .queryParam("year", year)
                .queryParam("month", month);
        if (payerId != null && !payerId.isBlank()) {
            b.queryParam("payer_id", payerId);
        }
        return forwardGet(b.toUriString(), requestId, correlationId);
    }

    @GetMapping("/provider-payments")
    public ResponseEntity<String> providerPayments(
            @RequestParam(name = "provider_id") UUID providerId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String path = UriComponentsBuilder
                .fromPath("/internal/v1/finance/reports/provider-payments")
                .queryParam("provider_id", providerId)
                .queryParam("year", year)
                .queryParam("month", month)
                .toUriString();
        return forwardGet(path, requestId, correlationId);
    }

    @GetMapping("/debt-aging")
    public ResponseEntity<String> debtAging(
            @RequestParam(name = "facility_id", required = false) UUID facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/internal/v1/finance/reports/debt-aging");
        if (facilityId != null) {
            b.queryParam("facility_id", facilityId);
        }
        return forwardGet(b.toUriString(), requestId, correlationId);
    }

    @GetMapping("/budget-vs-actual")
    public ResponseEntity<String> budgetVsActual(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam int year,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String path = UriComponentsBuilder
                .fromPath("/internal/v1/finance/reports/budget-vs-actual")
                .queryParam("facility_id", facilityId)
                .queryParam("year", year)
                .toUriString();
        return forwardGet(path, requestId, correlationId);
    }

    @PostMapping(value = "/close-period", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> closePeriod(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = costaClient.costaInternalPost("/internal/v1/finance/reports/close-period", body);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            log.warn("close-period upstream failed: {}", ex.getStatusCode());
            return withHeaders(ex, requestId, correlationId);
        }
    }

    private ResponseEntity<String> forwardGet(String pathAndQuery, String requestId, String correlationId) {
        try {
            ResponseEntity<String> upstream = costaClient.costaInternalGet(pathAndQuery);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            log.warn("financial report GET failed: {}", ex.getStatusCode());
            return withHeaders(ex, requestId, correlationId);
        }
    }

    private ResponseEntity<String> withHeaders(ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType ct = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    private ResponseEntity<String> withHeaders(HttpStatusCodeException ex, String requestId, String correlationId) {
        MediaType ct = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(ex.getResponseBodyAsString());
    }
}
