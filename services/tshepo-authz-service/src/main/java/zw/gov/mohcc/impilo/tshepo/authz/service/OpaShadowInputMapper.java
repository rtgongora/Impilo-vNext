package zw.gov.mohcc.impilo.tshepo.authz.service;

import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the {@code input} document for {@code data.impilo.authz.decision}.
 *
 * <p>This mapper exists as its own class because the previous inline version silently sent keys
 * the policy does not read. It set {@code identity_loa} and {@code authentication_aal}, while
 * {@code infra/opa/authz/authz.rego} reads {@code input.loa} and {@code input.assurance_loa}.
 * Both of the policy's LoA accessors fall back to {@code 0} when their key is absent, so
 * {@code effective_loa} evaluated to 0 for every request and {@code MIN_LOA} would have fired on
 * essentially all traffic the moment the mode left OFF. A shadow comparison that measures a typo
 * is worse than none, because its divergence rate looks like a policy disagreement.</p>
 *
 * <h2>Deliberate omissions</h2>
 *
 * <p>Some policy rules read fields whose vocabulary this service does not have. They are left
 * <em>absent</em> rather than filled with the nearest-looking value, because every one of those
 * rules is written to be inert when its field is missing — so an absent field is honestly inert,
 * whereas a wrong-vocabulary value is a rule that can never fire while appearing wired.</p>
 *
 * <ul>
 *   <li>{@code access_mode} — the policy expects an actor <em>zone</em>
 *       ({@code WORK|PROFESSIONAL|LIFE}). The nearest value here is {@link DutyContext#workMode()},
 *       drawn from a different vocabulary entirely ({@code CLINICAL_CARE}, {@code VIRTUAL_CARE},
 *       {@code REGULATORY_OPERATIONS}, …). Sending it would make {@code WORK_REQUIRES_ASSIGNMENT}
 *       and {@code WORK_TOKEN_CONTEXT_MISMATCH} permanently unmatched.</li>
 *   <li>{@code action} — the policy expects a fine-grained verb ({@code PROVIDER_CLAIM_APPROVE},
 *       {@code AUTHENTICATE}). {@link AuthzInternalRequest#action()} is {@code METHOD:path}.</li>
 *   <li>{@code regulated_action}, {@code identifier_kind} — no equivalent is resolved here.</li>
 * </ul>
 *
 * <p>{@link #COMPARABLE_DENY_REASONS} names the rules this input can actually exercise. Shadow
 * parity may only ever be claimed over that set.</p>
 */
public final class OpaShadowInputMapper {

    /**
     * Policy deny reasons this input can genuinely produce. Anything the policy defines outside
     * this set is inert under the current mapping and must not be counted as "in parity".
     */
    public static final Set<String> COMPARABLE_DENY_REASONS = Set.of(
            "INVALID_PURPOSE",
            "MIN_LOA",
            "ACCOUNT_NOT_VERIFIED",
            "SELF_TREATMENT");

    /**
     * Dimensions the Java engine enforces that the policy corpus has no rule for. These are the
     * opposite failure to {@link #INERT_DENY_REASONS}: not a rule this input cannot reach, but a
     * check the policy does not implement at all — so OPA will ALLOW where Java DENIES, and the
     * divergence is a genuine coverage gap in the corpus rather than a mapping fault.
     *
     * <p>{@code min_aal} is enforced by {@code PolicyEngine.evaluateConditions} but
     * {@code infra/opa/authz/authz.rego} defines no MIN_AAL deny reason. The key is therefore not
     * emitted: sending a value nothing reads is the same silent no-op as the {@code identity_loa}
     * defect. Closing this gap means adding the rule to the corpus, not adding the key here.</p>
     */
    public static final Map<String, String> POLICY_COVERAGE_GAPS = Map.of(
            "min_aal", "Java enforces a minimum AAL; the policy defines no MIN_AAL rule",
            "max_auth_age_seconds", "Java enforces authentication freshness; the policy has no equivalent",
            "phishing_resistant_required", "Java enforces phishing resistance; the policy has no equivalent");

    /** Rules the policy defines but this mapping cannot exercise, with the field that is missing. */
    public static final Map<String, String> INERT_DENY_REASONS = Map.of(
            "PROVIDER_SELF_CLAIM", "action (fine-grained verb vocabulary)",
            "PROVIDER_ID_REQUIRED", "regulated_action",
            "WORK_REQUIRES_ASSIGNMENT", "access_mode (actor zone vocabulary)",
            "LOGIN_PROVIDERID_DENY", "action + identifier_kind",
            "BADGE_NEVER_AUTHORISES", "action + identifier_kind",
            "LOGIN_PERSON_FIRST", "action + identifier_kind",
            "WORK_TOKEN_CONTEXT_MISMATCH", "access_mode (actor zone vocabulary)");

    private OpaShadowInputMapper() {
    }

    /**
     * @param request      the request under evaluation
     * @param purpose      resolved purpose of use, or null when the request never got that far
     * @param ruleMinLoa   {@code min_loa} from the matched rule, or null when no rule matched
     * @param ruleMinAal   {@code min_aal} from the matched rule, or null
     * @param accountAssuranceRequired whether the matched rule demands a verified account
     */
    public static Map<String, Object> build(AuthzInternalRequest request,
                                            PurposeOfUse purpose,
                                            Integer ruleMinLoa,
                                            Integer ruleMinAal,
                                            boolean accountAssuranceRequired,
                                            int identityLoa) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("actor_id", request.actorId() != null ? request.actorId() : "");
        input.put("purpose", purpose != null ? purpose.name() : "");

        // The key the policy actually reads. `loa` (the ACR login level) is deliberately not sent:
        // this service tracks authentication assurance as AAL on a separate scale, and feeding AAL
        // into an LoA maximum would make the comparison disagree with the Java min_loa check for a
        // reason that is an artefact of the mapping rather than a policy difference.
        input.put("assurance_loa", identityLoa);

        if (ruleMinLoa != null) {
            input.put("min_loa", ruleMinLoa);
        }
        // ruleMinAal is deliberately NOT emitted -- see POLICY_COVERAGE_GAPS. The policy defines no
        // MIN_AAL rule, so the key would be read by nothing. It stays in the signature because the
        // gap register is asserted against what the caller actually has available.
        if (accountAssuranceRequired) {
            input.put("account_assurance_required", true);
        }

        // SELF_TREATMENT needs all three; each is the same vocabulary on both sides (an id).
        putIfPresent(input, "subject_id", request.subjectId());
        putIfPresent(input, "provider_id", effectiveProviderId(request));

        // Supplied because they are real, same-vocabulary data. WORK_TOKEN_CONTEXT_MISMATCH stays
        // inert until access_mode can be supplied, which is recorded in INERT_DENY_REASONS.
        DutyContext duty = request.dutyContext();
        if (duty != null && duty.usable()) {
            input.put("assignment_active", true);
            Map<String, Object> workContext = new LinkedHashMap<>();
            putIfPresent(workContext, "facility_id", duty.facilityId());
            putIfPresent(workContext, "workspace_id", duty.workspaceId());
            if (!workContext.isEmpty()) {
                input.put("work_context", workContext);
            }
        }
        Map<String, Object> scope = new LinkedHashMap<>();
        putIfPresent(scope, "facility", request.facilityId() == null ? null : request.facilityId().toString());
        putIfPresent(scope, "workspace", request.workspaceId() == null ? null : request.workspaceId().toString());
        if (!scope.isEmpty()) {
            input.put("scope", scope);
        }
        return input;
    }

    /**
     * Java deny codes produced by the database policy_rule evaluation (step 4).
     *
     * <p>The policy corpus states its own scope: it decides the self-contained gate sub-decision
     * (purpose validity, min_loa, account assurance) and "the DB-rule RBAC/ABAC … is intentionally
     * NOT decided here". So when Java denies for one of these and OPA allows with no reasons, the
     * two engines did not disagree — OPA was never asked the question. Scoring that as divergence
     * would report a permanent, expected, ~100% divergence rate on all rule-governed traffic and
     * bury any real disagreement underneath it.</p>
     */
    public static final Set<String> JAVA_ONLY_RULE_DENY_CODES = Set.of(
            "NO_MATCHING_RULES",
            "POLICY_DENY",
            "NO_ALLOW_RULE");

    /** How a Java/OPA disagreement should be attributed. */
    public enum DivergenceKind {
        /** Both engines decided the same question and disagreed. This is the one that gates a cut-over. */
        REAL,
        /** OPA allowed because it does not implement the rule class Java denied on. */
        NO_RULE_COVERAGE,
        /** OPA's reasons are all rules this input cannot exercise — a mapping fault. */
        UNMAPPABLE
    }

    /**
     * Attribute a divergence. {@code javaDenyCode} is the Java {@code errorCode} (null on allow).
     */
    public static DivergenceKind classify(boolean opaAllow, String javaDenyCode,
                                          java.util.List<String> opaDenyReasons) {
        if (opaAllow && javaDenyCode != null && JAVA_ONLY_RULE_DENY_CODES.contains(javaDenyCode)) {
            return DivergenceKind.NO_RULE_COVERAGE;
        }
        if (opaDenyReasons != null && !opaDenyReasons.isEmpty()
                && opaDenyReasons.stream().noneMatch(COMPARABLE_DENY_REASONS::contains)) {
            return DivergenceKind.UNMAPPABLE;
        }
        return DivergenceKind.REAL;
    }

    private static String effectiveProviderId(AuthzInternalRequest request) {
        DutyContext duty = request.dutyContext();
        if (duty != null && duty.usable() && duty.providerId() != null) {
            return duty.providerId();
        }
        return request.providerId();
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    /** Normalised deny-reason label for metrics; bounded by the policy's own vocabulary. */
    public static String metricReason(java.util.List<String> denyReasons) {
        if (denyReasons == null || denyReasons.isEmpty()) {
            return "none";
        }
        return denyReasons.stream()
                .map(r -> r == null ? "" : r.trim().toUpperCase(Locale.ROOT))
                .filter(r -> COMPARABLE_DENY_REASONS.contains(r) || INERT_DENY_REASONS.containsKey(r))
                .findFirst()
                .orElse("other");
    }
}
