package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.Map;

/**
 * BFF proxy for VARAPI provider biometric APIs.
 */
@RestController
@RequestMapping("/internal/v1/identity/biometric/varapi")
public class VarapiBiometricBffController {

    private final VarapiServiceClient varapiServiceClient;

    public VarapiBiometricBffController(VarapiServiceClient varapiServiceClient) {
        this.varapiServiceClient = varapiServiceClient;
    }

    @PostMapping(value = "/{providerPublicId}/enroll", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> enroll(
            @PathVariable String providerPublicId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> varapiServiceClient.rawPost("/v1/provider-biometric/" + providerPublicId + "/enroll", body),
                requestId,
                correlationId);
    }

    @GetMapping("/{providerPublicId}/templates")
    public ResponseEntity<String> templates(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> varapiServiceClient.rawGet("/v1/provider-biometric/" + providerPublicId + "/templates"),
                requestId,
                correlationId);
    }

    @GetMapping("/{providerPublicId}/profile")
    public ResponseEntity<String> profile(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> varapiServiceClient.rawGet("/v1/provider-biometric/" + providerPublicId + "/profile"),
                requestId,
                correlationId);
    }

    @PostMapping(value = "/{providerPublicId}/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verify(
            @PathVariable String providerPublicId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(
                () -> varapiServiceClient.rawPost("/v1/provider-biometric/" + providerPublicId + "/verify", body),
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
            MediaType ct = upstream.getHeaders().getContentType();
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(ex.getResponseBodyAsString());
        }
    }
}
