package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.support.BffDegradedMeta;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/client-registry")
public class ClientRegistryController {

    private static final Logger log = LoggerFactory.getLogger(ClientRegistryController.class);

    private final VitoServiceClient vitoServiceClient;

    public ClientRegistryController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @GetMapping("/clients")
    public ResponseEntity<Map<String, Object>> listClients(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ok(requestId, correlationId, vitoServiceClient.listClientRegistryClients(query, status, verificationState, page, size));
        } catch (Exception e) {
            log.warn("Client registry list failed: {}", e.getMessage());
            return ok(requestId, correlationId, Map.of(
                    "items", new Object[0],
                    "page", page,
                    "size", size,
                    "totalElements", 0,
                    "totalPages", 0,
                    "hasNext", false), BffDegradedMeta.degraded(
                    requestId,
                    correlationId,
                    "vito-service",
                    "Client registry is temporarily unavailable. Use New registration for intake or retry when VITO is reachable."));
        }
    }

    @GetMapping("/clients/{healthId}")
    public ResponseEntity<Map<String, Object>> getClientProfile(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(requestId, correlationId, vitoServiceClient.getClientRegistryProfile(healthId));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "CLIENT_NOT_FOUND", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
    }

    @PostMapping("/registrations")
    public ResponseEntity<Map<String, Object>> createRegistration(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.createClientRegistration(body));
    }

    @PostMapping("/registrations/{registrationId}/submit")
    public ResponseEntity<Map<String, Object>> submitRegistration(
            @PathVariable String registrationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, vitoServiceClient.submitClientRegistration(registrationId));
    }

    @PostMapping("/clients/{healthId}/evidence")
    public ResponseEntity<Map<String, Object>> addEvidence(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.addClientEvidence(healthId, body));
    }

    @PostMapping("/clients/{healthId}/verification-reviews")
    public ResponseEntity<Map<String, Object>> recordVerificationReview(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.recordClientVerificationReview(healthId, body));
    }

    @PostMapping("/clients/{healthId}/match")
    public ResponseEntity<Map<String, Object>> runMatching(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, vitoServiceClient.runClientMatching(healthId));
    }

    @PostMapping("/match-candidates/{matchId}/review")
    public ResponseEntity<Map<String, Object>> reviewMatchCandidate(
            @PathVariable String matchId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, vitoServiceClient.reviewClientMatchCandidate(matchId, body));
    }

    @PostMapping("/merge-cases")
    public ResponseEntity<Map<String, Object>> createMergeCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.createClientMergeCase(body));
    }

    @PostMapping("/merge-cases/{mergeCaseId}/decision")
    public ResponseEntity<Map<String, Object>> decideMergeCase(
            @PathVariable String mergeCaseId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, vitoServiceClient.decideClientMergeCase(mergeCaseId, body));
    }

    @PostMapping("/clients/{healthId}/relationships")
    public ResponseEntity<Map<String, Object>> addRelationship(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.addClientRelationship(healthId, body));
    }

    @PostMapping("/clients/{healthId}/authorization-links")
    public ResponseEntity<Map<String, Object>> addAuthorizationLink(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.addClientAuthorizationLink(healthId, body));
    }

    @PostMapping("/clients/{healthId}/stewardship-actions")
    public ResponseEntity<Map<String, Object>> createStewardshipAction(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, vitoServiceClient.createClientStewardshipAction(healthId, body));
    }

    @PostMapping("/stewardship-actions/{actionId}")
    public ResponseEntity<Map<String, Object>> updateStewardshipAction(
            @PathVariable String actionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, vitoServiceClient.updateClientStewardshipAction(actionId, body));
    }

    @PostMapping("/clients/{healthId}/corrections")
    public ResponseEntity<Map<String, Object>> recordCorrection(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, vitoServiceClient.recordClientCorrection(healthId, body));
    }

    /**
     * Update a client's demographics — canonical typed route consolidating the retired Stack B
     * raw passthrough ({@code /internal/v1/vito/client-registry/clients/{healthId}/demographics}).
     * Proxies VITO's SoR write {@code PUT /v1/clients/{healthId}} (G14).
     */
    @PutMapping("/clients/{healthId}/demographics")
    public ResponseEntity<Map<String, Object>> updateDemographics(
            @PathVariable String healthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, vitoServiceClient.updateClientDemographics(healthId, body));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<Map<String, Object>> dashboardSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(requestId, correlationId, vitoServiceClient.getClientRegistryDashboard());
        } catch (Exception e) {
            log.warn("Client registry dashboard failed: {}", e.getMessage());
            return ok(requestId, correlationId, Map.of(
                    "totalClients", 0,
                    "provisionalClients", 0,
                    "pendingVerification", 0,
                    "pendingMatchReview", 0,
                    "openStewardshipActions", 0,
                    "mergeCasesOpen", 0,
                    "clientsByStatus", Map.of()), BffDegradedMeta.degraded(
                    requestId,
                    correlationId,
                    "vito-service",
                    "Client registry metrics are unavailable. List and intake flows may still work when VITO recovers."));
        }
    }

    @GetMapping("/stewardship/workspace")
    public ResponseEntity<Map<String, Object>> stewardshipWorkspace(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = vitoServiceClient.getClientStewardshipWorkspace();
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("Client stewardship workspace failed: {}", e.getMessage());
            return ok(requestId, correlationId, Map.of(
                    "duplicateQueue", new Object[0],
                    "verificationQueue", new Object[0],
                    "stewardshipQueue", new Object[0]
            ));
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object data) {
        return ok(requestId, correlationId, data, BffDegradedMeta.base(requestId, correlationId));
    }

    private ResponseEntity<Map<String, Object>> ok(
            String requestId, String correlationId, Object data, Map<String, Object> meta) {
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", meta
        ));
    }

    private ResponseEntity<Map<String, Object>> created(String requestId, String correlationId, Object data) {
        return ResponseEntity.status(201).body(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
