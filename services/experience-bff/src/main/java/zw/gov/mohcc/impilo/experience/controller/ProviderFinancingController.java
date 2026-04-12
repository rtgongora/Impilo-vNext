package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

/**
 * BFF proxy for provider contracts and networks (coverage-service /internal/v1).
 */
@RestController
@RequestMapping("/internal/v1")
public class ProviderFinancingController {

    private static final Logger log = LoggerFactory.getLogger(ProviderFinancingController.class);
    private final CoverageServiceClient coverageClient;

    public ProviderFinancingController(CoverageServiceClient coverageClient) {
        this.coverageClient = coverageClient;
    }

    @GetMapping("/provider-contracts")
    public ResponseEntity<Map<String, Object>> listContracts(
            @RequestParam(required = false) String provider_id,
            @RequestParam(required = false) String payer_id,
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listProviderContracts(provider_id, payer_id, status);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("listContracts: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @PostMapping("/provider-contracts")
    public ResponseEntity<Map<String, Object>> createContract(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.createProviderContract(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("createContract: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "CONTRACT_CREATE_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * Experience-layer suspend — maps to coverage-service POST .../suspend with a reason payload.
     */
    @DeleteMapping("/provider-contracts/{id}")
    public ResponseEntity<Map<String, Object>> suspendContract(
            @PathVariable("id") String contractId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String reason = "Suspended via Experience UI";
            if (body != null) {
                Object r = body.get("reason");
                if (r instanceof String s && !s.isBlank()) {
                    reason = s;
                }
            }
            JsonNode data = coverageClient.suspendProviderContract(contractId, Map.of("reason", reason));
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("suspendContract: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "SUSPEND_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/provider-contracts/{id}/reinstate")
    public ResponseEntity<Map<String, Object>> reinstateContract(
            @PathVariable("id") String contractId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.reinstateProviderContract(contractId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("reinstateContract: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "REINSTATE_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/provider-networks")
    public ResponseEntity<Map<String, Object>> listNetworks(
            @RequestParam(required = false) String payer_id,
            @RequestParam(required = false) String status,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listProviderNetworks(payer_id, status);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("listNetworks: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @PostMapping("/provider-networks")
    public ResponseEntity<Map<String, Object>> createNetwork(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.createProviderNetwork(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("createNetwork: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "NETWORK_CREATE_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/provider-networks/{id}/members")
    public ResponseEntity<Map<String, Object>> listNetworkMembers(
            @PathVariable("id") String networkId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listNetworkMembers(networkId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @PostMapping("/provider-networks/{id}/members")
    public ResponseEntity<Map<String, Object>> addNetworkMember(
            @PathVariable("id") String networkId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.addNetworkMember(networkId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("addNetworkMember: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "MEMBER_ADD_FAILED", "message", e.getMessage())));
        }
    }

    @DeleteMapping("/provider-networks/{id}/members/{providerId}")
    public ResponseEntity<Map<String, Object>> removeNetworkMember(
            @PathVariable("id") String networkId,
            @PathVariable String providerId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            coverageClient.removeNetworkMember(networkId, providerId);
            return ResponseEntity.ok(Map.of("data", new LinkedHashMap<>(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("removeNetworkMember: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "MEMBER_REMOVE_FAILED", "message", e.getMessage())));
        }
    }
}
