package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen coverage/insurance endpoints.
 * GET /internal/v1/mobile/citizen/coverage
 * GET /internal/v1/mobile/citizen/coverage/{id}
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/coverage")
public class CitizenCoverageController {

    private final CoverageServiceClient coverageClient;

    public CitizenCoverageController(CoverageServiceClient coverageClient) {
        this.coverageClient = coverageClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        JsonNode coverage = coverageClient.getCitizenCoverage(actorId);

        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., tenantId, patientId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", coverage);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode plan = coverageClient.getPlan(id.toString());

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", plan);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
