package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.StructuredHistoryService;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Structured history — the paths experience-bff has always called and pct-service has never served.
 *
 * <p>Their absence is why every one of those endpoints returned a transitional 502 naming this
 * lane and wave. With this controller they answer, and the {@code in_development} notice on the BFF
 * side can go.</p>
 */
@RestController
@RequestMapping("/v1/ehr")
public class StructuredHistoryController {

    private final StructuredHistoryService history;

    public StructuredHistoryController(StructuredHistoryService history) {
        this.history = history;
    }

    private static String cid() { return TrustContextHolder.require().correlationId().toString(); }

    @GetMapping("/social-history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> socialHistory(
            @RequestParam(name = "patient_id") String patientId) {
        return ResponseEntity.ok(ApiResponse.ok(history.socialHistory(patientId).stream()
                .map(StructuredHistoryController::social).collect(Collectors.toList()), cid()));
    }

    @PostMapping("/social-history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordSocial(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(social(history.recordSocialHistory(body)), cid()));
    }

    @GetMapping("/family-history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> familyHistory(
            @RequestParam(name = "patient_id") String patientId) {
        List<Map<String, Object>> out = history.familyHistory(patientId).stream().map(m -> {
            Map<String, Object> row = familyMember(m);
            row.put("conditions", history.conditionsOf(m.getMemberId()).stream()
                    .map(StructuredHistoryController::familyCondition).collect(Collectors.toList()));
            return row;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out, cid()));
    }

    @PostMapping("/family-history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordFamily(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(familyMember(history.recordFamilyMember(body)), cid()));
    }

    @GetMapping("/functional-assessments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> functional(
            @RequestParam(name = "patient_id") String patientId) {
        return ResponseEntity.ok(ApiResponse.ok(history.functionalAssessments(patientId).stream()
                .map(StructuredHistoryController::functional).collect(Collectors.toList()), cid()));
    }

    @PostMapping("/functional-assessments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordFunctional(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(functional(history.recordFunctionalAssessment(body)), cid()));
    }

    @GetMapping("/procedures")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> procedures(
            @RequestParam(name = "patient_id") String patientId) {
        return ResponseEntity.ok(ApiResponse.ok(history.pastProcedures(patientId).stream()
                .map(StructuredHistoryController::procedure).collect(Collectors.toList()), cid()));
    }

    @PostMapping("/procedures")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordProcedure(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(procedure(history.recordPastProcedure(body)), cid()));
    }

    @GetMapping("/advance-directives")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> directives(
            @RequestParam(name = "patient_id") String patientId) {
        return ResponseEntity.ok(ApiResponse.ok(history.advanceDirectives(patientId).stream()
                .map(StructuredHistoryController::directive).collect(Collectors.toList()), cid()));
    }

    @PostMapping("/advance-directives")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordDirective(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(directive(history.recordAdvanceDirective(body)), cid()));
    }

    // ── Projections ─────────────────────────────────────────────────────────────

    private static Map<String, Object> social(SocialHistoryEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getEntryId().toString());
        m.put("category", e.getCategory());
        m.put("status", e.getStatus());
        m.put("detail", e.getDetail());
        m.put("riskLevel", e.getRiskLevel());
        m.put("lastUpdated", e.getLastUpdated() == null ? null : e.getLastUpdated().toString());
        return m;
    }

    private static Map<String, Object> familyMember(FamilyHistoryMemberEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getMemberId().toString());
        m.put("relationship", e.getRelationship());
        m.put("distinguisher", e.getDistinguisher());
        m.put("age", e.getAge());
        m.put("deceased", e.getDeceased());
        m.put("deceasedAge", e.getDeceasedAge());
        m.put("causeOfDeath", e.getCauseOfDeath());
        return m;
    }

    private static Map<String, Object> familyCondition(FamilyHistoryConditionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getConditionId().toString());
        m.put("conditionLabel", e.getConditionLabel());
        m.put("code", e.getCode());
        m.put("onsetAge", e.getOnsetAge());
        m.put("status", e.getStatus());
        m.put("notes", e.getNotes());
        return m;
    }

    private static Map<String, Object> functional(FunctionalAssessmentEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getAssessmentId().toString());
        m.put("assessmentType", e.getAssessmentType());
        m.put("assessmentDate", e.getAssessmentDate() == null ? null : e.getAssessmentDate().toString());
        m.put("assessor", e.getAssessor());
        m.put("totalScore", e.getTotalScore());
        m.put("maxScore", e.getMaxScore());
        // Travels beside the score so a reader can tell an unscored assessment from a zero one.
        m.put("scoreAbsentReason", e.getScoreAbsentReason());
        m.put("interpretation", e.getInterpretation());
        return m;
    }

    private static Map<String, Object> procedure(PastProcedureEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getProcedureId().toString());
        m.put("name", e.getName());
        m.put("procedureType", e.getProcedureType());
        m.put("procedureDate", e.getProcedureDate() == null ? null : e.getProcedureDate().toString());
        m.put("datePrecision", e.getDatePrecision());
        m.put("performer", e.getPerformer());
        m.put("facility", e.getFacility());
        m.put("status", e.getStatus());
        m.put("notes", e.getNotes());
        return m;
    }

    private static Map<String, Object> directive(AdvanceDirectiveEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getDirectiveId().toString());
        m.put("directiveType", e.getDirectiveType());
        m.put("status", e.getStatus());
        m.put("summary", e.getSummary());
        m.put("documentId", e.getDocumentId() == null ? null : e.getDocumentId().toString());
        m.put("effectiveFrom", e.getEffectiveFrom() == null ? null : e.getEffectiveFrom().toString());
        m.put("reviewedOn", e.getReviewedOn() == null ? null : e.getReviewedOn().toString());
        return m;
    }
}
