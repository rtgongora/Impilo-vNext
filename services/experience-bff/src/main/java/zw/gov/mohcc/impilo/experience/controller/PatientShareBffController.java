package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;
import java.util.UUID;

/**
 * BFF proxy for patient-mediated record sharing (VITO) — citizen lane and public external-provider lane.
 */
@RestController
public class PatientShareBffController {

    /** Must match VITO {@code PublicPatientShareController#H_SESSION}. */
    public static final String H_PATIENT_SHARE_SESSION = "X-Patient-Share-Session";

    private final VitoServiceClient vitoServiceClient;

    public PatientShareBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    // ── Citizen (authenticated) ─────────────────────────────────────────

    @PostMapping("/internal/v1/citizen/clients/{healthId}/patient-shares")
    public ResponseEntity<String> citizenCreate(
            @PathVariable UUID healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawPost("/v1/clients/" + healthId + "/patient-shares", body),
                requestId,
                correlationId);
    }

    @GetMapping("/internal/v1/citizen/clients/{healthId}/patient-shares")
    public ResponseEntity<String> citizenList(
            @PathVariable UUID healthId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawGet("/v1/clients/" + healthId + "/patient-shares"),
                requestId,
                correlationId);
    }

    @PostMapping("/internal/v1/citizen/clients/{healthId}/patient-shares/{shareRequestId}/revoke")
    public ResponseEntity<String> citizenRevoke(
            @PathVariable UUID healthId,
            @PathVariable long shareRequestId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawPost(
                        "/v1/clients/" + healthId + "/patient-shares/" + shareRequestId + "/revoke",
                        body != null ? body : Map.of()),
                requestId,
                correlationId);
    }

    @GetMapping("/internal/v1/citizen/clients/{healthId}/patient-shares/{shareRequestId}/contributions")
    public ResponseEntity<String> citizenContributions(
            @PathVariable UUID healthId,
            @PathVariable long shareRequestId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawGet(
                        "/v1/clients/" + healthId + "/patient-shares/" + shareRequestId + "/contributions"),
                requestId,
                correlationId);
    }

    // ── Public external provider (unauthenticated; session header on later steps) ──

    @PostMapping("/internal/v1/public/patient-shares/validate")
    public ResponseEntity<String> publicValidate(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/public/patient-shares/validate", body), requestId, correlationId);
    }

    @PostMapping("/internal/v1/public/patient-shares/verify-otp")
    public ResponseEntity<String> publicVerifyOtp(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/public/patient-shares/verify-otp", body), requestId, correlationId);
    }

    @GetMapping("/internal/v1/public/patient-shares/councils")
    public ResponseEntity<String> publicCouncils(
            @RequestParam("tenantId") UUID tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        String path = UriComponentsBuilder.fromPath("/v1/public/patient-shares/councils")
                .queryParam("tenantId", tenantId)
                .toUriString();
        return forward(() -> vitoServiceClient.rawGet(path), requestId, correlationId);
    }

    @PostMapping("/internal/v1/public/patient-shares/verify-step-up")
    public ResponseEntity<String> publicVerifyStepUp(
            @RequestHeader(H_PATIENT_SHARE_SESSION) String session,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawPostWithHeader(
                        "/v1/public/patient-shares/verify-step-up", body, H_PATIENT_SHARE_SESSION, session),
                requestId,
                correlationId);
    }

    @PostMapping("/internal/v1/public/patient-shares/complete-identity")
    public ResponseEntity<String> publicComplete(
            @RequestHeader(H_PATIENT_SHARE_SESSION) String session,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawPostWithHeader(
                        "/v1/public/patient-shares/complete-identity",
                        body,
                        H_PATIENT_SHARE_SESSION,
                        session),
                requestId,
                correlationId);
    }

    @GetMapping("/internal/v1/public/patient-shares/workspace")
    public ResponseEntity<String> publicWorkspace(
            @RequestHeader(H_PATIENT_SHARE_SESSION) String session,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawGetWithHeader(
                        "/v1/public/patient-shares/workspace", H_PATIENT_SHARE_SESSION, session),
                requestId,
                correlationId);
    }

    @PostMapping("/internal/v1/public/patient-shares/contributions")
    public ResponseEntity<String> publicContribute(
            @RequestHeader(H_PATIENT_SHARE_SESSION) String session,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return forward(
                () -> vitoServiceClient.rawPostWithHeader(
                        "/v1/public/patient-shares/contributions",
                        body,
                        H_PATIENT_SHARE_SESSION,
                        session),
                requestId,
                correlationId);
    }

    @FunctionalInterface
    private interface SupplierEx {
        ResponseEntity<String> get();
    }

    private static ResponseEntity<String> forward(SupplierEx call, String requestId, String correlationId) {
        String rid = requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
        String cid = correlationId != null && !correlationId.isBlank() ? correlationId : UUID.randomUUID().toString();
        try {
            ResponseEntity<String> upstream = call.get();
            return copy(upstream, rid, cid);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, rid)
                    .header(CompanionHeaders.CORRELATION_ID, cid)
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
