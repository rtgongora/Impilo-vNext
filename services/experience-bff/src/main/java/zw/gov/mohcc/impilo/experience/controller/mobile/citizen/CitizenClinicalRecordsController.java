package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen clinical-record reads for the mobile app — the logged-in citizen's own immunizations,
 * care plans, and referrals (G054). The patient identity comes from {@code X-Actor-ID} (the
 * citizen's CPID), as in {@code CitizenResultsController}. Each endpoint delegates to the existing
 * sovereign-service client (PCT for immunizations/referrals, inpatient for care plans) — no new
 * model, no fixtures.
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen")
public class CitizenClinicalRecordsController {

    private final PctServiceClient pctClient;
    private final InpatientServiceClient inpatientClient;

    public CitizenClinicalRecordsController(PctServiceClient pctClient,
                                            InpatientServiceClient inpatientClient) {
        this.pctClient = pctClient;
        this.inpatientClient = inpatientClient;
    }

    @GetMapping("/immunizations")
    public ResponseEntity<Map<String, Object>> immunizations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JsonNode data = pctClient.listImmunizations(actorId, page, Math.min(size, 100));
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    @GetMapping("/care-plans")
    public ResponseEntity<Map<String, Object>> carePlans(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {
        JsonNode data = inpatientClient.listCarePlans(actorId);
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    @GetMapping("/referrals")
    public ResponseEntity<Map<String, Object>> referrals(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JsonNode data = pctClient.listPatientReferrals(actorId, page, Math.min(size, 100));
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    private static Map<String, Object> envelope(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }
}
