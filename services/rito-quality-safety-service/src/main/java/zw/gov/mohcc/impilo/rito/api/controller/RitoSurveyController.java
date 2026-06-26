package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.SurveyService;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito/surveys")
public class RitoSurveyController {

    private final SurveyService surveyService;

    public RitoSurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @PostMapping("")
    @SuppressWarnings("unchecked")
    public ResponseEntity<SurveyEntity> createSurvey(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surveyService.createSurvey(tenantId,
                required(body, "survey_key"), required(body, "name"), str(body, "description"),
                str(body, "survey_type"), str(body, "form_schema_ref"),
                (List<Object>) body.get("questions"), (Boolean) body.get("anonymous_allowed")));
    }

    @GetMapping("")
    public List<SurveyEntity> listSurveys(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return surveyService.listSurveys(tenantId);
    }

    @GetMapping("/{id}")
    public SurveyEntity getSurvey(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return surveyService.getSurvey(tenantId, id);
    }

    @PostMapping("/{id}/responses")
    @SuppressWarnings("unchecked")
    public ResponseEntity<SurveyResponseEntity> submitResponse(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String respondentActorId = str(body, "respondent_actor_id");
        String respondentActorType = str(body, "respondent_actor_type");
        return ResponseEntity.status(HttpStatus.CREATED).body(surveyService.submitResponse(tenantId, id,
                respondentActorId != null ? respondentActorId : actorId,
                respondentActorType != null ? respondentActorType : actorType,
                (Boolean) body.get("anonymous"), uuid(body, "facility_id"), str(body, "visit_ref"),
                (Map<String, Object>) body.getOrDefault("answers", java.util.Map.of()),
                str(body, "free_text")));
    }

    @GetMapping("/{id}/aggregate")
    public Map<String, Object> aggregate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return surveyService.aggregate(tenantId, id);
    }

    // ---- request-map helpers ----

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(v.toString());
    }
}
