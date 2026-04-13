package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ButanoServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen health summary endpoint — aggregated health overview.
 *
 * <p>Delegates to BUTANO (IPS) and PCT for canonical health summary data.</p>
 *
 * <p>GET /internal/v1/mobile/citizen/summary</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/summary")
public class CitizenHealthSummaryController {

    private final ButanoServiceClient butanoClient;
    private final PctServiceClient pctClient;

        this.butanoClient = butanoClient;
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealthSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        JsonNode summary = pctClient.getPatientHealthSummary(actorId);

        // patients, conditions, allergies, prescriptions, vitals_records, lab_orders,
        // encounters, citizen_telehealth_sessions, clinical_documents tables
        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> conditions = jdbcTemplate.queryForList(...);
        // List<Map<String, Object>> allergies = jdbcTemplate.queryForList(...);
        // ... etc.

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", summary);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
