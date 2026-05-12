package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/vito/cards")
public class VitoCardsBffController {

    private final VitoServiceClient vitoServiceClient;

    public VitoCardsBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @PostMapping(value = "/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requestCard(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/cards/request", body), requestId, correlationId);
    }

    @PostMapping(value = "/{cardId}/print", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> printCard(
            @PathVariable String cardId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/cards/" + cardId + "/print", body), requestId, correlationId);
    }

    @PostMapping(value = "/{cardId}/activate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> activateCard(
            @PathVariable String cardId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/cards/" + cardId + "/activate", body), requestId, correlationId);
    }

    @PostMapping(value = "/{cardId}/inactivate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> inactivateCard(
            @PathVariable String cardId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/cards/" + cardId + "/inactivate", body), requestId, correlationId);
    }

    @PostMapping(value = "/{cardId}/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeCard(
            @PathVariable String cardId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/cards/" + cardId + "/revoke", body), requestId, correlationId);
    }

    @GetMapping("/active/{healthId}")
    public ResponseEntity<String> getActiveCard(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/cards/active/" + healthId), requestId, correlationId);
    }

    @GetMapping("/history/{healthId}")
    public ResponseEntity<String> getCardHistory(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/cards/history/" + healthId), requestId, correlationId);
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<String> getCardsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawGet("/v1/cards/by-status/" + status + "?page=" + page + "&size=" + size),
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
