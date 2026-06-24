package zw.gov.mohcc.impilo.clinical.reasoning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.clinical.llm.LlmClinicalClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM-backed grounded reasoner. Calls the governed llm-orchestration service for a structured,
 * grounded answer, validates it ({@link GroundingValidator}), and emits an {@code LLM} result only
 * when a REAL provider returned a grounding-valid output. On ANY failure — disabled, stub provider,
 * timeout, malformed/ungrounded output, empty-retrieval-but-answered — it delegates to the
 * {@link DeterministicClinicalReasoner} (the honest floor) and marks the fallback reason.
 */
public class LlmClinicalReasoner implements ClinicalReasoner {

    private static final Logger log = LoggerFactory.getLogger(LlmClinicalReasoner.class);

    private final LlmClinicalClient client;
    private final ClinicalPromptBuilder promptBuilder;
    private final GroundingValidator groundingValidator;
    private final ClinicalReasoner deterministicFallback;
    private final double minGroundingRatio;

    public LlmClinicalReasoner(
            LlmClinicalClient client,
            ClinicalPromptBuilder promptBuilder,
            GroundingValidator groundingValidator,
            ClinicalReasoner deterministicFallback,
            double minGroundingRatio) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.groundingValidator = groundingValidator;
        this.deterministicFallback = deterministicFallback;
        this.minGroundingRatio = minGroundingRatio;
    }

    @Override
    public boolean isLlmBacked() {
        return true;
    }

    @Override
    public ReasoningResult reason(ReasoningRequest request) {
        Optional<LlmClinicalClient.LlmStructuredResult> maybe = client.requestStructured(
                "CLINICAL_ASSISTANT_ASK",
                promptBuilder.assistantMessages(request),
                promptBuilder.assistantSchema(),
                actorContext(request),
                "HIGH",
                0.1);

        if (maybe.isEmpty()) {
            return fallback("llm-unavailable-or-stub");
        }
        LlmClinicalClient.LlmStructuredResult res = maybe.get();
        Map<String, Object> output = res.structuredOutput();

        Set<String> retrievedIds = request.citations() == null ? Set.of()
                : request.citations().stream()
                        .map(c -> c.get("section_id"))
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toSet());

        GroundingValidator.GroundingReport report = groundingValidator.validate(output, retrievedIds, minGroundingRatio);
        if (!report.valid()) {
            log.info("Clinical LLM output rejected ({}) — falling back to deterministic", report.reason());
            return fallback("grounding:" + report.reason());
        }

        boolean insufficient = Boolean.TRUE.equals(output.get("insufficient_evidence"));
        String answer = str(output.get("answer_summary"));
        // Honest: an "insufficient evidence" LLM verdict must not override the deterministic answer.
        if (insufficient || answer == null || answer.isBlank()) {
            return fallback("llm-insufficient-evidence");
        }

        return new ReasoningResult(
                "LLM",
                answer,
                buildRationale(output),
                differentials(output),
                stringList(output.get("used_citation_ids")),
                false,
                stringList(output.get("safety_flags")),
                res.model(),
                report.ratio(),
                true,
                false,
                null,
                res.auditRef(),
                output);
    }

    private ReasoningResult fallback(String reason) {
        ReasoningResult det = deterministicFallback.reason(null);
        return new ReasoningResult(
                det.provenance(), det.answerSummary(), det.rationale(), det.differentialConsiderations(),
                det.usedCitationIds(), det.insufficientEvidence(), det.safetyFlags(), det.model(),
                det.groundingRatio(), det.groundingValid(), true, reason, det.llmAuditRef(), det.rawLlmStructuredOutput());
    }

    private static Map<String, Object> actorContext(ReasoningRequest req) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("tenantId", req.tenantId() == null ? "default" : req.tenantId());
        ctx.put("actorId", req.actorId() == null ? "anonymous" : req.actorId());
        ctx.put("role", req.role() == null ? "PROVIDER" : req.role());
        ctx.put("purposeOfUse", "CLINICAL_DECISION_SUPPORT");
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> differentials(Map<String, Object> output) {
        Object d = output.get("differential_considerations");
        if (d instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }

    private static String buildRationale(Map<String, Object> output) {
        Object d = output.get("differential_considerations");
        int n = d instanceof List<?> l ? l.size() : 0;
        return "Grounded LLM reasoning over indexed EDLIZ sections with "
                + n + " differential consideration(s); citations validated against retrieved sources.";
    }

    private static List<String> stringList(Object o) {
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
