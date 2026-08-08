package zw.gov.mohcc.impilo.zibo.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Which actor types may change governed national terminology.
 *
 * <h2>What was measured</h2>
 * <p>2026-08-08: ZIBO exposed <b>22 write endpoints and zero authorization checks</b>. No
 * {@code @PreAuthorize}, no {@code @Secured}, no actor-type guard, no interceptor — a repository-wide
 * grep for every authorization construct returned nothing in this service, main or test. Authorization
 * was binary: authenticated or not. Anyone holding any valid token could publish, deprecate or retire
 * a national CodeSystem, reassign a terminology pack to a facility, or rebuild the mapping index.</p>
 *
 * <p>That was tolerable only while ZIBO held 254 seed concepts nobody could search. It stops being
 * tolerable the moment real national content loads, which is why this lands before Tier 1 content
 * rather than after it.</p>
 *
 * <h2>Why an interceptor and not {@code @PreAuthorize}</h2>
 * <p>The obvious instrument does not work here, and it fails silently, which is worse.</p>
 * <ul>
 *   <li>{@code @EnableMethodSecurity} is not on in this service, so the annotations would be
 *       <b>inert</b> — present, reviewed, enforcing nothing.</li>
 *   <li>Even switched on, {@code hasAnyRole(...)} refuses everyone: the workload token
 *       experience-bff mints carries {@code realm_access: null} and scopes only, so no role reaches
 *       this service. {@code ActorTypeGuard}'s own javadoc records why — there is no role header in
 *       the trust contract and none in Envoy's {@code allowed_upstream_headers}.</li>
 * </ul>
 *
 * <h2>Why a policy rule alone is not enough either</h2>
 * <p>ZIBO has no Envoy route. Every route in the gateway targets {@code experience_bff}, so only the
 * BFF proxy path {@code /internal/v1/registry/zibo/**} passes through ext_authz. A direct call to
 * {@code zibo-service:8085} never reaches the PDP at all — and NetworkPolicy is not enforced on this
 * cluster, so anything in-cluster can make one. A {@code policy_rule} row is still worth having for
 * the proxied path; it cannot be the only gate.</p>
 *
 * <h2>The gate, and what it deliberately does not gate</h2>
 * <p>Changing governed terminology requires {@link ActorTypeGuard#BACK_OFFICE_WRITERS} —
 * {@code SYSTEM}, {@code SERVICE}, {@code OPERATOR}. Terminology stewardship is an operator act.
 * {@code PROVIDER} is excluded on purpose: a clinician consumes the national vocabulary, and must
 * not be able to retire a CodeSystem from a consulting room. {@code CITIZEN} and {@code CAREGIVER}
 * are excluded for the obvious reason.</p>
 *
 * <p>{@link #READ_SHAPED_WRITES} lists the POSTs that answer a question rather than change
 * terminology — validation, translation, confidentiality classification. They are POST only because
 * their input does not fit in a query string. They sit on the clinical path and are called by
 * BUTANO's validation interceptor, OROS, MSIKA and the teleconsult response validator. Gating those
 * to back-office would 403 terminology validation during care: an outage dressed as a hardening, and
 * precisely the failure the estate has already had once when deny-by-default killed 69 pages.</p>
 *
 * <p>Everything not listed gets the strict default, so a newly-mapped endpoint is gated the moment
 * it is mapped rather than being open until somebody notices.</p>
 */
final class TerminologyWriteAuthorization {

    private TerminologyWriteAuthorization() {
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * POSTs that compute an answer and change no governed terminology.
     *
     * <p>Each entry was justified by finding its live caller, not by reading the path name. They
     * write at most a {@code zibo_validation_logs} row or a {@code zibo_validation_jobs} request —
     * telemetry and work queues, not the artifacts of record.</p>
     */
    private static final Set<String> READ_SHAPED_WRITES = Set.of(
            // BUTANO's TerminologyValidationInterceptor, on every coded clinical resource it stores.
            "/api/v1/terminology/validate",
            // OROS lab-order coding, MSIKA, and experience-bff's teleconsult response validation.
            "/v1/validate/coding",
            "/v1/validate/resource",
            // A validation request, not a terminology change. Submitting one asks ZIBO to measure
            // how coded something is — the thing this whole programme exists to be able to answer.
            "/v1/validate/job",
            // ConceptMap translation lookup. POST only because the source coding is a body.
            "/v1/map",
            // Sensitivity classification, called by the confidential-lane routing on the read path.
            "/internal/v1/confidentiality/classify");

    /**
     * Human-readable descriptions for the governed writes, so a refused operator reads
     * "publishing a terminology artifact requires ..." rather than a path pattern.
     *
     * <p>Ordered: first match wins, so the more specific pattern comes first.</p>
     */
    private static final Map<String, String> GOVERNED_WRITES = new LinkedHashMap<>();

    static {
        GOVERNED_WRITES.put("/v1/artifacts/*/publish", "publishing a terminology artifact");
        GOVERNED_WRITES.put("/v1/artifacts/*/deprecate", "deprecating a terminology artifact");
        GOVERNED_WRITES.put("/v1/artifacts/*/retire", "retiring a terminology artifact");
        GOVERNED_WRITES.put("/v1/artifacts/**", "creating or editing a terminology artifact");
        GOVERNED_WRITES.put("/v1/packs/*/publish", "publishing a terminology pack");
        GOVERNED_WRITES.put("/v1/packs/**", "changing a terminology pack");
        GOVERNED_WRITES.put("/v1/assignments/**", "assigning terminology to a scope");
        GOVERNED_WRITES.put("/v1/import/**", "importing terminology content");
        GOVERNED_WRITES.put("/v1/map/rebuild", "rebuilding the mapping index");
        GOVERNED_WRITES.put("/v1/concepts/rebuild", "rebuilding the concept index");
        GOVERNED_WRITES.put("/internal/v1/snapshots/**", "emitting a terminology snapshot");
    }

    /** True when this request answers a question rather than changing governed terminology. */
    static boolean isReadShaped(String path) {
        return READ_SHAPED_WRITES.contains(path);
    }

    /**
     * The duty a write on {@code path} must satisfy. Never null: an unlisted path gets the governed
     * default, so a newly-mapped endpoint is gated rather than open.
     */
    static ActorTypeGuard.Duty dutyFor(String path) {
        for (var e : GOVERNED_WRITES.entrySet()) {
            if (MATCHER.match(e.getKey(), path)) {
                return ActorTypeGuard.Duty.of(e.getValue(), ActorTypeGuard.BACK_OFFICE_WRITERS);
            }
        }
        return ActorTypeGuard.Duty.of("changing governed terminology",
                ActorTypeGuard.BACK_OFFICE_WRITERS);
    }
}
