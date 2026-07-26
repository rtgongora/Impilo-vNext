package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a clinical rule came from, and what we changed about it.
 *
 * <p>Two questions a clinician is entitled to ask of any recommendation on their screen: who says
 * so, and is this the international guidance or a local variation of it? Without the second, a
 * national adaptation is indistinguishable from the WHO position it departs from — and the places
 * where Zimbabwe deliberately differs from WHO are exactly the places where a reader most needs to
 * know that they do.
 *
 * <p>This is the projection carried on API responses. The authoritative record is the content JSON
 * itself, which carries the full adaptation block — WHO's original statement, ours, the rationale,
 * the approving authority, effective and review dates — and is checked by
 * {@code scripts/guard/check-dak-traceability.sh}. What travels to a caller is the citation, not the
 * committee paperwork.
 *
 * @param adaptationType one of ADOPTED_VERBATIM, STRENGTHENED, NARROWED, SPLIT, MERGED,
 *                       ADDED_NATIONAL, DEFERRED, REJECTED. {@code ADDED_NATIONAL} means there is no
 *                       WHO artefact behind this rule at all, which is a legitimate state and must be
 *                       visible rather than inferred from a missing reference.
 */
public record DakProvenance(
        String dak,
        String dakVersion,
        String artefactId,
        String row,
        String adaptationType,
        String approvingAuthority) {

    /**
     * Read provenance from a content node. Returns null when the rule declares none — absence is
     * left as absence here and reported by the guard, rather than being papered over with a
     * plausible-looking empty citation.
     */
    public static DakProvenance from(JsonNode dakRef, JsonNode adaptation) {
        boolean hasRef = dakRef != null && !dakRef.isNull() && dakRef.isObject();
        boolean hasAdaptation = adaptation != null && !adaptation.isNull() && adaptation.isObject();
        if (!hasRef && !hasAdaptation) {
            return null;
        }
        return new DakProvenance(
                hasRef ? text(dakRef, "dak") : null,
                hasRef ? text(dakRef, "dakVersion") : null,
                hasRef ? text(dakRef, "id") : null,
                hasRef ? text(dakRef, "row") : null,
                hasAdaptation ? text(adaptation, "type") : null,
                hasAdaptation ? text(adaptation, "approvingAuthority") : null);
    }

    /** True when this rule has no WHO artefact behind it and says so. */
    public boolean nationallyAdded() {
        return "ADDED_NATIONAL".equalsIgnoreCase(adaptationType);
    }

    /** True when the WHO text was changed rather than adopted as published. */
    public boolean adapted() {
        return adaptationType != null
                && !"ADOPTED_VERBATIM".equalsIgnoreCase(adaptationType)
                && !nationallyAdded();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (dak != null) {
            out.put("dak", dak);
        }
        if (dakVersion != null) {
            out.put("dak_version", dakVersion);
        }
        if (artefactId != null) {
            out.put("artefact_id", artefactId);
        }
        if (row != null) {
            out.put("row", row);
        }
        if (adaptationType != null) {
            out.put("adaptation_type", adaptationType);
        }
        if (approvingAuthority != null) {
            out.put("approving_authority", approvingAuthority);
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
