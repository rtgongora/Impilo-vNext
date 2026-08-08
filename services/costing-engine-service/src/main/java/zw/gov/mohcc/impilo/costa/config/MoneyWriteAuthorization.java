package zw.gov.mohcc.impilo.costa.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Which actor types may perform each write in this service.
 *
 * <h2>Why this is a map and not 81 call sites</h2>
 * <p>Measured 2026-08-07: of the 25 controllers here that expose a write, <b>24 had no
 * authorization of any kind</b> — no {@code @PreAuthorize}, no actor-type check, nothing. Only
 * {@code WaiverController} was guarded. That is 81 unguarded write mappings across bills, payments,
 * cashup, budgets, tariffs, receivables and cost centres, in the service that owns money.</p>
 *
 * <p>Guarding those one method at a time would have left the same defect one edit away: the next
 * controller added to this service inherits nothing. Here the default is
 * {@link ActorTypeGuard#BACK_OFFICE_WRITERS} and a path must be listed to get anything <em>wider</em>,
 * so a new endpoint is gated the moment it is mapped, and {@code MoneyWriteAuthorizationTest}
 * fails if any mapped write cannot be resolved to a duty.</p>
 *
 * <h2>Why not {@code @PreAuthorize}</h2>
 * <p>The obvious instrument does not work here, and it fails silently, which is worse.</p>
 * <ul>
 *   <li>{@code @EnableMethodSecurity} is not on in this service, so the annotations would be
 *       <b>inert</b> — present, reviewed, enforcing nothing.</li>
 *   <li>Even switched on, {@code hasAnyRole(...)} would refuse everyone. Measured against the live
 *       realm: the workload token experience-bff mints ({@code client_credentials} on
 *       {@code impilo-bff}) carries {@code realm_access: null}, {@code resource_access: {}} and
 *       scopes only. No role reaches this service, and {@code ActorTypeGuard}'s own javadoc records
 *       why: there is no role header in the trust contract and none in Envoy's
 *       {@code allowed_upstream_headers}.</li>
 * </ul>
 *
 * <p>So the enforceable question is "what kind of principal is this?", not "which duty do they
 * hold". The finer distinction is P3 workload-identity work and is deliberately not faked here.</p>
 *
 * <h2>The two sets, and why the wider one exists</h2>
 * <p>{@link ActorTypeGuard#BACK_OFFICE_WRITERS} for anything that <em>decides</em> money —
 * approving, finalising, refunding, writing off, posting to a budget, closing a period.
 * {@link ActorTypeGuard#CHARGE_CAPTURE} for <em>capturing</em> what was done at the point of care,
 * which admits {@code PROVIDER} as well.</p>
 *
 * <p>The wider set is not a convenience. {@code POST /costa/v1/bills/draft} is called by
 * {@code EncounterController}, {@code MobileEncounterController},
 * {@code MobileProviderExtendedController}, {@code MobileDischargeController} and
 * {@code TeleconsultController}, and experience-bff forwards the caller's {@code X-Actor-Type}
 * unchanged — so a clinician raising a bill at the bedside arrives as {@code PROVIDER}. Gating that
 * to back-office would 403 every bill raised during care: an outage dressed as a hardening.</p>
 *
 * <p><b>Both sets exclude {@code CITIZEN} and {@code CAREGIVER}</b>, which is the exposure P0
 * named: the person whose bill it is must not be able to raise, price, approve or write off the
 * charge against themselves.</p>
 */
final class MoneyWriteAuthorization {

    private MoneyWriteAuthorization() {
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * Paths exempt from the money gate entirely. Only the v1.1 conformance probe, which writes
     * nothing — {@code CostaV11ProbeController} echoes its body back. It is driven by the in-JVM
     * golden-contract suite, which presents no actor type; gating it would fail that suite while
     * protecting nothing.
     */
    private static final Set<String> EXEMPT = Set.of(
            "/internal/v1/test-command",
            "/internal/v1/health");

    /**
     * Writes that CAPTURE care rather than decide money. Ordered map: first match wins, so the more
     * specific pattern must come first.
     *
     * <p>Every entry here was justified by finding a clinician-facing caller, not by reading the
     * path name. Where no such caller exists the stricter default applies, because a wrongly-wide
     * gate is invisible and a wrongly-narrow one shows up immediately as a 403.</p>
     */
    private static final Map<String, String> CHARGE_CAPTURE_PATHS = new LinkedHashMap<>();

    static {
        // Raised by the five clinician-facing BFF controllers listed in the class javadoc.
        CHARGE_CAPTURE_PATHS.put("/costa/v1/bills/draft", "raising a draft bill");
        // Adding and recomputing lines on a draft is the same act, continued.
        CHARGE_CAPTURE_PATHS.put("/costa/v1/bills/*/lines", "adding a charge line to a bill");
        CHARGE_CAPTURE_PATHS.put("/costa/v1/bills/*/recompute", "recomputing a bill's charges");
        // MobileProviderExtendedController.createEstimate — pricing, not deciding.
        CHARGE_CAPTURE_PATHS.put("/costa/v1/estimate", "pricing a cost estimate");
        CHARGE_CAPTURE_PATHS.put("/api/costa/cost-estimate", "pricing a cost estimate");
        // Emergency care is given before identity or payment is settled; the deferred charge is a
        // record of care delivered. Refusing it at the point of care would make the guard a reason
        // not to treat someone.
        CHARGE_CAPTURE_PATHS.put("/costa/v1/emergency-reconciliation/deferred-charges",
                "recording a deferred emergency charge");
        CHARGE_CAPTURE_PATHS.put("/costa/v1/finance/lifecycle/charges", "capturing a charge");
    }

    /** True when this request needs no money gate at all. */
    static boolean isExempt(String path) {
        return EXEMPT.contains(path);
    }

    /**
     * Whether a write DECIDES money rather than captures care — i.e. the paths where "which duty
     * does this person hold?" is the real question and {@link ActorTypeGuard} cannot answer it.
     *
     * <p>Defined as the complement of the charge-capture list, so it cannot drift away from it: a
     * path added to capture leaves the decision set automatically, and a newly-mapped path is a
     * decision by default.</p>
     */
    static boolean isMoneyDecision(String path) {
        if (isExempt(path)) {
            return false;
        }
        for (var e : CHARGE_CAPTURE_PATHS.entrySet()) {
            if (MATCHER.match(e.getKey(), path)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The duty a write on {@code path} must satisfy. Never null: an unlisted path gets the
     * stricter set, so a newly-mapped endpoint is gated rather than open.
     */
    static ActorTypeGuard.Duty dutyFor(String path) {
        for (var e : CHARGE_CAPTURE_PATHS.entrySet()) {
            if (MATCHER.match(e.getKey(), path)) {
                return ActorTypeGuard.Duty.of(e.getValue(), ActorTypeGuard.CHARGE_CAPTURE);
            }
        }
        return new ActorTypeGuard.Duty(
                "this money operation",
                ActorTypeGuard.BACK_OFFICE_WRITERS,
                // Recorded, not enforced: what this really wants is a finance duty. No header
                // carries one today. See ActorTypeGuard's KNOWN GAP.
                Set.of("FACILITY_FINANCE", "FINANCE_OFFICER"));
    }
}
