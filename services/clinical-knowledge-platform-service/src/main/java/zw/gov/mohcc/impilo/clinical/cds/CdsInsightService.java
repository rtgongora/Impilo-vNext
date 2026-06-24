package zw.gov.mohcc.impilo.clinical.cds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.llm.LlmClinicalClient;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.reasoning.ClinicalPromptBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces a real, grounded, fail-closed AI "insight" that summarises/prioritises the patient's
 * already-computed DETERMINISTIC safety alerts for the CDS banner. The LLM is given ONLY the
 * deterministic alerts + structured patient context (never free text to invent findings); the
 * insight can never introduce an alert that was not deterministically raised, never gates the
 * deterministic alerts, and is absent (not faked) whenever the LLM is unavailable.
 */
@Service
public class CdsInsightService {

    private static final Logger log = LoggerFactory.getLogger(CdsInsightService.class);

    private final LlmClinicalClient llmClient;
    private final ClinicalPromptBuilder promptBuilder;
    private final TraceService traceService;
    private final boolean llmEnabled;

    public CdsInsightService(
            LlmClinicalClient llmClient,
            ClinicalPromptBuilder promptBuilder,
            TraceService traceService,
            @Value("${impilo.clinical.llm.enabled:true}") boolean llmEnabled) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.traceService = traceService;
        this.llmEnabled = llmEnabled;
    }

    /** Returns {@code {"insight": {...}}} or {@code {"insight": null}} (honest empty / fail-closed). */
    public Map<String, Object> summarise(
            String tenantId,
            String actorId,
            Map<String, Object> patientContext,
            List<Map<String, Object>> alerts,
            String encounterId) {

        Map<String, Object> none = new LinkedHashMap<>();
        none.put("insight", null);

        if (!llmEnabled || alerts == null || alerts.isEmpty()) {
            return none;
        }

        Set<String> suppliedCodes = alerts.stream()
                .map(a -> a.get("code"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());

        Optional<LlmClinicalClient.LlmStructuredResult> maybe = llmClient.requestStructured(
                "CLINICAL_CDS_ALERT_SUMMARY",
                promptBuilder.cdsMessages(patientContext, alerts),
                promptBuilder.cdsSchema(),
                Map.of("tenantId", tenantId == null ? "default" : tenantId,
                        "actorId", actorId == null ? "anonymous" : actorId,
                        "purposeOfUse", "CLINICAL_DECISION_SUPPORT"),
                "HIGH",
                0.0);

        if (maybe.isEmpty()) {
            return none;
        }
        Map<String, Object> out = maybe.get().structuredOutput();

        // Guardrail: prioritised codes must be a subset of the supplied deterministic alert codes.
        List<String> prioritised = asStringList(out.get("prioritised_alert_codes"));
        for (String code : prioritised) {
            if (!suppliedCodes.contains(code)) {
                log.info("CDS insight rejected — code '{}' not in supplied deterministic alerts", code);
                return none;
            }
        }
        String summary = str(out.get("summary"));
        if (summary == null || summary.isBlank()) {
            return none;
        }

        String patientId = patientContext != null && patientContext.get("patient_id") != null
                ? patientContext.get("patient_id").toString() : null;

        Map<String, Object> insightPayload = new LinkedHashMap<>();
        insightPayload.put("summary", summary);
        insightPayload.put("advisory", str(out.get("advisory")));
        insightPayload.put("prioritised_alert_codes", prioritised);
        insightPayload.put("generated_by", "AI");
        insightPayload.put("model", maybe.get().model());

        RecommendationTraceEntity trace = traceService.record(
                actorId, "PROVIDER", patientId, encounterId, "CDS_INSIGHT",
                Map.of("tenant_id", tenantId == null ? "default" : tenantId,
                        "patient_context", patientContext == null ? Map.of() : patientContext,
                        "llm_audit_ref", maybe.get().auditRef() == null ? "" : maybe.get().auditRef()),
                insightPayload,
                List.of(),
                alerts,
                "ckp-seed-1",
                "n/a",
                "LLM_SUMMARY_OF_DETERMINISTIC");
        insightPayload.put("trace_id", trace.getId().toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("insight", insightPayload);
        return result;
    }

    private static List<String> asStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) {
                if (e != null) {
                    out.add(e.toString());
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
