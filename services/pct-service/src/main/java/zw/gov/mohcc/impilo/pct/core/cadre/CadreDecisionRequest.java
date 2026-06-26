package zw.gov.mohcc.impilo.pct.core.cadre;

import java.util.Locale;

/**
 * Input to the {@link CadreEngine} (shared read-model C9).
 *
 * <p>All inputs are resolvable from existing read-models:
 * {@code role}/{@code cadre} from Varapi (C1), {@code scope} from Vashandi (C2),
 * {@code visitType} from the Sorting Desk (PCT, new), {@code acuity} from the PCT
 * TriageRecord, {@code context} from the six work contexts, and {@code accessState}
 * from the COSTA ServiceAccessDecision (C6).</p>
 */
public record CadreDecisionRequest(
        String role,
        String cadre,
        String scope,
        String visitType,
        Integer acuity,
        String context,
        String accessState) {

    /** Normalises a possibly-null token to upper-case, returning {@code def} when blank. */
    public static String norm(String raw, String def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public String roleOrUnknown()        { return norm(role, "UNKNOWN"); }
    public String cadreOrUnknown()       { return norm(cadre, "UNKNOWN"); }
    public String scopeOrFacility()      { return norm(scope, "FACILITY"); }
    public String visitTypeOrGeneral()   { return norm(visitType, "GENERAL"); }
    public String contextOrOutpatient()  { return norm(context, "OUTPATIENT"); }
    public String accessStateOrUnknown() { return norm(accessState, "UNKNOWN"); }

    /** Clamps acuity into the valid 1–5 band; defaults to 3 (mid) when unset. */
    public int acuityClamped() {
        if (acuity == null) {
            return 3;
        }
        return Math.max(1, Math.min(5, acuity));
    }
}
