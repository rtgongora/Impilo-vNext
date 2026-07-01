package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pct.core.clinical.CarePlanService;
import zw.gov.mohcc.impilo.pct.core.clinical.ProblemService;
import zw.gov.mohcc.impilo.pct.integration.FormsCatalogIntegration;
import zw.gov.mohcc.impilo.pct.integration.OrosIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormExtractedResourceEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormExtractedResourceRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormResponseRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts structured clinical resources from a submitted form response, driven by the locked definition's
 * {@code resourceMappings}. In-process sinks (problems, care plans) commit in the submit transaction and are
 * fully wired; external sinks (OROS orders) are eventually consistent; Observation writes to BUTANO are
 * emitted as events for the SHR bridge (deferred write). Every extraction writes a provenance row tying the
 * resource back to (response, form version, source field). Idempotent per response.
 */
@Service
public class FormExtractionService implements FormExtractionHook {

    private static final Logger log = LoggerFactory.getLogger(FormExtractionService.class);

    private final FormsCatalogIntegration formsCatalogIntegration;
    private final ProblemService problemService;
    private final CarePlanService carePlanService;
    private final OrosIntegration orosIntegration;
    private final FormExtractedResourceRepository extractedRepository;
    private final FormResponseRepository responseRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FormExtractionService(FormsCatalogIntegration formsCatalogIntegration,
                                 ProblemService problemService,
                                 CarePlanService carePlanService,
                                 OrosIntegration orosIntegration,
                                 FormExtractedResourceRepository extractedRepository,
                                 FormResponseRepository responseRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.formsCatalogIntegration = formsCatalogIntegration;
        this.problemService = problemService;
        this.carePlanService = carePlanService;
        this.orosIntegration = orosIntegration;
        this.extractedRepository = extractedRepository;
        this.responseRepository = responseRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void extract(FormResponseEntity r) {
        // Idempotent: never double-extract the same response.
        if (!extractedRepository.findByResponseId(r.getResponseId()).isEmpty()) {
            log.info("pct.form.extraction.skip id={} (already extracted)", r.getResponseId());
            return;
        }

        JsonNode answers = readTree(r.getAnswers());
        List<Map<String, Object>> mappings = readMappings(r.getFormKey());
        int extracted = 0;

        for (Map<String, Object> m : mappings) {
            String linkId = str(m.get("linkId"));
            if (linkId == null) {
                continue;
            }
            JsonNode value = answers.get(linkId);
            if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
                continue;
            }
            String resourceType = upper(str(m.get("resourceType")));
            try {
                switch (resourceType) {
                    case "CONDITION" -> extractCondition(r, m, linkId, value);
                    case "CARE_PLAN" -> extractCarePlan(r, m, linkId, value);
                    case "SERVICE_REQUEST" -> extractServiceRequest(r, m, linkId, value);
                    case "OBSERVATION", "PROCEDURE" -> extractObservation(r, m, linkId, value, resourceType);
                    case "SAFETY_EVENT" -> extractSafetyEvent(r, m, linkId, value);
                    default -> log.debug("Unmapped resource type {} for linkId {}", resourceType, linkId);
                }
                extracted++;
            } catch (RuntimeException e) {
                recordFailure(r, m, linkId, resourceType, e.getMessage());
            }
        }

        // Canonical QuestionnaireResponse projection for the SHR handoff.
        try {
            r.setQuestionnaireResponse(
                    QuestionnaireResponseMapper.toQuestionnaireResponse(r, answers, objectMapper).toString());
            responseRepository.save(r);
        } catch (RuntimeException e) {
            log.warn("Failed to build QuestionnaireResponse projection for {}: {}", r.getResponseId(), e.getMessage());
        }

        writeOutbox(r, "pct.form.extracted", Map.of(
                "responseId", r.getResponseId().toString(),
                "formKey", r.getFormKey(),
                "extractedCount", extracted));
        log.info("pct.form.extracted id={} formKey={} resources={}", r.getResponseId(), r.getFormKey(), extracted);
    }

