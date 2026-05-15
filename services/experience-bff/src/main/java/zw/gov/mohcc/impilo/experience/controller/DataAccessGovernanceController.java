package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DagsServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF controller for the Data Access Governance Service (DAGS).
 *
 * <p>Implements Privacy by Architecture (Health OS Doctrine Brief §6) by
 * proxying governance-policy and access-request workflows to the DAGS
 * sovereign service. All responses use the standard BFF envelope with
 * {@code data} + {@code meta}.</p>
 *
 * <p>Request mapping prefix: {@code /internal/v1/governance/access}</p>
 */
@RestController
@RequestMapping("/internal/v1/governance/access")
public class DataAccessGovernanceController {

    private final DagsServiceClient dagsClient;

    public DataAccessGovernanceController(DagsServiceClient dagsClient) {
        this.dagsClient = dagsClient;
    }

    // ------------------------------------------------------------------ Policies

    /**
     * GET /internal/v1/governance/access/policies — list governance policies.
     */
    @GetMapping("/policies")
    public ResponseEntity<Map<String, Object>> listPolicies(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = dagsClient.listPolicies();
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure("DAGS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * POST /internal/v1/governance/access/policies — create a governance policy.
     */
    @PostMapping("/policies")
    public ResponseEntity<Map<String, Object>> createPolicy(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = dagsClient.createPolicy(body);
            Map<String, Object> response = envelope(data, requestId, correlationId);
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            return upstreamFailure("DAGS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // -------------------------------------------------------------- Access Requests

    /**
     * GET /internal/v1/governance/access/requests — list access requests.
     */
    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> listAccessRequests(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            JsonNode data = dagsClient.listAccessRequests(status, page, Math.min(size, 200));
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure("DAGS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * POST /internal/v1/governance/access/requests — submit an access request.
     */
    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> submitAccessRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = dagsClient.submitAccessRequest(body);
            Map<String, Object> response = envelope(data, requestId, correlationId);
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            return upstreamFailure("DAGS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * POST /internal/v1/governance/access/requests/{id}/approve — approve an access request.
     */
    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveAccessRequest(
            @PathVariable Long id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = dagsClient.approveAccessRequest(id, body != null ? body : Map.of());
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure("DAGS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * POST /internal/v1/governance/access/requests/{id}/deny — deny an access request.
     */
    @PostMapping("/requests/{id}/deny")
    public ResponseEntity<Map<String, Object>> denyAccessRequest(
            @PathVariable Long id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = dagsClient.denyAccessRequest(id, body != null ? body : Map.of());
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            return upstreamFailure("DAGS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ------------------------------------------------------------------ Helpers

    private Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data != null ? data : Map.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return response;
    }

    private Map<String, Object> errorEnvelope(String message, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", message);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return response;
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Access governance upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
