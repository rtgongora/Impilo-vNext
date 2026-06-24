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

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    /** Golden-path demo patient — offline structured rows when PCT has no data. */
    private static final String GOLDEN_PATH_DEMO_PATIENT = "a1000000-0000-0000-0000-000000000001";

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

    private static String dateStr(Date d) {
        return d == null ? null : d.toLocalDate().toString();
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
            log.warn("PCT getSocialHistory failed: {}", e.getMessage());
        }
        return demoSocialHistory(patientIdParam, requestId, correlationId);
    }

    private Map<String, Object> socialRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class).toString());
        m.put("category", rs.getString("category"));
        m.put("icon", rs.getString("icon"));
        m.put("status", rs.getString("status"));
        m.put("detail", rs.getString("detail"));
        m.put("lastUpdated", dateStr(rs.getDate("last_updated")));
        m.put("riskLevel", rs.getString("risk_level"));
        return m;
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
            log.warn("PCT getFamilyHistory failed: {}", e.getMessage());
        }
        return demoFamilyHistory(patientIdParam, requestId, correlationId);
    }

    private Map<String, Object> familyMemberRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class).toString());
        m.put("name", rs.getString("name"));
        m.put("relationship", rs.getString("relationship"));
        int age = rs.getInt("age");
        m.put("age", rs.wasNull() ? null : age);
        m.put("deceased", Boolean.TRUE.equals(rs.getObject("deceased", Boolean.class)));
        int da = rs.getInt("deceased_age");
        m.put("deceasedAge", rs.wasNull() ? null : da);
        m.put("causeOfDeath", rs.getString("cause_of_death"));
        return m;
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
            log.warn("PCT getFunctionalAssessments failed: {}", e.getMessage());
        }
        return demoFunctionalAssessments(patientIdParam, requestId, correlationId);
    }

    private Map<String, Object> functionalRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class).toString());
        m.put("type", rs.getString("assessment_type"));
        m.put("date", dateStr(rs.getDate("assessment_date")));
        m.put("assessor", rs.getString("assessor"));
        m.put("totalScore", rs.getInt("total_score"));
        m.put("maxScore", rs.getInt("max_score"));
        m.put("interpretation", rs.getString("interpretation"));
        String json = rs.getString("activities_json");
        List<Map<String, Object>> activities = List.of();
        if (json != null && !json.isBlank()) {
            try {
                activities = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
                });
            } catch (Exception ignored) {
                activities = List.of();
            }
        }
        m.put("activities", activities);
        return m;
    }

    @GetMapping("/procedures")
    public ResponseEntity<Map<String, Object>> procedures(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientIdParam) {
        parsePatientId(patientIdParam);
        try {
            JsonNode inpatientData = inpatientClient.listProcedureHistory(patientIdParam);
            if (inpatientData != null && inpatientData.isArray() && !inpatientData.isEmpty()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("data", inpatientData);
                body.put("meta", meta(requestId, correlationId));
                return ResponseEntity.ok(body);
            }
        } catch (Exception e) {
            log.warn("Inpatient listProcedureHistory failed: {}", e.getMessage());
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
            log.warn("PCT getProcedures failed: {}", e.getMessage());
        }
        return demoProcedures(patientIdParam, requestId, correlationId);
    }

    private Map<String, Object> procedureRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class).toString());
        m.put("name", rs.getString("name"));
        m.put("type", rs.getString("procedure_type"));
        m.put("date", dateStr(rs.getDate("procedure_date")));
        m.put("surgeon", rs.getString("surgeon"));
        m.put("facility", rs.getString("facility"));
        m.put("status", rs.getString("status"));
        m.put("notes", rs.getString("notes"));
        return m;
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
            log.warn("PCT getAdvanceDirectives failed: {}", e.getMessage());
        }
        return demoAdvanceDirectives(patientIdParam, requestId, correlationId);
    }

    private Map<String, Object> directiveRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getObject("id", UUID.class).toString());
        m.put("type", rs.getString("directive_type"));
        m.put("status", rs.getString("status"));
        m.put("effectiveDate", dateStr(rs.getDate("effective_date")));
        m.put("reviewDate", dateStr(rs.getDate("review_date")));
        m.put("documentRef", rs.getString("document_ref"));
        m.put("summary", rs.getString("summary"));
        m.put("contact", rs.getString("contact"));
        m.put("contactRelation", rs.getString("contact_relation"));
        m.put("contactPhone", rs.getString("contact_phone"));
        return m;
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

    private ResponseEntity<Map<String, Object>> demoSocialHistory(
            String patientIdParam, String requestId, String correlationId) {
        if (!GOLDEN_PATH_DEMO_PATIENT.equals(patientIdParam)) {
            return emptyHistory(requestId, correlationId);
        }
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", "soc-demo-1",
                "category", "Tobacco",
                "icon", "smoke",
                "status", "UNKNOWN",
                "detail", "Not assessed",
                "lastUpdated", "2026-01-01",
                "riskLevel", "UNKNOWN"));
        return ResponseEntity.ok(Map.of("data", rows, "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> demoFamilyHistory(
            String patientIdParam, String requestId, String correlationId) {
        if (!GOLDEN_PATH_DEMO_PATIENT.equals(patientIdParam)) {
            return emptyHistory(requestId, correlationId);
        }
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", "fam-demo-1",
                "name", "Parent",
                "relationship", "Mother",
                "age", 72,
                "deceased", false,
                "conditions", List.of(Map.of("code", "E11", "label", "Type 2 diabetes"))));
        return ResponseEntity.ok(Map.of("data", rows, "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> demoFunctionalAssessments(
            String patientIdParam, String requestId, String correlationId) {
        if (!GOLDEN_PATH_DEMO_PATIENT.equals(patientIdParam)) {
            return emptyHistory(requestId, correlationId);
        }
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", "func-demo-1",
                "type", "ADL",
                "date", "2026-01-15",
                "assessor", "Sample data (demo)",
                "totalScore", 18,
                "maxScore", 24,
                "interpretation", "Independent with aids",
                "activities", List.of(Map.of("code", "MOBILITY", "score", 4))));
        return ResponseEntity.ok(Map.of("data", rows, "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> demoProcedures(
            String patientIdParam, String requestId, String correlationId) {
        if (!GOLDEN_PATH_DEMO_PATIENT.equals(patientIdParam)) {
            return emptyHistory(requestId, correlationId);
        }
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", "proc-demo-1",
                "name", "Appendectomy",
                "type", "Surgical",
                "date", "2018-06-01",
                "surgeon", "Sample data (demo)",
                "facility", "Harare Central",
                "status", "COMPLETED",
                "notes", "Uncomplicated"));
        return ResponseEntity.ok(Map.of("data", rows, "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> demoAdvanceDirectives(
            String patientIdParam, String requestId, String correlationId) {
        if (!GOLDEN_PATH_DEMO_PATIENT.equals(patientIdParam)) {
            return emptyHistory(requestId, correlationId);
        }
        List<Map<String, Object>> rows = List.of(Map.of(
                "id", "ad-demo-1",
                "type", "POLST",
                "status", "ACTIVE",
                "effectiveDate", "2025-12-01",
                "reviewDate", "2027-12-01",
                "summary", "Full code unless terminal",
                "contact", "Next of kin",
                "contactRelation", "Spouse",
                "contactPhone", "+263-77-000-0000"));
        return ResponseEntity.ok(Map.of("data", rows, "meta", meta(requestId, correlationId)));
    }
}
