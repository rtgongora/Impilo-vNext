package zw.gov.mohcc.impilo.clinical.interpretation;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationContext;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ObservationInput;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ReferenceRange;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalContextEnricher;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates a single context-aware interpretation evaluation: it interprets the supplied observations
 * against patient-appropriate reference intervals, feeds the interpreted picture into the deterministic
 * rules engine, and records an auditable trace (including the selected range id + version for every
 * interpretation). Advisory only — never overrides clinician judgement.
 */
@Service
public class InterpretationEvaluationService {

    private static final String KNOWLEDGE_VERSION = "ckp-interpretation-1";
    private static final String SUPPORT_MODE = "DETERMINISTIC_INTERPRETATION";

    private final InterpretationEngine interpretationEngine;
    private final ClinicalContextEnricher contextEnricher;
    private final ClinicalRulesEngine rulesEngine;
    private final TraceService traceService;

    public InterpretationEvaluationService(InterpretationEngine interpretationEngine,
                                           ClinicalContextEnricher contextEnricher,
                                           ClinicalRulesEngine rulesEngine,
                                           TraceService traceService) {
        this.interpretationEngine = interpretationEngine;
        this.contextEnricher = contextEnricher;
        this.rulesEngine = rulesEngine;
        this.traceService = traceService;
    }

    /**
     * @param actorId     the requesting actor (for audit)
     * @param patientId   subject patient id (nullable)
     * @param encounterId encounter id (nullable)
     * @param contextMap  patient/encounter context (age/sex/pregnancy/specimen/diagnoses/meds/...)
     * @param observationMaps the observations to interpret
     * @return {@code {interpreted_observations, alerts, ranges_used, trace_id, support_mode}}
     */
    public Map<String, Object> evaluate(String actorId, String patientId, String encounterId,
                                        Map<String, Object> contextMap,
                                        List<Map<String, Object>> observationMaps) {

        InterpretationContext interpretationContext = InterpretationContext.fromMap(contextMap);

        List<ObservationInput> inputs = new ArrayList<>();
        if (observationMaps != null) {
            for (Map<String, Object> m : observationMaps) {
                if (m != null) {
                    inputs.add(ObservationInput.fromMap(m));
                }
            }
        }

        List<InterpretedObservation> interpreted = interpretationEngine.interpretAll(inputs, interpretationContext);

        // Feed the interpreted picture into the deterministic rules engine.
        ClinicalEvaluationContext evalContext = ClinicalEvaluationContext.fromMap(contextMap)
                .withInterpretations(interpreted, ClinicalEvaluationContext.fromMap(contextMap).derivedValues());
        evalContext = contextEnricher.enrich(evalContext);
        List<RuleAlert> alerts = rulesEngine.evaluate(evalContext);

        List<Map<String, Object>> interpretedMaps = interpreted.stream().map(InterpretedObservation::toMap).toList();
        List<Map<String, Object>> alertMaps = alerts.stream().map(RuleAlert::toMap).toList();
        // Citations record the provenance (artifact id + version) of every governed range used.
        List<Map<String, Object>> rangesUsed = citations(interpreted);

        Map<String, Object> recommendations = new LinkedHashMap<>();
        recommendations.put("interpreted_observations", interpretedMaps);
        recommendations.put("alerts", alertMaps);

        RecommendationTraceEntity trace = traceService.record(
                actorId == null ? "anonymous" : actorId,
                "PROVIDER",
                patientId,
                encounterId,
                "CLINICAL_INTERPRETATION",
                Map.of("context", contextMap == null ? Map.of() : contextMap,
                        "observation_count", inputs.size()),
                recommendations,
                rangesUsed,
                alertMaps,
                KNOWLEDGE_VERSION,
                "n/a",
                SUPPORT_MODE);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("interpreted_observations", interpretedMaps);
        data.put("alerts", alertMaps);
        data.put("ranges_used", rangesUsed);
        data.put("trace_id", trace.getId().toString());
        data.put("support_mode", SUPPORT_MODE);
        data.put("advisory", true);
        return data;
    }

    /** Provenance of every governed/lab range actually used — recorded in the audit trace. */
    private List<Map<String, Object>> citations(List<InterpretedObservation> interpreted) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InterpretedObservation o : interpreted) {
            ReferenceRange r = o.range();
            if (r == null || r.source() == null) {
                continue;
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("analyte", o.displayName() != null ? o.displayName() : o.loincCode());
            c.put("loinc_code", o.loincCode());
            c.put("source", r.source().name());
            c.put("artifact_id", r.artifactId());
            c.put("version", r.version());
            c.put("canonical_url", r.canonicalUrl());
            out.add(c);
        }
        return out;
    }
}
