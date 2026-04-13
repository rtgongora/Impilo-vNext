package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Patient structured history for EHR continuity pages.
 * <ul>
 *   <li>GET /internal/v1/ehr/social-history?patient_id=</li>
 *   <li>GET /internal/v1/ehr/family-history?patient_id=</li>
 *   <li>GET /internal/v1/ehr/functional-assessments?patient_id=</li>
 *   <li>GET /internal/v1/ehr/procedures?patient_id=</li>
 *   <li>GET /internal/v1/ehr/advance-directives?patient_id=</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/ehr")
public class StructuredHistoryController {

    private static final Logger log = LoggerFactory.getLogger(StructuredHistoryController.class);

    private final PctServiceClient pctClient;

    public StructuredHistoryController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        );
    }

    @GetMapping("/social-history")
    public ResponseEntity<Map<String, Object>> socialHistory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        try {
            JsonNode pctData = pctClient.getSocialHistory(patientIdParam);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("PCT getSocialHistory failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("data", List.of(), "meta", meta(requestId, correlationId)));
    }

    @GetMapping("/family-history")
    public ResponseEntity<Map<String, Object>> familyHistory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        try {
            JsonNode pctData = pctClient.getFamilyHistory(patientIdParam);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("PCT getFamilyHistory failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("data", List.of(), "meta", meta(requestId, correlationId)));
    }

    @GetMapping("/functional-assessments")
    public ResponseEntity<Map<String, Object>> functionalAssessments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        try {
            JsonNode pctData = pctClient.getFunctionalAssessments(patientIdParam);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("PCT getFunctionalAssessments failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("data", List.of(), "meta", meta(requestId, correlationId)));
    }

    @GetMapping("/procedures")
    public ResponseEntity<Map<String, Object>> procedures(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        try {
            JsonNode pctData = pctClient.getProcedures(patientIdParam);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("PCT getProcedures failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("data", List.of(), "meta", meta(requestId, correlationId)));
    }

    @GetMapping("/advance-directives")
    public ResponseEntity<Map<String, Object>> advanceDirectives(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        try {
            JsonNode pctData = pctClient.getAdvanceDirectives(patientIdParam);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("PCT getAdvanceDirectives failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("data", List.of(), "meta", meta(requestId, correlationId)));
    }
}
