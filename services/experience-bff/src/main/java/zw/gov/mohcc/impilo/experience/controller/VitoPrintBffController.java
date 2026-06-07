package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.facility.FacilityNameResolver;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/vito/print")
public class VitoPrintBffController {

    private final VitoServiceClient vitoServiceClient;
    private final FacilityNameResolver facilityNameResolver;
    private final ObjectMapper objectMapper;

    public VitoPrintBffController(
            VitoServiceClient vitoServiceClient,
            FacilityNameResolver facilityNameResolver,
            ObjectMapper objectMapper) {
        this.vitoServiceClient = vitoServiceClient;
        this.facilityNameResolver = facilityNameResolver;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/card/job", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> submitPrintJob(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/print/card/job", body), requestId, correlationId);
    }

    @PostMapping(value = "/card/verify-pickup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verifyPickup(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> enrichVerifyPickup(vitoServiceClient.rawPost("/v1/print/card/verify-pickup", body)),
                requestId,
                correlationId);
    }

    @PostMapping(value = "/card/confirm-handover", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> confirmHandover(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/print/card/confirm-handover", body), requestId, correlationId);
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

    private ResponseEntity<String> enrichVerifyPickup(ResponseEntity<String> upstream) {
        if (!upstream.getStatusCode().is2xxSuccessful() || upstream.getBody() == null || upstream.getBody().isBlank()) {
            return upstream;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(upstream.getBody(), Map.class);
            Object facilityName = payload.get("facilityName");
            if (facilityName != null) {
                String raw = facilityName.toString();
                String resolved = facilityNameResolver.resolve(raw);
                if (resolved != null && !resolved.isBlank() && !resolved.equals(raw)) {
                    payload.put("facilityName", resolved);
                }
            }
            return ResponseEntity.status(upstream.getStatusCode())
                    .headers(upstream.getHeaders())
                    .body(objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            return upstream;
        }
    }
}
