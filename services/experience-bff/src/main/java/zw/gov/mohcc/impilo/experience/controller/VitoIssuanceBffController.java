package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/vito/issuance")
public class VitoIssuanceBffController {

    private final VitoServiceClient vitoServiceClient;

    public VitoIssuanceBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> submit(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/issuance/submit", body), requestId, correlationId);
    }

    @PostMapping(value = "/{requestId}/proofing", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> proofing(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/issuance/" + requestId + "/proofing", body), reqId, correlationId);
    }

    @PostMapping(value = "/{requestId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> approve(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/issuance/" + requestId + "/approve", body), reqId, correlationId);
    }

    @PostMapping(value = "/{requestId}/issue", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> issue(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/issuance/" + requestId + "/issue", body), reqId, correlationId);
    }

    @PostMapping(value = "/{requestId}/deliver", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deliver(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/issuance/" + requestId + "/deliver", body), reqId, correlationId);
    }

    @PostMapping(value = "/{requestId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reject(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/internal/issuance/" + requestId + "/reject", body), reqId, correlationId);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> queue(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String filter = state != null && !state.isBlank() ? state : status;
        String path = "/v1/internal/issuance/queue?page=" + page + "&size=" + size
                + (filter != null && !filter.isBlank() ? "&state=" + filter : "");
        return forward(() -> vitoServiceClient.rawGet(path), requestId, correlationId);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<String> getRequest(
            @PathVariable String requestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/internal/issuance/" + requestId), reqId, correlationId);
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
