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

@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class FinancialBudgetsBffController {

    private static final Logger log = LoggerFactory.getLogger(FinancialBudgetsBffController.class);

    private final CostaServiceClient costaClient;

    public FinancialBudgetsBffController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = costaClient.costaInternalPost("/internal/v1/finance/budgets", body);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            log.warn("budget create upstream failed: {}", ex.getStatusCode());
            return withHeaders(ex, requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<String> list(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam int year,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String path = UriComponentsBuilder
                .fromPath("/internal/v1/finance/budgets")
                .queryParam("facility_id", facilityId)
                .queryParam("year", year)
                .toUriString();
        try {
            ResponseEntity<String> upstream = costaClient.costaInternalGet(path);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            log.warn("budget list upstream failed: {}", ex.getStatusCode());
            return withHeaders(ex, requestId, correlationId);
        }
    }

    @PutMapping(value = "/{id}/spend", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordSpend(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            ResponseEntity<String> upstream = costaClient.costaInternalPut(
                    "/internal/v1/finance/budgets/" + id + "/spend", body);
            return withHeaders(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            log.warn("budget spend upstream failed: {}", ex.getStatusCode());
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
