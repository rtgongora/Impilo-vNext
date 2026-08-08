package zw.gov.mohcc.impilo.shared.auth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * Observes — and, once a census supports it, enforces — the <em>duty</em> behind a consequential
 * write.
 *
 * <h2>The gap this addresses</h2>
 * <p>{@link ActorTypeGuard} can only ask "what kind of principal is this?" — a person, an operator,
 * a machine. It cannot ask "which duty do they hold?", so today <b>any {@code OPERATOR} can approve
 * any waiver or close any accounting period</b>. Its javadoc records why: no role header reaches a
 * service.</p>
 *
 * <h2>Why that is not the whole truth, and why this class can exist now</h2>
 * <p>No role <em>header</em> reaches a service — but the <b>JWT does carry roles</b>. Measured
 * 2026-08-08 against the live realm: {@code experience-ui}, {@code impilo-mobile-citizen},
 * {@code impilo-mobile-provider} and {@code impilo-backend} each carry an active
 * {@code oidc-usermodel-realm-role-mapper} writing {@code realm_access.roles} into the
 * <b>access token</b>, 44 realm users hold roles including {@code FINANCE}, and experience-bff
 * forwards the inbound user token downstream.</p>
 *
 * <p>That matters beyond convenience. {@code X-Actor-Type} is a <b>header any in-cluster caller can
 * set</b> — proven during this work, where a workload token asserting {@code X-Actor-Type: OPERATOR}
 * produced 19 successful writes across the estate. A realm role in the JWT is <b>signed and cannot
 * be forged</b>. Enforcing on the token is therefore strictly stronger than the actor-type gate.</p>
 *
 * <h2>Why it ships in SHADOW</h2>
 * <p>Enforcing a finance role on money without a census of who legitimately holds one is the exact
 * mistake already recorded against the P1 shadow work ("15% census, no duty token — do NOT enforce
 * on it"). So this evaluates, logs, and <b>changes no response</b> until {@link Mode#ENFORCE} is
 * deliberately set. The log lines ARE the census.</p>
 *
 * <h2>The two lanes, and why the distinction is the whole design</h2>
 * <ul>
 *   <li><b>MACHINE</b> — schedulers, outbox publishers and service-to-service calls. A
 *       {@code client_credentials} token has no user and therefore <b>no roles by construction</b>.
 *       Counting those as "would deny" would drown the census in false positives and make the
 *       would-deny rate meaningless.</li>
 *   <li><b>HUMAN</b> — anything asserting a person's actor type. This is where the question is
 *       real, and where a caller presenting <b>no roles at all</b> is the finding: it means human
 *       authority was asserted by a header on a token that carries no duty whatsoever.</li>
 * </ul>
 */
public final class DutyShadow {

    private static final Logger log = LoggerFactory.getLogger(DutyShadow.class);

    private DutyShadow() {
    }

    /** OFF: do nothing. SHADOW: observe and log. ENFORCE: refuse. Default is SHADOW. */
    public enum Mode {
        OFF, SHADOW, ENFORCE;

        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return SHADOW;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown duty mode '{}' — defaulting to SHADOW rather than OFF, because an "
                        + "unreadable setting must not silently disable a control", raw);
                return SHADOW;
            }
        }
    }

    /**
     * Realm roles treated as capable of a back-office money duty.
     *
     * <p>Taken from the live realm rather than invented: {@code FINANCE} (2 holders),
     * {@code SYSTEM_ADMIN} (3), {@code ADMIN} (4), {@code FACILITY_ADMIN} (3),
     * {@code NATIONAL_ADMINISTRATOR} (1). Deliberately wider than "finance" alone for the shadow —
     * the census exists to tell us whether it can be <b>narrowed</b>, and starting narrow would
     * report a would-deny rate that reflects the guess rather than the estate.</p>
     */
    public static final Set<String> FINANCE_CAPABLE = Set.of(
            "FINANCE", "SYSTEM_ADMIN", "ADMIN", "NATIONAL_ADMINISTRATOR", "FACILITY_ADMIN");

    /** Actor types that are machines. Kept in step with {@link ActorTypeGuard#MACHINE_ONLY}. */
    private static final Set<String> MACHINE_ACTOR_TYPES = ActorTypeGuard.MACHINE_ONLY;

    /** What was observed. Returned so a caller can assert on it in a test. */
    public record Observation(String lane, Set<String> roles, boolean wouldPermit, String subject) {
    }

    /**
     * Observe (and in {@link Mode#ENFORCE}, refuse) the duty behind {@code operation}.
     *
     * @param mode      the configured mode; never null
     * @param operation what is being attempted, in words (e.g. "approving a fee waiver")
     * @param path      the request path, for the census
     * @param actorType the presented actor type, used only to separate the machine lane
     * @return the observation, or null when {@link Mode#OFF}
     * @throws ResponseStatusException 403 only when {@link Mode#ENFORCE} and the duty is not met
     */
    public static Observation observe(Mode mode, String operation, String path, String actorType) {
        if (mode == Mode.OFF) {
            return null;
        }
        Jwt jwt = currentJwt();
        Set<String> roles = realmRoles(jwt);
        String subject = jwt == null ? "<no-token>"
                : String.valueOf(jwt.getClaims().getOrDefault("preferred_username", jwt.getSubject()));

        boolean machine = isMachine(actorType, subject, roles);
        String lane = machine ? "MACHINE" : "HUMAN";
        boolean wouldPermit = machine || !java.util.Collections.disjoint(roles, FINANCE_CAPABLE);

        if (!wouldPermit) {
            // The census line. One per refusal-that-would-have-happened, with everything needed to
            // decide whether FINANCE_CAPABLE can be narrowed and whether enforcing is safe.
            log.warn("DUTY_SHADOW mode={} lane={} would=DENY path={} operation=\"{}\" actor_type={} "
                            + "subject={} roles_presented={} requires_one_of={}",
                    mode, lane, path, operation, actorType == null ? "<absent>" : actorType,
                    subject, new TreeSet<>(roles), new TreeSet<>(FINANCE_CAPABLE));
        } else if (log.isInfoEnabled()) {
            log.info("DUTY_SHADOW mode={} lane={} would=PERMIT path={} actor_type={} subject={} "
                            + "roles_presented={}",
                    mode, lane, path, actorType == null ? "<absent>" : actorType, subject,
                    new TreeSet<>(roles));
        }

        if (mode == Mode.ENFORCE && !wouldPermit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    capitalise(operation) + " requires one of " + new TreeSet<>(FINANCE_CAPABLE)
                            + "; this session presents "
                            + (roles.isEmpty() ? "no duty role" : new TreeSet<>(roles).toString()) + ".");
        }
        return new Observation(lane, roles, wouldPermit, subject);
    }

    /**
     * A caller is a machine when it says so AND the token backs that up — a service account, or a
     * token with no user roles at all.
     *
     * <p>Both halves matter. Trusting the header alone would let the forgeable {@code X-Actor-Type}
     * move a caller into the lane that is never questioned, which is the hole this class exists to
     * measure. Trusting the token alone would misfile a genuine human whose realm happens to grant
     * them nothing.</p>
     */
    private static boolean isMachine(String actorType, String subject, Set<String> roles) {
        String presented = actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
        boolean claimsMachine = MACHINE_ACTOR_TYPES.contains(presented);
        boolean tokenIsServiceAccount = subject != null && subject.startsWith("service-account-");
        return claimsMachine && (tokenIsServiceAccount || roles.isEmpty());
    }

    private static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jat) {
            return jat.getToken();
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt j) {
            return j;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return Set.of();
        }
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> m)) {
            return Set.of();
        }
        Object roles = m.get("roles");
        if (!(roles instanceof Collection<?> c)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object r : c) {
            if (r != null) {
                out.add(r.toString());
            }
        }
        // Keycloak's default composite is noise in a duty census.
        out.removeAll(List.of("default-roles-impilo", "offline_access", "uma_authorization"));
        return out;
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? String.valueOf(s)
                : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
