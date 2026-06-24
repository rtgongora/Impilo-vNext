package zw.gov.mohcc.impilo.clinical.assistant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.audit.TraceService;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceDocumentEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceDocumentRepository;
import zw.gov.mohcc.impilo.clinical.assistant.retrieval.GuidanceRetriever;
import zw.gov.mohcc.impilo.clinical.reasoning.ReasoningRequest;
import zw.gov.mohcc.impilo.clinical.reasoning.ReasoningResult;
import zw.gov.mohcc.impilo.clinical.rules.ClinicalRulesEngine;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.*;

@Service
public class ClinicalAssistantService {

    private final GuidanceRetriever guidanceRetriever;
    private final MedicineGuidanceRepository medicineRepository;
    private final SourceDocumentRepository documentRepository;
    private final ClinicalRulesEngine rulesEngine;
    private final TraceService traceService;
    private final zw.gov.mohcc.impilo.clinical.reasoning.ClinicalReasoner clinicalReasoner;

    public ClinicalAssistantService(
            GuidanceRetriever guidanceRetriever,
            MedicineGuidanceRepository medicineRepository,
            SourceDocumentRepository documentRepository,
            ClinicalRulesEngine rulesEngine,
            TraceService traceService,
            zw.gov.mohcc.impilo.clinical.reasoning.ClinicalReasoner clinicalReasoner) {
        this.guidanceRetriever = guidanceRetriever;
        this.medicineRepository = medicineRepository;
        this.documentRepository = documentRepository;
        this.rulesEngine = rulesEngine;
        this.traceService = traceService;
        this.clinicalReasoner = clinicalReasoner;
    }

    @Transactional
    public Map<String, Object> ask(
            String tenantId,
            String actorId,
            String role,
            String question,
            Map<String, Object> patientContext,
            String encounterId,
            boolean citizenMode) {

        String q = question == null ? "" : question.trim();
        String qClass = QuestionClassifier.classify(q);
        Set<String> topics = QuestionClassifier.topicTokens(q);

        String search = q.length() > 200 ? q.substring(0, 200) : q;
        if (topics.contains("asthma")) {
            search = "asthma " + search;
        } else if (topics.contains("gonorrhoea")) {
            search = "gonorrhoea ceftriaxone " + search;
        } else if (topics.contains("neonatal_sepsis")) {
            search = "neonatal sepsis " + search;
        }

        List<SourceSectionEntity> sections = guidanceRetriever.retrieve(search, 5);
        String medSearch = search.length() >= 3 ? search : (String.join(" ", topics) + " " + search).trim();
        if (medSearch.length() < 3) {
            medSearch = "treatment";
        }
        List<MedicineGuidanceEntity> meds = medicineRepository.search(medSearch);

        SourceDocumentEntity doc = documentRepository.findFirstByStatusOrderByEffectiveDateDesc("ACTIVE").orElse(null);
        String sourceVersion = doc != null ? doc.getVersionLabel() : "unknown";

        ClinicalEvaluationContext ctx = ClinicalEvaluationContext.fromMap(patientContext);
        List<RuleAlert> alerts = ctx.activeMedications().isEmpty() && ctx.diagnoses().isEmpty()
                ? List.of()
                : rulesEngine.evaluate(ctx);

        List<Map<String, Object>> citations = new ArrayList<>();
        for (SourceSectionEntity s : sections) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("section_id", s.getId().toString());
            c.put("page", s.getPageNumber());
            c.put("section_title", s.getSectionTitle());
            c.put("excerpt", excerpt(s.getRawText(), 400));
            c.put("document_version", sourceVersion);
            citations.add(c);
        }

        List<Map<String, Object>> ruleMaps = alerts.stream().map(RuleAlert::toMap).toList();

        String supportMode = resolveSupportMode(sections.isEmpty(), meds.isEmpty(), alerts.isEmpty());

        String answerSummary = composeAnswer(q, citizenMode, sections, meds, alerts, topics);

