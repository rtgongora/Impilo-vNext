package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.AssessmentService;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.persistence.entity.AssessmentEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Baseline wellness assessment wizard. Citizen-self by default (guarded by {@link WellnessAccessGuard});
 * the BFF reverse-proxies {@code /internal/v1/wellness/**} to this service so web/mobile reach it directly.
 */
@RestController
@RequestMapping("/internal/v1/wellness/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final WellnessAccessGuard guard;

    public AssessmentController(AssessmentService assessmentService, WellnessAccessGuard guard) {
        this.assessmentService = assessmentService;
        this.guard = guard;
    }

    private WellnessAccessGuard.WellnessAccessContext ctx(
            UUID tenantId, String actorId, String actorType, String providerId,
            String facilityId, String workspaceId, String programmeId,
            String correlationId, String requestId) {
        return new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType, providerId,
                facilityId, workspaceId, programmeId, correlationId, requestId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> startOrResume(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String personCpid = required(body, "person_cpid");
        String templateCode = str(body, "template_code");
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, null, null, null, correlationId, requestId),
                personCpid, "wellness.assessment.start");
        AssessmentEntity a = assessmentService.startOrResumeDraft(tenantId, personCpid, templateCode,
                actorId != null ? actorId : personCpid);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", a));
    }

    @GetMapping("/{assessmentId}")
    public Map<String, Object> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("assessmentId") UUID assessmentId) {
        AssessmentEntity a = assessmentService.get(tenantId, assessmentId);
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, null, null, null, correlationId, requestId),
                a.getPersonCpid(), "wellness.assessment.read");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", a);
        out.put("responses", assessmentService.responses(assessmentId));
        return out;
    }

    @GetMapping
    public Map<String, Object> listForPerson(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam("person_cpid") String personCpid) {
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, null, null, null, correlationId, requestId),
                personCpid, "wellness.assessment.list");
        return Map.of("data", assessmentService.list(tenantId, personCpid));
    }

    @PatchMapping("/{assessmentId}/step")
    public Map<String, Object> saveStep(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("assessmentId") UUID assessmentId,
            @RequestBody Map<String, Object> body) {
        AssessmentEntity existing = assessmentService.get(tenantId, assessmentId);
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, null, null, null, correlationId, requestId),
                existing.getPersonCpid(), "wellness.assessment.save-step");

        String step = str(body, "step");
        Boolean consent = body.get("consent_captured") == null ? null
                : Boolean.valueOf(String.valueOf(body.get("consent_captured")));
        List<AssessmentService.ResponseInput> inputs = parseResponses(body.get("responses"));
        AssessmentEntity a = assessmentService.saveStep(tenantId, assessmentId, step, consent, inputs);
        return Map.of("data", a);
    }

    @PostMapping("/{assessmentId}/complete")
    public Map<String, Object> complete(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("assessmentId") UUID assessmentId) {
        AssessmentEntity existing = assessmentService.get(tenantId, assessmentId);
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, null, null, null, correlationId, requestId),
                existing.getPersonCpid(), "wellness.assessment.complete");

        AssessmentService.CompletionResult result = assessmentService.complete(tenantId, assessmentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", result.assessment());
        out.put("risk_band", result.assessment().getRiskBand());
        out.put("care_linkage_routed", result.careLinkageRouted());
        out.put("next_actions", result.nextActions());
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<AssessmentService.ResponseInput> parseResponses(Object raw) {
        List<AssessmentService.ResponseInput> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> mm = (Map<String, Object>) m;
            String qc = str(mm, "question_code");
            if (qc == null || qc.isBlank()) {
                continue;
            }
            BigDecimal num = null;
            Object n = mm.get("value_numeric");
            if (n != null && !String.valueOf(n).isBlank()) {
                try {
                    num = new BigDecimal(String.valueOf(n));
                } catch (NumberFormatException ignored) {
                    num = null;
                }
            }
            out.add(new AssessmentService.ResponseInput(
                    str(mm, "section"), qc, str(mm, "value_text"), num, str(mm, "unit")));
        }
        return out;
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
