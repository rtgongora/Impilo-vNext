package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

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


    private final ObjectMapper objectMapper;
    private final PctServiceClient pctClient;
    private final InpatientServiceClient inpatientClient;

    public StructuredHistoryController(ObjectMapper objectMapper, PctServiceClient pctClient,
                                       InpatientServiceClient inpatientClient) {
        this.objectMapper = objectMapper;
        this.pctClient = pctClient;
        this.inpatientClient = inpatientClient;
    }

    private static UUID parsePatientId(String patientId) {
        try {
            return UUID.fromString(patientId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid patient_id");
        }
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        );
    }

    /**
     * Every endpoint here falls through to an empty history when the upstream answers and holds
     * nothing. That fallback is only honest when the upstream actually answered — a failed call
     * rendered as an empty history is an affirmative clinical claim ("no surgery", "no advance
     * directive") made at the moment the system knows least. Failures land here instead, and say
     * who owes the capability.
     */
    private static ResponseEntity<Map<String, Object>> upstreamUnavailable(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", code,
                "message", message,
                "meta", meta(requestId, correlationId)));
    }

    @GetMapping("/social-history")
    public ResponseEntity<Map<String, Object>> socialHistory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        parsePatientId(patientIdParam);
        try {
            JsonNode pctData = pctClient.getSocialHistory(patientIdParam);
            if (!pctPayloadMissingOrEmpty(pctData)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", pctData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            log.error("PCT getSocialHistory failed for patient={}: {}", patientIdParam, e.getMessage());
            return upstreamUnavailable("social_history_unavailable",
                    "Social history could not be retrieved. Do not treat this as an absence of "
                    + "social history.", requestId, correlationId);
        }
        return emptyHistory(requestId, correlationId);
    }

    @GetMapping("/family-history")
    public ResponseEntity<Map<String, Object>> familyHistory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {

        parsePatientId(patientIdParam);
        try {
            JsonNode pctData = pctClient.getFamilyHistory(patientIdParam);
            if (!pctPayloadMissingOrEmpty(pctData)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", pctData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            log.error("PCT getFamilyHistory failed for patient={}: {}", patientIdParam, e.getMessage());
            return upstreamUnavailable("family_history_unavailable",
                    "Family history could not be retrieved. Do not treat this as an absence of "
                    + "family history.", requestId, correlationId);
        }
        return emptyHistory(requestId, correlationId);
    }

    private Map<UUID, List<Map<String, Object>>> loadFamilyConditions(List<UUID> memberIds) {
        // Experience BFF is a pure proxy with no database. Family condition rows are owned by PCT
        // and should be returned by PCT endpoints. Until those endpoints are available, return empty.
        return Map.of();
    }

    @GetMapping("/functional-assessments")
    public ResponseEntity<Map<String, Object>> functionalAssessments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        parsePatientId(patientIdParam);
        try {
            JsonNode pctData = pctClient.getFunctionalAssessments(patientIdParam);
            if (!pctPayloadMissingOrEmpty(pctData)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", pctData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            log.error("PCT getFunctionalAssessments failed for patient={}: {}",
                    patientIdParam, e.getMessage());
            return upstreamUnavailable("functional_assessments_unavailable",
                    "Functional assessments could not be retrieved. Do not treat this as an "
                    + "absence of assessments.", requestId, correlationId);
        }
        return emptyHistory(requestId, correlationId);
    }

    @GetMapping("/procedures")
    public ResponseEntity<Map<String, Object>> procedures(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        parsePatientId(patientIdParam);
        // Procedures are assembled from two sources. A source that could not be reached leaves a
        // hole we cannot see into, so an empty result is only reportable when both sources
        // actually answered — otherwise "no surgical history" would be a fabricated finding.
        boolean sourceUnreachable = false;
        try {
            JsonNode inpatientData = inpatientClient.listProcedureHistory(patientIdParam);
            if (inpatientData != null && inpatientData.isArray() && !inpatientData.isEmpty()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", inpatientData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            sourceUnreachable = true;
            log.error("Inpatient listProcedureHistory failed for patient={}: {}",
                    patientIdParam, e.getMessage());
        }
        try {
            JsonNode pctData = pctClient.getProcedures(patientIdParam);
            if (!pctPayloadMissingOrEmpty(pctData)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", pctData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            sourceUnreachable = true;
            log.error("PCT getProcedures failed for patient={}: {}", patientIdParam, e.getMessage());
        }
        if (sourceUnreachable) {
            return upstreamUnavailable("procedure_history_unavailable",
                    "Procedure history could not be retrieved. Do not treat this as an absence "
                    + "of procedures.", requestId, correlationId);
        }
        return emptyHistory(requestId, correlationId);
    }

    @GetMapping("/advance-directives")
    public ResponseEntity<Map<String, Object>> advanceDirectives(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        parsePatientId(patientIdParam);
        try {
            JsonNode pctData = pctClient.getAdvanceDirectives(patientIdParam);
            if (!pctPayloadMissingOrEmpty(pctData)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", pctData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no advance directive on file", which
            // is the finding a resuscitation decision turns on. Never fabricate it.
            log.error("PCT getAdvanceDirectives failed for patient={}: {}",
                    patientIdParam, e.getMessage());
            return upstreamUnavailable("advance_directives_unavailable",
                    "Advance directives could not be retrieved. Do not treat this as an absence "
                    + "of a directive.", requestId, correlationId);
        }
        return emptyHistory(requestId, correlationId);
    }

    private static boolean pctPayloadMissingOrEmpty(JsonNode pctData) {
        if (pctData == null || pctData.isNull()) {
            return true;
        }
        return pctData.isArray() && pctData.isEmpty();
    }

    private static ResponseEntity<Map<String, Object>> emptyHistory(String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of("data", List.of(), "meta", meta(requestId, correlationId)));
    }

}