    private void extractCondition(FormResponseEntity r, Map<String, Object> m, String linkId, JsonNode value) {
        Map<String, Object> code = asMap(m.get("code"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject_cpid", r.getSubjectCpid());
        body.put("journey_id", r.getJourneyId());
        body.put("encounter_id", r.getEncounterId());
        body.put("display", value.asText());
        body.put("code", str(code.get("code")));
        body.put("code_system", firstNonBlank(str(m.get("codeSystem")), str(code.get("system"))));
        body.put("category", firstNonBlank(str(m.get("category")), "DIAGNOSIS"));
        var problem = problemService.add(body);
        record(r, m, linkId, "CONDITION", "PCT_PROBLEM", "CONFIRMED",
                null, String.valueOf(problem.getProblemId()), body);
    }

    private void extractCarePlan(FormResponseEntity r, Map<String, Object> m, String linkId, JsonNode value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject_cpid", r.getSubjectCpid());
        body.put("journey_id", r.getJourneyId());
        body.put("encounter_id", r.getEncounterId());
        body.put("title", value.asText());
        body.put("plan_type", "OUTPATIENT");
        var plan = carePlanService.create(body);
        record(r, m, linkId, "CARE_PLAN", "PCT_CARE_PLAN", "CONFIRMED",
                null, String.valueOf(plan.getPlanId()), body);
    }

    private void extractServiceRequest(FormResponseEntity r, Map<String, Object> m, String linkId, JsonNode value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject_cpid", r.getSubjectCpid());
        payload.put("category", firstNonBlank(str(m.get("orderCategory")), "laboratory"));
        payload.put("code", value.asText());
        payload.put("note", "From form " + r.getFormKey() + " field " + linkId);
        Map<String, Object> result = orosIntegration.submitOrder(r.getJourneyId(), toJson(payload));
        String orderId = result == null ? null : str(result.get("orderId"));
        if (orderId == null || orderId.isBlank()) {
            record(r, m, linkId, "SERVICE_REQUEST", "OROS", "FAILED", null, null, payload);
        } else {
            record(r, m, linkId, "SERVICE_REQUEST", "OROS", "ROUTED", orderId, null, payload);
        }
    }

    private void extractObservation(FormResponseEntity r, Map<String, Object> m, String linkId,
                                    JsonNode value, String resourceType) {
        // No direct PCT→BUTANO Observation write method exists; emit for the SHR bridge and record PENDING.
        Map<String, Object> code = asMap(m.get("code"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject_cpid", r.getSubjectCpid());
        payload.put("butano_encounter_ref", r.getButanoEncounterRef());
        payload.put("code", code);
        payload.put("value", value.isNumber() ? value.numberValue() : value.asText());
        payload.put("unit", str(m.get("unit")));
        record(r, m, linkId, resourceType, "BUTANO", "PENDING", null, null, payload);
        writeOutbox(r, "pct.form.observation.extracted", payload);
    }

    /**
     * Adverse-event / safety answers → a Rito safety signal (event-driven; Rito + surveillance consume
     * {@code pct.form.safety.flagged}). Recorded as provenance (route RITO). The trigger is config: a
     * mapping with resourceType SAFETY_EVENT, optionally gated to a specific answer via {@code triggerValue}.
     */
    private void extractSafetyEvent(FormResponseEntity r, Map<String, Object> m, String linkId, JsonNode value) {
        String trigger = str(m.get("triggerValue"));
        if (trigger != null && !trigger.equalsIgnoreCase(value.asText())) {
            return; // answer did not match the safety trigger
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectCpid", r.getSubjectCpid());
        payload.put("encounterId", r.getEncounterId());
        payload.put("formKey", r.getFormKey());
        payload.put("field", linkId);
        payload.put("value", value.asText());
        payload.put("category", firstNonBlank(str(m.get("safetyCategory")), "ADVERSE_EVENT"));
        record(r, m, linkId, "SAFETY_EVENT", "RITO", "ROUTED", null, null, payload);
        writeOutbox(r, "pct.form.safety.flagged", payload);
    }

    private void record(FormResponseEntity r, Map<String, Object> m, String linkId, String resourceType,
                        String routeTarget, String status, String externalRef, String localRef,
                        Map<String, Object> payload) {
        FormExtractedResourceEntity e = new FormExtractedResourceEntity();
        e.setTenantId(r.getTenantId());
        e.setResponseId(r.getResponseId());
        e.setFormSchemaVersionId(r.getFormSchemaVersionId());
        e.setResourceType(resourceType);
        e.setRouteTarget(routeTarget);
        e.setSourceLinkIds(toJson(List.of(linkId)));
        e.setResourcePayload(toJson(payload));
        e.setExternalRef(externalRef);
        e.setLocalRef(localRef);
        e.setStatus(status);
        e.setAttempts(1);
        extractedRepository.save(e);
    }

    private void recordFailure(FormResponseEntity r, Map<String, Object> m, String linkId,
                               String resourceType, String reason) {
        FormExtractedResourceEntity e = new FormExtractedResourceEntity();
        e.setTenantId(r.getTenantId());
        e.setResponseId(r.getResponseId());
        e.setFormSchemaVersionId(r.getFormSchemaVersionId());
        e.setResourceType(resourceType == null ? "UNKNOWN" : resourceType);
        e.setRouteTarget(upper(str(m.get("routeTarget"))));
        e.setSourceLinkIds(toJson(List.of(linkId)));
        e.setStatus("FAILED");
        e.setAttempts(1);
        e.setFailureReason(reason);
        extractedRepository.save(e);
        log.warn("pct.form.extraction.item.failed id={} linkId={} type={}: {}",
                r.getResponseId(), linkId, resourceType, reason);
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readMappings(String formKey) {
        try {
            FormCatalogEntry entry = formsCatalogIntegration.fetchCatalog().stream()
                    .filter(e -> formKey.equalsIgnoreCase(e.formKey()))
                    .findFirst().orElse(null);
            if (entry == null || entry.resourceMappings() == null || entry.resourceMappings().isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(entry.resourceMappings());
            JsonNode mappings = root.get("mappings");
            if (mappings == null || !mappings.isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(mappings, List.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to read resource mappings for {}: {}", formKey, e.getMessage());
            return List.of();
        }
    }

    private void writeOutbox(FormResponseEntity r, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("FORM_RESPONSE");
        outbox.setAggregateId(r.getResponseId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(r.getTenantId());
        outboxRepository.save(outbox);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
