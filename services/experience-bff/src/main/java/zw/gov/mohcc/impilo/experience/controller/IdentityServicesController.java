package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.Map;

/**
 * Identity Services BFF Controller â€” bridges Lovable ID Services Hub
 * to VITO (patient identity), VARAPI (provider identity), and
 * TSHEPO-IDENTITY (CPID generation).
 *
 * Endpoints:
 * - POST /internal/v1/identity/patient/register â€” register patient PHID
 * - POST /internal/v1/identity/patient/resolve â€” resolve health ID â†’ CPID
 * - POST /internal/v1/identity/patient/recovery/start â€” start ID recovery
 * - POST /internal/v1/identity/patient/recovery/verify â€” verify recovery
 * - POST /internal/v1/identity/provider/create â€” create provider ID
 * - GET  /internal/v1/identity/provider/{id} â€” get provider identity
 * - GET  /internal/v1/identity/search â€” search identities
 */
@RestController
@RequestMapping("/internal/v1/identity")
public class IdentityServicesController {

    private static final Logger log = LoggerFactory.getLogger(IdentityServicesController.class);

    private final VitoServiceClient vitoClient;
    private final VarapiServiceClient varapiClient;

    public IdentityServicesController(VitoServiceClient vitoClient, VarapiServiceClient varapiClient) {
        this.vitoClient = vitoClient;
        this.varapiClient = varapiClient;
    }

    // â”€â”€ Patient Identity (VITO) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/patient/register")
    public ResponseEntity<Map<String, Object>> registerPatientIdentity(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode result = vitoClient.registerIdentity(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Patient identity registration failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "REGISTRATION_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/patient/resolve")
    public ResponseEntity<Map<String, Object>> resolvePatientIdentity(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {
        try {
            String healthId = body.get("healthId");
            JsonNode result = vitoClient.resolveIdentity(healthId);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Identity resolution failed: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Identity not found")));
        }
    }

    @PostMapping("/patient/recovery/start")
    public ResponseEntity<Map<String, Object>> startRecovery(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode result = vitoClient.startRecovery(body);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("ID recovery start failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "RECOVERY_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/patient/recovery/verify")
    public ResponseEntity<Map<String, Object>> verifyRecovery(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode result = vitoClient.verifyRecovery(body);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("ID recovery verify failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "VERIFY_FAILED", "message", e.getMessage())));
        }
    }

    // â”€â”€ Provider Identity (VARAPI) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/provider/create")
    public ResponseEntity<Map<String, Object>> createProviderIdentity(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            // VARAPI provider creation â€” delegates to POST /v1/internal/providers
            JsonNode result = varapiClient.createProvider(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Provider identity creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CREATION_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/provider/{id}")
    public ResponseEntity<Map<String, Object>> getProviderIdentity(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = varapiClient.getProvider(id);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Provider not found")));
        }
    }

    // â”€â”€ Search â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchIdentities(
            @RequestParam String query,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = vitoClient.searchClients(query);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}

