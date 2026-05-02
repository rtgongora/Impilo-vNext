package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/vito/client-registry")
public class VitoClientRegistryBffController {

    private final VitoServiceClient vitoServiceClient;

    public VitoClientRegistryBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @GetMapping("/clients")
    public ResponseEntity<String> listClients(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        StringBuilder path = new StringBuilder("/v1/client-registry/clients?page=").append(page).append("&size=").append(size);
        if (query != null) path.append("&query=").append(query);
        if (status != null) path.append("&status=").append(status);
        if (verificationState != null) path.append("&verificationState=").append(verificationState);
        return forward(() -> vitoServiceClient.rawGet(path.toString()), requestId, correlationId);
    }

    @GetMapping("/clients/{healthId}")
    public ResponseEntity<String> getClient(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/client-registry/clients/" + healthId), requestId, correlationId);
    }

    @GetMapping("/clients/{healthId}/identity-summary")
    public ResponseEntity<String> getIdentitySummary(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/client-registry/clients/" + healthId + "/identity-summary"), requestId, correlationId);
    }

    @PostMapping(value = "/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createRegistration(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/registrations", body), requestId, correlationId);
    }

    @PostMapping(value = "/registrations/{id}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> submitRegistration(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/registrations/" + id + "/submit", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/evidence", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addEvidence(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/evidence", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/verification-reviews", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createVerificationReview(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/verification-reviews", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerMatch(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/match", body), requestId, correlationId);
    }

    @PostMapping(value = "/match-candidates/{matchId}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reviewMatchCandidate(
            @PathVariable String matchId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/match-candidates/" + matchId + "/review", body), requestId, correlationId);
    }

    @PostMapping(value = "/merge-cases", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createMergeCase(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/merge-cases", body), requestId, correlationId);
    }

    @PostMapping(value = "/merge-cases/{caseId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> mergeCaseDecision(
            @PathVariable String caseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/merge-cases/" + caseId + "/decision", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/relationships", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addRelationship(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/relationships", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/authorization-links", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addAuthorizationLink(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/authorization-links", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/stewardship-actions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createStewardshipAction(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/stewardship-actions", body), requestId, correlationId);
    }

    @PostMapping(value = "/clients/{healthId}/corrections", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordCorrection(
            @PathVariable String healthId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/client-registry/clients/" + healthId + "/corrections", body), requestId, correlationId);
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<String> getDashboardSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/client-registry/dashboard/summary"), requestId, correlationId);
    }

    @GetMapping("/stewardship/workspace")
    public ResponseEntity<String> getStewardshipWorkspace(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/client-registry/stewardship/workspace"), requestId, correlationId);
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
