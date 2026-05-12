package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/vito/registry")
public class VitoRegistryAdminBffController {

    private final VitoServiceClient vitoServiceClient;

    public VitoRegistryAdminBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @GetMapping("/mode")
    public ResponseEntity<String> getMode(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/registry/mode"), requestId, correlationId);
    }

    @PostMapping(value = "/provisional/issue", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> issueProvisional(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/registry/provisional/issue", body), requestId, correlationId);
    }

    @PostMapping(value = "/provisional/{ref}/reconcile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reconcileProvisional(
            @PathVariable String ref,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/registry/provisional/" + ref + "/reconcile", body), requestId, correlationId);
    }

    @GetMapping("/provisional/pending")
    public ResponseEntity<String> getPendingProvisional(
            @RequestParam(required = false) String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        StringBuilder path = new StringBuilder("/v1/registry/provisional/pending?page=").append(page).append("&size=").append(size);
        if (facilityId != null && !facilityId.isBlank()) path.append("&facilityId=").append(facilityId);
        return forward(() -> vitoServiceClient.rawGet(path.toString()), requestId, correlationId);
    }

    @GetMapping("/dedup/pending")
    public ResponseEntity<String> getPendingDedup(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawGet("/v1/registry/dedup/pending?page=" + page + "&size=" + size), requestId, correlationId);
    }

    @PostMapping(value = "/dedup/{caseId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resolveDedupCase(
            @PathVariable String caseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/registry/dedup/" + caseId + "/resolve", body), requestId, correlationId);
    }

    @PostMapping(value = "/opencr/match", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> openCrMatch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward(() -> vitoServiceClient.rawPost("/v1/registry/opencr/match", body), requestId, correlationId);
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
