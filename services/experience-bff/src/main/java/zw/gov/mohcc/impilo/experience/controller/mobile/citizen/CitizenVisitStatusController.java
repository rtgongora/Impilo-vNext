package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.patientlane.PatientLaneService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Citizen patient-lane status (SYS-3 / G-CT-01). Lets a citizen follow their own visit:
 *
 * <ul>
 *   <li>GET /internal/v1/mobile/citizen/visit/{transactionId}/status — outpatient journey</li>
 *   <li>GET /internal/v1/mobile/citizen/inpatient/{admissionRef}/status — inpatient stay</li>
 * </ul>
 *
 * <p>Both compose the canonical state from the sovereign services (PCT, inpatient) and return a
 * plain-language, person-centered status. The BFF holds no state.
 */
@RestController
public class CitizenVisitStatusController {

    private final PatientLaneService patientLane;

    public CitizenVisitStatusController(PatientLaneService patientLane) {
        this.patientLane = patientLane;
    }

    @GetMapping("/internal/v1/mobile/citizen/visit/{transactionId}/status")
    public ResponseEntity<Map<String, Object>> visitStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String transactionId) {
        return ok(patientLane.visitStatus(transactionId), requestId, correlationId);
    }

    @GetMapping("/internal/v1/mobile/citizen/inpatient/{admissionRef}/status")
    public ResponseEntity<Map<String, Object>> inpatientStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String admissionRef) {
        return ok(patientLane.inpatientStatus(admissionRef), requestId, correlationId);
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
