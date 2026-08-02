package zw.gov.mohcc.impilo.tshepo.authz.core;

import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Regenerates operating-context trust headers from server-validated evidence.
 *
 * <p>Doctrine: "all client-supplied trust headers are stripped and regenerated only from validated
 * evidence" (trust-plane doctrine §6). Until now the PDP regenerated identity
 * ({@code x-actor-id}, {@code x-tenant-id}, {@code x-provider-id}, {@code x-subject-id},
 * {@code x-assurance-level}) but <em>not</em> operating context, so the browser's
 * {@code x-facility-id}, {@code x-workspace-id}, {@code x-department-id}, {@code x-ward-id},
 * {@code x-programme-id} and {@code x-purpose-of-use} travelled to every service exactly as the
 * client set them. That could not be fixed by stripping alone: strip without a regenerator deletes
 * the context the estate runs on.</p>
 *
 * <h2>Where the validated values come from</h2>
 *
 * <p>The work-context (duty) token. It is minted by {@code tshepo-identity} against Vashandi /
 * org-registry and introspected per request, so its claims are server-validated in a way a request
 * header never is. {@link DutyContext#usable()} means introspection returned an active
 * {@code WORK_CONTEXT} token.</p>
 *
 * <p>Purpose of use is regenerated from a different validated source: the closed
 * {@link PurposeOfUse} vocabulary. An unparseable purpose is already denied upstream, so echoing
 * the parsed enum name normalises casing and guarantees the downstream value is one of the
 * recognised constants rather than arbitrary client text.</p>
 *
 * <h2>What is deliberately NOT regenerated</h2>
 *
 * <ul>
 *   <li>{@code x-shift-id} — no server-validated source exists. {@link DutyContext} has no shift
 *       claim, so there is nothing to regenerate it from. Claiming authority over it would mean
 *       either deleting it or echoing the client's own value back as though it were validated.</li>
 *   <li>{@code x-workflow-state} — same: no validated source, and policy rules read it
 *       ({@code allowed_workflow_states}).</li>
 *   <li>{@code x-work-context-token} — a credential, not a claim. It is self-authenticating:
 *       a forged token fails introspection, so passing it through costs nothing and lets an
 *       upstream re-introspect it.</li>
 * </ul>
 *
 * <p>Those two remaining gaps are real and are recorded as such rather than papered over.</p>
 */
public final class ContextHeaderAuthority {

    /**
     * Rollout mode for context regeneration.
     *
     * <p>{@code AUTHORITATIVE} changes what every downstream service sees, so it is not something
     * to flip blind. {@code SHADOW} answers the question that decides whether it is safe: how often
     * does the client's claimed context differ from the duty token's?</p>
     */
    public enum Mode {
        /** Emit nothing. Client values travel unchanged. The pre-Checkpoint-4 behaviour. */
        PASSTHROUGH,
        /** Emit nothing, but measure client-vs-validated divergence. */
        SHADOW,
        /** Emit validated values; Envoy strips the client's and re-adds these. */
        AUTHORITATIVE;

        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return PASSTHROUGH;
            }
            String v = raw.trim().toUpperCase(Locale.ROOT);
            for (Mode m : values()) {
                if (m.name().equals(v)) {
                    return m;
                }
            }
            // A typo must not silently become PASSTHROUGH: that would disable the control while
            // the configuration claims it is on.
            throw new IllegalArgumentException(
                    "tshepo.authz.context-header-mode is '" + raw + "'; expected PASSTHROUGH, SHADOW or AUTHORITATIVE");
        }
    }

    /** Headers this class can regenerate, in the order they are emitted. */
    public static final List<String> REGENERATED_HEADERS = List.of(
            TrustHeaders.FACILITY_ID,
            TrustHeaders.WORKSPACE_ID,
            TrustHeaders.DEPARTMENT_ID,
            TrustHeaders.WARD_ID,
            TrustHeaders.PROGRAMME_ID,
            TrustHeaders.PURPOSE_OF_USE);

    /** Context headers with no server-validated source. Named so the gap cannot be forgotten. */
    public static final Map<String, String> UNREGENERATED_HEADERS = Map.of(
            TrustHeaders.SHIFT_ID, "no shift claim exists on the work-context token",
            TrustHeaders.WORKFLOW_STATE, "no server-validated workflow source exists",
            TrustHeaders.WORK_CONTEXT_TOKEN, "a credential, self-authenticating via introspection");

    private ContextHeaderAuthority() {
    }

    /**
     * Validated context headers for this request, or an empty map when nothing can be validated.
     *
     * <p>An absent or unusable duty token yields no context headers at all. That is the correct
     * outcome and the reason this is staged: if the trust plane cannot validate a facility, then
     * for this request there <em>is</em> no validated facility, and the client asserting one is
     * precisely the exposure being closed.</p>
     */
    public static Map<String, String> validatedContext(AuthzInternalRequest request, PurposeOfUse purpose) {
        Map<String, String> headers = new LinkedHashMap<>();
        DutyContext duty = request.dutyContext();
        if (duty != null && duty.usable()) {
            put(headers, TrustHeaders.FACILITY_ID, duty.facilityId());
            put(headers, TrustHeaders.WORKSPACE_ID, duty.workspaceId());
            put(headers, TrustHeaders.DEPARTMENT_ID, duty.departmentId());
            put(headers, TrustHeaders.WARD_ID, duty.wardId());
            put(headers, TrustHeaders.PROGRAMME_ID, duty.programmeId());
        }
        if (purpose != null) {
            headers.put(TrustHeaders.PURPOSE_OF_USE, purpose.name());
        }
        return headers;
    }

    /** One header whose client-supplied value disagrees with the validated one. */
    public record ContextDivergence(String header, boolean clientSupplied, boolean validatedPresent, boolean differs) {
    }

    /**
     * Compare what the client claimed against what the duty token validates.
     *
     * <p>Reports the header names only. The values are facility and programme identifiers — real
     * operational data that has no business in a metric tag or a log line at this volume.</p>
     */
    public static List<ContextDivergence> compare(AuthzInternalRequest request, PurposeOfUse purpose) {
        Map<String, String> validated = validatedContext(request, purpose);
        Map<String, String> claimed = new LinkedHashMap<>();
        putUuid(claimed, TrustHeaders.FACILITY_ID, request.facilityId());
        putUuid(claimed, TrustHeaders.WORKSPACE_ID, request.workspaceId());
        put(claimed, TrustHeaders.DEPARTMENT_ID, request.departmentId());
        put(claimed, TrustHeaders.WARD_ID, request.wardId());
        put(claimed, TrustHeaders.PROGRAMME_ID, request.programmeId());
        put(claimed, TrustHeaders.PURPOSE_OF_USE, request.purposeOfUse());

        return REGENERATED_HEADERS.stream()
                .map(h -> {
                    String c = claimed.get(h);
                    String v = validated.get(h);
                    boolean differs = c != null && v != null && !equalsIgnoringCase(c, v);
                    return new ContextDivergence(h, c != null, v != null, differs);
                })
                .filter(d -> d.clientSupplied() || d.validatedPresent())
                .toList();
    }

    private static boolean equalsIgnoringCase(String a, String b) {
        return Objects.equals(a.trim().toLowerCase(Locale.ROOT), b.trim().toLowerCase(Locale.ROOT));
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private static void putUuid(Map<String, String> target, String key, java.util.UUID value) {
        if (value != null) {
            target.put(key, value.toString());
        }
    }
}