        Map<String, Object> recommendation = new LinkedHashMap<>();
        if (!meds.isEmpty()) {
            MedicineGuidanceEntity m = meds.get(0);
            recommendation.put("medicine", Map.of(
                    "name", m.getMedicineName(),
                    "generic", m.getGenericName(),
                    "dose", m.getDoseExpression(),
                    "unit", m.getDoseUnit(),
                    "route", m.getRoute(),
                    "frequency", m.getFrequencyExpression(),
                    "duration", m.getDurationExpression(),
                    "level_of_care", m.getLevelOfCare(),
                    "ven_class", m.getVenClass(),
                    "specialist_only", Boolean.TRUE.equals(m.getSpecialistOnly())
            ));
        }

        List<String> nextActions = new ArrayList<>();
        if (!alerts.isEmpty()) {
            nextActions.add("Review clinical alerts before prescribing or changing therapy.");
        }
        if (citizenMode) {
            nextActions.add("Seek in-person care if symptoms are severe, worsening, or worrying.");
        } else {
            nextActions.add("Correlate with bedside assessment and local hospital protocols.");
        }

        // Grounded reasoning. The deterministic answer above is the honest floor; the reasoner may
        // override answer_summary/rationale ONLY with a real, grounding-validated LLM result. The
        // authoritative fields below (warnings/alerts_detail/rule_trace_refs/source_citations/
        // support_mode) always come from deterministic rules + retrieval — never from the LLM.
        List<Map<String, Object>> medGuidanceMaps = meds.stream().map(m -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("name", m.getMedicineName());
            g.put("generic", m.getGenericName());
            g.put("dose", m.getDoseExpression());
            g.put("route", m.getRoute());
            g.put("level_of_care", m.getLevelOfCare());
            g.put("specialist_only", Boolean.TRUE.equals(m.getSpecialistOnly()));
            return g;
        }).toList();

        ReasoningResult reasoning = clinicalReasoner.reason(new ReasoningRequest(
                tenantId, actorId, role, citizenMode, q,
                citations, medGuidanceMaps, ruleMaps,
                patientContext == null ? Map.of() : patientContext,
                sourceVersion, "ckp-seed-1"));

        boolean llm = "LLM".equals(reasoning.provenance());
        String finalAnswer = (llm && reasoning.answerSummary() != null) ? reasoning.answerSummary() : answerSummary;
        String finalRationale = (llm && reasoning.rationale() != null) ? reasoning.rationale() : buildRationale(sections, alerts);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answer_summary", finalAnswer);
        payload.put("question_classification", qClass);
        payload.put("recommendation", recommendation.isEmpty() ? null : recommendation);
        payload.put("rationale", finalRationale);
        payload.put("warnings", alerts.stream().map(RuleAlert::message).toList());
        payload.put("alerts_detail", ruleMaps);
        payload.put("applicable_population", citizenMode ? "general_public_education" : "licensed_clinician");
        payload.put("source_citations", citations);
        payload.put("structured_guidance_refs", meds.stream().map(m -> m.getId().toString()).toList());
        payload.put("rule_trace_refs", ruleMaps.stream().map(r -> r.get("code")).toList());
        payload.put("next_actions", nextActions);
        payload.put("support_mode", supportMode);
        payload.put("knowledge_version", "ckp-seed-1");
        payload.put("source_version", sourceVersion);
        payload.put("disclaimer", "Guideline support assists clinical judgment; it is not a substitute for assessment and local policy.");
        // Additive intelligence fields (back-compatible — existing consumers ignore unknown keys).
        payload.put("reasoning_provenance", reasoning.provenance());
        payload.put("model", reasoning.model());
        payload.put("differential_considerations", reasoning.differentialConsiderations());
        Map<String, Object> grounding = new LinkedHashMap<>();
        grounding.put("ratio", reasoning.groundingRatio());
        grounding.put("valid", reasoning.groundingValid());
        grounding.put("fell_back", reasoning.fellBack());
        grounding.put("reason", reasoning.fallbackReason());
        payload.put("grounding", grounding);

        String patientId = patientContext != null && patientContext.get("patient_id") != null
                ? patientContext.get("patient_id").toString()
                : null;

        Map<String, Object> inputContext = new LinkedHashMap<>();
        inputContext.put("tenant_id", tenantId);
        inputContext.put("question", q);
        inputContext.put("patient_context", patientContext == null ? Map.of() : patientContext);
        Map<String, Object> llmReasoning = new LinkedHashMap<>();
        llmReasoning.put("provenance", reasoning.provenance());
        llmReasoning.put("model", reasoning.model());
        llmReasoning.put("fed_citation_ids", citations.stream().map(c -> c.get("section_id")).toList());
        llmReasoning.put("used_citation_ids", reasoning.usedCitationIds());
        llmReasoning.put("grounding_ratio", reasoning.groundingRatio());
        llmReasoning.put("grounding_valid", reasoning.groundingValid());
        llmReasoning.put("fell_back", reasoning.fellBack());
        llmReasoning.put("fallback_reason", reasoning.fallbackReason());
        llmReasoning.put("llm_audit_ref", reasoning.llmAuditRef());
        llmReasoning.put("raw_structured_output", reasoning.rawLlmStructuredOutput());
        inputContext.put("llm_reasoning", llmReasoning);

        RecommendationTraceEntity trace = traceService.record(
                actorId,
                role,
                patientId,
                encounterId,
                "ASSISTANT_ASK",
                inputContext,
                payload,
                citations,
                ruleMaps,
                "ckp-seed-1",
                sourceVersion,
                supportMode
        );
        payload.put("trace_id", trace.getId().toString());

        return payload;
    }

    private static String resolveSupportMode(boolean noSections, boolean noMeds, boolean noRules) {
        if (!noRules) {
            return noSections && noMeds ? "RULE_EVALUATED" : "MIXED_RULE_AND_SOURCE";
        }
        if (!noSections || !noMeds) {
            return "SOURCE_GROUNDED";
        }
        return "INSUFFICIENT_EVIDENCE";
    }

    private static String excerpt(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String composeAnswer(
            String question,
            boolean citizenMode,
            List<SourceSectionEntity> sections,
            List<MedicineGuidanceEntity> meds,
            List<RuleAlert> alerts,
            Set<String> topics) {

        if (sections.isEmpty() && meds.isEmpty() && alerts.isEmpty()) {
            return "No validated national guidance excerpt is indexed for that question yet. "
                    + (citizenMode
                    ? "For personal symptoms, contact a registered health facility."
                    : "Consult EDLIZ directly or escalate to a senior clinician.");
        }

        StringBuilder sb = new StringBuilder();
        if (citizenMode) {
            sb.append("Educational summary (not personal medical advice): ");
        }
        if (!sections.isEmpty()) {
            sb.append(excerpt(sections.get(0).getRawText(), 600));
        } else if (!meds.isEmpty()) {
            MedicineGuidanceEntity m = meds.get(0);
            sb.append(m.getMedicineName()).append(" — ").append(m.getNotes());
        }
        if (!alerts.isEmpty()) {
            sb.append("\n\nAlerts:\n");
            for (RuleAlert a : alerts) {
                sb.append("- ").append(a.message()).append("\n");
            }
        }
        if (topics.contains("neonatal_sepsis") && !citizenMode) {
            sb.append("\n\nNeonatal sepsis within 48 hours requires urgent neonatal unit assessment and weight-based dosing.");
        }
        return sb.toString().trim();
    }

    private static String buildRationale(List<SourceSectionEntity> sections, List<RuleAlert> alerts) {
        if (!sections.isEmpty()) {
            return "Answer draws on indexed EDLIZ-style source sections with page references in citations.";
        }
        if (!alerts.isEmpty()) {
            return "Answer is driven by evaluated clinical rules with deterministic inputs.";
        }
        return "Limited indexed content matched; expand curated extracts after PDF ingestion.";
    }
}
