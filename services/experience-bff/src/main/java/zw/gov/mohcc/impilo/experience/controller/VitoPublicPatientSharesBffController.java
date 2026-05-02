package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

/**
 * BFF proxy for VITO public patient-share APIs (unauthenticated / session-token lane).
 */
@RestController
@RequestMapping("/internal/v1/vito/public/patient-shares")
public class VitoPublicPatientSharesBffController {

    private static final String H_SESSION = "X-Patient-Share-Session";

    private final VitoServiceClient vitoServiceClient;

    public VitoPublicPatientSharesBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> validate(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/public/patient-shares/validate", body), requestId, correlationId);
    }

    @GetMapping("/councils")
    public ResponseEntity<String> councils(
            @RequestParam(required = false) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String path = "/v1/public/patient-shares/councils" + (tenantId != null ? "?tenantId=" + tenantId : "");
        return forward(() -> vitoServiceClient.rawGet(path), requestId, correlationId);
    }

    @PostMapping(value = "/verify-otp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verifyOtp(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/public/patient-shares/verify-otp", body), requestId, correlationId);
    }

    @PostMapping(value = "/verify-step-up", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verifyStepUp(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = H_SESSION, required = false) String sessionToken,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPostWithHeader(
                "/v1/public/patient-shares/verify-step-up", body, H_SESSION, sessionToken), requestId, correlationId);
    }

    @PostMapping(value = "/complete-identity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> completeIdentity(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = H_SESSION, required = false) String sessionToken,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPostWithHeader(
                "/v1/public/patient-shares/complete-identity", body, H_SESSION, sessionToken), requestId, correlationId);
    }

    @GetMapping("/workspace")
    public ResponseEntity<String> workspace(
            @RequestHeader(value = H_SESSION, required = false) String sessionToken,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGetWithHeader(
                "/v1/public/patient-shares/workspace", H_SESSION, sessionToken), requestId, correlationId);
    }

    @PostMapping(value = "/contributions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> contributions(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = H_SESSION, required = false) String sessionToken,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPostWithHeader(
                "/v1/public/patient-shares/contributions", body, H_SESSION, sessionToken), requestId, correlationId);
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
