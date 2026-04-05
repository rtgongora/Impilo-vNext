package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

import java.util.Map;

/**
 * Coverage BFF Controller — bridges experience UI to the
 * coverage sovereign service for plans, eligibility, claims,
 * preauth, and remittance.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class CoverageController {

    private static final Logger log = LoggerFactory.getLogger(CoverageController.class);
    private final CoverageServiceClient coverageClient;

    public CoverageController(CoverageServiceClient coverageClient) {
        this.coverageClient = coverageClient;
    }

    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> listPlans(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listPlans();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<Map<String, Object>> getPlan(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getPlan(id);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    @GetMapping("/member/{clientId}")
    public ResponseEntity<Map<String, Object>> getMemberCoverage(@PathVariable String clientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getMemberCoverage(clientId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @PostMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> checkEligibility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.checkEligibility(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Eligibility check failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "CHECK_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/claims")
    public ResponseEntity<Map<String, Object>> submitClaim(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.submitClaim(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Claim submission failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "SUBMIT_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/claims/{id}")
    public ResponseEntity<Map<String, Object>> getClaim(@PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.getClaim(id);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    @GetMapping("/claims")
    public ResponseEntity<Map<String, Object>> listClaims(
            @RequestParam(required = false) String coverageId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageId != null ? coverageClient.listClaims(coverageId) : coverageClient.listPlans();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @PostMapping("/preauth")
    public ResponseEntity<Map<String, Object>> createPreauth(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.createPreauth(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Preauth creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "PREAUTH_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/remittances")
    public ResponseEntity<Map<String, Object>> listRemittances(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = coverageClient.listRemittances();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    // ── Membership ───────────────────────────────────────────────

    @PostMapping("/members")
    public ResponseEntity<Map<String, Object>> enrollMember(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.enrollMember(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Member enrollment failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "ENROLL_FAILED", "message", e.getMessage())));
        }
    }

    @GetMapping("/members")
    public ResponseEntity<Map<String, Object>> listMembers(
            @RequestParam(required = false, name = "plan_id") String planId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = planId != null ? coverageClient.listMembers(planId) : coverageClient.listPlans();
            return ResponseEntity.ok(Map.of("data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }
}
