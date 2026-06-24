package zw.gov.mohcc.impilo.clinical.reasoning;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates an LLM structured output against the context it was given, so an ungrounded or
 * hallucinated answer is rejected and the caller falls back to the deterministic path.
 *
 * <p>Checks: (1) schema shape; (2) citation closure — every cited id must be a real retrieved
 * section id; (3) grounding ratio — a substantive answer must cite enough of the retrieved set;
 * (4) empty-retrieval guard — with no sources the only valid output is insufficient_evidence=true.
 */
@Component
public class GroundingValidator {

    public record GroundingReport(boolean valid, double ratio, String reason) {
        static GroundingReport ok(double ratio) {
            return new GroundingReport(true, ratio, "grounded");
        }

        static GroundingReport invalid(String reason) {
            return new GroundingReport(false, 0.0, reason);
        }
    }

    @SuppressWarnings("unchecked")
    public GroundingReport validate(Map<String, Object> output, Set<String> retrievedCitationIds, double minGroundingRatio) {
        if (output == null || output.isEmpty()) {
            return GroundingReport.invalid("empty-output");
        }
        // (1) shape
        if (!(output.get("insufficient_evidence") instanceof Boolean insufficient)) {
            return GroundingReport.invalid("missing-insufficient_evidence");
        }
        String answer = output.get("answer_summary") == null ? "" : output.get("answer_summary").toString().trim();

        // collect every citation reference the model made
        List<String> refs = new ArrayList<>(asStringList(output.get("used_citation_ids")));
        Object diffs = output.get("differential_considerations");
        if (diffs instanceof List<?> list) {
            for (Object d : list) {
                if (d instanceof Map<?, ?> m) {
                    refs.addAll(asStringList(m.get("citation_refs")));
                }
            }
        }
        Object rec = output.get("recommendation");
        if (rec instanceof Map<?, ?> m) {
            refs.addAll(asStringList(m.get("citation_refs")));
        }

        boolean retrievalEmpty = retrievedCitationIds == null || retrievedCitationIds.isEmpty();

        // (4) empty-retrieval guard: with no sources, only an honest "insufficient evidence" is acceptable
        if (retrievalEmpty) {
            if (insufficient && refs.isEmpty()) {
                return GroundingReport.ok(0.0);
            }
            return GroundingReport.invalid("answered-without-sources");
        }

        // (2) citation closure
        for (String ref : refs) {
            if (!retrievedCitationIds.contains(ref)) {
                return GroundingReport.invalid("hallucinated-citation:" + ref);
            }
        }

        if (insufficient) {
            return GroundingReport.ok(0.0);
        }

        // (3) grounding ratio for a substantive answer
        long used = refs.stream().distinct().filter(retrievedCitationIds::contains).count();
        if (!answer.isEmpty() && used == 0) {
            return GroundingReport.invalid("uncited-answer");
        }
        double ratio = retrievedCitationIds.isEmpty() ? 0.0 : (double) used / retrievedCitationIds.size();
        if (!answer.isEmpty() && ratio < minGroundingRatio) {
            return GroundingReport.invalid("low-grounding:" + String.format("%.2f", ratio));
        }
        return GroundingReport.ok(ratio);
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
}
