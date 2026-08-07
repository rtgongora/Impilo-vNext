package zw.gov.mohcc.impilo.shared.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.ActorType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place a service asks "is this actor type allowed to do this?".
 *
 * <h2>Why this exists</h2>
 * <p>Before this class there was no shared helper, and fourteen call sites had each copy-pasted
 * their own inline {@code Set.of(...)} of permitted actor types. Measured on
 * {@code feat/trust-domain-responsibility-foundations} at {@code 73cc29d27}, they had drifted into
 * seven distinct vocabularies:</p>
 *
 * <pre>
 * {SYSTEM}                                                        tuso FacilityVerificationController
 * {SYSTEM, REGISTRY_ADMIN}                                        varapi ProviderRecoveryService, ProviderBootstrapService
 * {SYSTEM, REGISTRY_ADMIN, NATIONAL_ADMIN}                        varapi ProviderAccessRequestService
 * {SYSTEM, REGISTRY_ADMIN, NATIONAL_ADMIN, DATA_STEWARD}          indawo, ndila, tuso x3
 * {SYSTEM, REGISTRY_ADMIN, NATIONAL_ADMIN, DATA_STEWARD,
 *  INSPECTOR, REGULATOR}                                          indawo SiteSelfServiceController
 * {SYSTEM, REGISTRY_ADMIN, NATIONAL_ADMIN, ORG_REPRESENTATIVE}    varapi ProviderBadgeController
 * {SYSTEM, NATIONAL_ADMIN, ORG_REPRESENTATIVE, OPERATOR,
 *  REGISTRY_ADMIN}                                                varapi ProviderBootstrapService
 * </pre>
 *
 * <h2>The finding that matters more than the drift</h2>
 * <p><b>Most of those tokens are values no client has ever put on the wire.</b> The actor-type
 * vocabulary is declared in two places that are kept in sync by
 * {@code ContractMirrorsTest} and {@code PolicyRuleSeedVocabularyTest}:</p>
 *
 * <ul>
 *   <li>{@code ui/shared-ui/lib/contracts.ts} — {@code "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM"}</li>
 *   <li>{@code ActorType} in {@code libs/tshepo-contracts} — the four above plus {@code CAREGIVER}
 *       and {@code SERVICE}, which only server-side emitters produce.</li>
 * </ul>
 *
 * <p>{@code REGISTRY_ADMIN}, {@code NATIONAL_ADMIN}, {@code DATA_STEWARD}, {@code INSPECTOR},
 * {@code REGULATOR}, {@code ORG_REPRESENTATIVE} and {@code FACILITY_FINANCE} are in none of them.
 * {@code ActorType}'s own javadoc states the consequence: actor type "flows through the PDP as a raw
 * string and is matched by string equality. Nothing at runtime will reject an unknown value — a seed
 * naming an actor type no client emits simply never matches, silently." The same is true of a
 * service-side guard. So each of those seven sets is, in practice, {@code {SYSTEM}} with a
 * decorative tail: the extra members can never match a real request, and they read as if a
 * distinction is being enforced when none is.</p>
 *
 * <p>That is why the constants below name only emittable values. A guard that lists a token nothing
 * emits is not stricter — it is a guard whose stated intent and actual behaviour disagree, which is
 * the harder defect to find.</p>
 *
 * <h2>KNOWN GAP — actor type is not a role (P3 workload-identity work)</h2>
 * <p>These guards can only distinguish <em>what kind of principal</em> is calling — a person, an
 * operator, a machine. They cannot distinguish <em>which duty</em> that principal holds. Envoy
 * ext_authz gates on role ({@code SYSTEM_ADMIN}, {@code HIE_ADMIN}, and the rest of
 * {@code policy_rule.role}); a service sees only {@code X-Actor-Type}. {@link TrustContext} carries
 * no roles, there is no role header in the trust contract, and none is listed in Envoy's
 * {@code allowed_upstream_headers} — so no service-side guard can express "a finance officer, but
 * not a ward clerk" today.</p>
 *
 * <p>Closing that is P3 workload-identity work and is deliberately <b>not</b> attempted here.
 * Until it lands, the honest ceiling for a service-side guard on a consequential write is
 * {@link #BACK_OFFICE_WRITERS}: keep citizens, patients and clinical sessions out of the ledger and
 * the waiver desk, and record that the finer distinction is enforced upstream or not at all.</p>
 *
 * <h2>Shape</h2>
 * <p>Modelled on {@code varapi ProviderAccessRequestService.requireReviewer}: a named constant set
 * rather than an inline {@code Set.of} at the call site; called <b>before</b> the entity is loaded so
 * the check cannot be used as an existence oracle; and a 403 that names exactly what is required,
 * because the first person refused will be a legitimate operator whose session lacks a matching
 * actor type, and a bare 403 sends them looking in the wrong place.</p>
 */
public final class ActorTypeGuard {

    private static final Logger log = LoggerFactory.getLogger(ActorTypeGuard.class);

    private ActorTypeGuard() {
    }

    /**
     * Machine callers only: schedulers, outbox publishers, and named platform services calling
     * under their own identity. No human session — of any kind — satisfies this.
     */
    public static final Set<String> MACHINE_ONLY = Set.of("SYSTEM", "SERVICE");

    /**
     * The ceiling for consequential back-office writes — GL journals, fee waivers, ledger appends.
     * Admits machine callers and administrative/operational staff ({@code OPERATOR}, which is what
     * the admin consoles and experience-bff's session resolution actually emit for facility admins,
     * finance and support).
     *
     * <p>Deliberately excludes {@code CITIZEN}, {@code PROVIDER} and {@code CAREGIVER}. A person
     * acting on their own behalf, a clinician acting in clinical capacity, and a guardian acting for
     * another are all real, authenticated principals — and none of them is a reason to be able to
     * post a journal or approve a waiver. That exclusion is the whole of what this set enforces; see
     * the KNOWN GAP above for what it cannot.</p>
     */
    public static final Set<String> BACK_OFFICE_WRITERS = Set.of("SYSTEM", "SERVICE", "OPERATOR");

    /** Every actor type any client can put on the wire, taken from the contract rather than retyped. */
    public static final Set<String> EMITTABLE = Arrays.stream(ActorType.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * A named thing a caller may be trying to do, carrying both halves of the answer: what is
     * actually <b>enforced</b>, and what the author actually <b>meant</b>.
     *
     * <h2>Why both</h2>
     * <p>The guards this class replaced were written against role names — {@code HPA_REGISTRAR},
     * {@code DATA_STEWARD}, {@code FACILITY_FINANCE}, {@code IDENTITY_STEWARD} — that no client
     * emits, so they enforced nothing those names describe. Deleting the names would make each
     * guard honest and would throw away the only written record of the distinction its author was
     * reaching for. That record is the input to the P3 workload-identity work, so it is kept here
     * as {@link #intendedRoles()} rather than dropped: declared, visibly unenforced, and in the
     * same object as the gate that stands in for it.</p>
     *
     * @param description  what the caller is trying to do, in words a refused operator can act on
     *                     (e.g. "waiving a facility registration fee")
     * @param actorTypes   the enforced gate. Validated at construction to contain only values some
     *                     client actually emits, so a dead token cannot be reintroduced here.
     * @param intendedRoles the finer role distinction this duty is really about, recorded and NOT
     *                     enforced. Validated to hold only non-emittable names — anything emittable
     *                     belongs in {@code actorTypes}, where it would take effect.
     */
    public record Duty(String description, Set<String> actorTypes, Set<String> intendedRoles) {

        public Duty {
            Objects.requireNonNull(description, "description");
            actorTypes = Set.copyOf(actorTypes);
            intendedRoles = intendedRoles == null ? Set.of() : Set.copyOf(intendedRoles);
            if (actorTypes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Duty '" + description + "' enforces an empty actor-type set, which refuses "
                                + "every caller. A duty nobody can perform is a closed door, not a guard.");
            }
            Set<String> dead = actorTypes.stream().filter(t -> !EMITTABLE.contains(t))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!dead.isEmpty()) {
                throw new IllegalArgumentException(
                        "Duty '" + description + "' enforces " + dead + ", which no client emits — the "
                                + "gate would silently never match. Emittable actor types are "
                                + EMITTABLE.stream().sorted().toList()
                                + "; put role names in intendedRoles instead.");
            }
            Set<String> misplaced = intendedRoles.stream().filter(EMITTABLE::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!misplaced.isEmpty()) {
                throw new IllegalArgumentException(
                        "Duty '" + description + "' records " + misplaced + " as intended-only, but those "
                                + "are emittable actor types — listing them there silently drops a gate "
                                + "the author expected to have. Move them to actorTypes.");
            }
        }

        /** Convenience for a duty with no unenforceable role distinction behind it. */
        public static Duty of(String description, Set<String> actorTypes) {
            return new Duty(description, actorTypes, Set.of());
        }
    }

    /**
     * Refuse the operation unless the trust context satisfies {@code duty}.
     *
     * <p>Call this before loading the target entity.</p>
     *
     * @throws ResponseStatusException 403, naming what is required and what was presented
     */
    public static void require(TrustContext ctx, Duty duty) {
        String presented = normalize(ctx == null ? null : ctx.actorType());
        if (duty.actorTypes().contains(presented)) {
            return;
        }
        String actorId = ctx == null ? null : ctx.actorId();
        log.warn("Refused {} — actor {} presents actorType '{}', which is not one of {}{}",
                duty.description(),
                actorId == null || actorId.isBlank() ? "<absent>" : actorId,
                presented.isEmpty() ? "<absent>" : presented,
                duty.actorTypes().stream().sorted().toList(),
                duty.intendedRoles().isEmpty() ? ""
                        : " (duty is really about " + duty.intendedRoles().stream().sorted().toList()
                                + ", which no header carries)");
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                capitalise(duty.description())
                        + " requires one of " + duty.actorTypes().stream().sorted().toList()
                        + "; this session presents "
                        + (presented.isEmpty() ? "no actor type" : "'" + presented + "'")
                        + ".");
    }

    /** Whether the trust context satisfies {@code duty}, without throwing. */
    public static boolean permits(TrustContext ctx, Duty duty) {
        return duty.actorTypes().contains(normalize(ctx == null ? null : ctx.actorType()));
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * Refuse the operation unless the trust context presents one of {@code allowed}.
     *
     * <p>Call this before loading the target entity.</p>
     *
     * @param ctx       the current trust context; a null context is refused, never waved through
     * @param allowed   a named constant set — never an inline {@code Set.of} at the call site
     * @param operation what is being refused, in words a refused operator can act on
     *                  (e.g. "posting a general-ledger journal")
     * @throws ResponseStatusException 403, naming the required actor types and the one presented
     */
    public static void require(TrustContext ctx, Set<String> allowed, String operation) {
        require(ctx == null ? null : ctx.actorType(),
                ctx == null ? null : ctx.actorId(),
                allowed, operation);
    }

    /**
     * Framework-agnostic overload for callers that hold the actor type without a {@link TrustContext}.
     *
     * @see #require(TrustContext, Set, String)
     */
    public static void require(String actorType, String actorId, Set<String> allowed, String operation) {
        String presented = normalize(actorType);
        if (!allowed.contains(presented)) {
            log.warn("Refused {} — actor {} presents actorType '{}', which is not one of {}",
                    operation,
                    actorId == null || actorId.isBlank() ? "<absent>" : actorId,
                    presented.isEmpty() ? "<absent>" : presented,
                    allowed.stream().sorted().toList());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    operation.substring(0, 1).toUpperCase(Locale.ROOT) + operation.substring(1)
                            + " requires one of " + allowed.stream().sorted().toList()
                            + "; this session presents "
                            + (presented.isEmpty() ? "no actor type" : "'" + presented + "'"));
        }
    }

    /** Whether the actor type is permitted, without throwing. For callers that must branch. */
    public static boolean permits(Set<String> allowed, String actorType) {
        return allowed.contains(normalize(actorType));
    }

    /** Trimmed and upper-cased against a fixed locale; null and blank both become "". */
    private static String normalize(String actorType) {
        return actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
    }
}
