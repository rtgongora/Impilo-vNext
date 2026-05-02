package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

/**
 * BFF proxy for VITO internal client search APIs (internal trust lane).
 */
@RestController
@RequestMapping("/internal/v1/vito/internal/clients")
public class VitoInternalSearchBffController {

    private final VitoServiceClient vitoServiceClient;

    public VitoInternalSearchBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/clients/search", body), requestId, correlationId);
    }

    @GetMapping("/{healthId}/full")
    public ResponseEntity<String> fullClient(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawGet("/v1/internal/clients/" + healthId + "/full"),
                requestId,
                correlationId);
    }

    @FunctionalInterface
    private interface SupplierEx {
        ResponseEntity<String> get();
    }

    private static ResponseEntity<String> forward(SupplierEx call, String requestId, String correlationId) {
        try {
            ResponseEntity<String> upstream = call.get();
            return copy(upstream, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(ex.getResponseBodyAsString());
        }
    }

    private static ResponseEntity<String> copy(
            ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType ct = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }
}
