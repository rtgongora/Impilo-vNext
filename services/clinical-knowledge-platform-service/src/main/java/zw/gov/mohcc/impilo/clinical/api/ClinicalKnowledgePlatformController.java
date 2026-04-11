package zw.gov.mohcc.impilo.clinical.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.assistant.ClinicalAssistantService;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.nudge.NudgeEvaluationService;
import zw.gov.mohcc.impilo.clinical.pathway.PathwaySessionService;
import zw.gov.mohcc.impilo.clinical.persistence.entity.OverrideRecordEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwaySessionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.OverrideRecordRepository;
import zw.gov.mohcc.impilo.clinical.prescribing.PrescribingEvaluationService;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API surface — Experience BFF and trusted back-office proxies only.
 */
@RestController
@RequestMapping("/internal/v1/clinical")
public class ClinicalKnowledgePlatformController {

    private final ClinicalAssistantService assistantService;
    private final PrescribingEvaluationService prescribingEvaluationService;
    private final PathwaySessionService pathwaySessionService;
    private final NudgeEvaluationService nudgeEvaluationService;
    private final TraceService traceService;
    private final OverrideRecordRepository overrideRecordRepository;
    private final ClinicalRulesEngine clinicalRulesEngine;
    private final ClinicalOutboxWriter clinicalOutboxWriter;

    public ClinicalKnowledgePlatformController(
            ClinicalAssistantService assistantService,
            PrescribingEvaluationService prescribingEvaluationService,
            PathwaySessionService pathwaySessionService,
            NudgeEvaluationService nudgeEvaluationService,
            TraceService traceService,
            OverrideRecordRepository overrideRecordRepository,
            ClinicalRulesEngine clinicalRulesEngine,
            ClinicalOutboxWriter clinicalOutboxWriter) {
        this.assistantService = assistantService;
        this.prescribingEvaluationService = prescribingEvaluationService;
        this.pathwaySessionService = pathwaySessionService;
        this.nudgeEvaluationService = nudgeEvaluationService;
        this.traceService = traceService;
        this.overrideRecordRepository = overrideRecordRepository;
        this.clinicalRulesEngine = clinicalRulesEngine;
        this.clinicalOutboxWriter = clinicalOutboxWriter;
    }

    @PostMapping("/assistant/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestHeader(value = "x-tenant-id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {

        String question = body.get("question") != null ? body.get("question").toString() : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> patientContext = body.get("patient_context") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        boolean citizenMode = Boolean.TRUE.equals(body.get("citizen_mode"));
        String role = body.getOrDefault("role", "PROVIDER").toString();

        Map<String, Object> data = assistantService.ask(tenantId, actorId, role, question, patientContext, encounterId, citizenMode);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/assistant/traces/{id}")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable UUID id) {
        JsonNode node = traceService.findTraceJson(id);
        if (node == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", node));
    }

    @PostMapping("/prescribing/evaluate")
    public ResponseEntity<Map<String, Object>> prescribing(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", prescribingEvaluationService.evaluate(body)));
    }

    @PostMapping("/rules/evaluate")
    public ResponseEntity<Map<String, Object>> rules(@RequestBody Map<String, Object> body) {
        var ctx = ClinicalEvaluationContext.fromMap(body);
        var alerts = clinicalRulesEngine.evaluate(ctx).stream().map(RuleAlert::toMap).toList();
        return ResponseEntity.ok(Map.of("data", Map.of("alerts", alerts)));
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> pathways() {
        return ResponseEntity.ok(Map.of("data", pathwaySessionService.listPathways()));
    }

    @GetMapping("/pathways/{id}")
    public ResponseEntity<Map<String, Object>> pathwayDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("data", pathwaySessionService.getPathwayDetail(id)));
    }

    @PostMapping("/pathways/sessions")
    public ResponseEntity<Map<String, Object>> startPathway(
            @RequestHeader(value = "x-tenant-id", defaultValue = "default") String tenantId,
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {
        UUID pathwayId = UUID.fromString(body.get("pathway_id").toString());
        String patientId = body.get("patient_id") != null ? body.get("patient_id").toString() : null;
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        PathwaySessionEntity s = pathwaySessionService.start(tenantId, actorId, pathwayId, patientId, encounterId);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("session_id", s.getId().toString());
        d.put("pathway_id", s.getPathwayId().toString());
        d.put("current_step_order", s.getCurrentStepOrder());
        d.put("status", s.getStatus());
        return ResponseEntity.ok(Map.of("data", d));
    }

    @PostMapping("/pathways/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advancePathway(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> answers = body.get("answers") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : Map.of();
        return ResponseEntity.ok(Map.of("data", pathwaySessionService.advance(sessionId, answers)));
    }

    @PostMapping("/nudges/evaluate")
    public ResponseEntity<Map<String, Object>> nudges(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", nudgeEvaluationService.evaluate(body)));
    }

    @PostMapping("/audit/overrides")
    public ResponseEntity<Map<String, Object>> recordOverride(
            @RequestHeader(value = "x-actor-id", defaultValue = "anonymous") String actorId,
            @RequestBody Map<String, Object> body) {
        UUID traceId = UUID.fromString(body.get("recommendation_trace_id").toString());
        String reason = body.get("override_reason").toString();
        OverrideRecordEntity o = new OverrideRecordEntity();
        o.setRecommendationTraceId(traceId);
        o.setOverrideReason(reason);
        o.setOverriddenBy(actorId);
        OverrideRecordEntity saved = overrideRecordRepository.save(o);
        clinicalOutboxWriter.enqueue(
                "GUIDANCE_OVERRIDE_RECORDED",
                "default",
                "OverrideRecord",
                saved.getId().toString(),
                Map.of(
                        "override_id", saved.getId().toString(),
                        "recommendation_trace_id", traceId.toString(),
                        "overridden_by", actorId));
        return ResponseEntity.ok(Map.of("data", Map.of("status", "recorded", "override_id", saved.getId().toString())));
    }
}
