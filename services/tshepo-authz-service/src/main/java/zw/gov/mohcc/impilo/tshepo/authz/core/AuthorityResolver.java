package zw.gov.mohcc.impilo.tshepo.authz.core;

import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthorityBinding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link AuthorityBinding} for a request from <em>server-validated</em> evidence.
 *
 * <p>Doctrine (trust-plane §2): <em>"A role ≠ authority to perform an action"</em>,
 * <em>"Active work context (where you are) ≠ authority (what you may do there)"</em>, and
 * <em>"Selecting/entering a workplace ≠ being granted authority at that workplace"</em>.</p>
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code AuthorityBinding} is a Checkpoint 2 contract type with <strong>zero production
 * writers</strong>. Authority was only ever implicit — a role list on the request plus whatever a
 * policy rule happened to check. Nothing assembled a single answer to "what activated right is
 * being relied on, and is it still valid?", so nothing could audit or deny on it.</p>
 *
 * <h2>The one rule that must not be violated</h2>
 *
 * <p><strong>A work context does not create authority.</strong> Holding a usable duty token proves
 * <em>where</em> an actor is operating; it does not prove they may act. This resolver therefore
 * produces a binding that is only {@link #isActionable} when the duty token names an actual
 * appointment ({@code assignmentId}) — the activated right — and the provider is neither suspended
 * nor expired. A minted context with no appointment yields a binding that authorises nothing, and
 * {@code AuthorityResolverTest} asserts precisely that.</p>
 *
 * <h2>Evidence sources, and the gap</h2>
 *
 * <ul>
 *   <li>appointment / roles / provider — the introspected WORK_CONTEXT token
 *       ({@link DutyContext}), which is server-validated in a way a request header is not.</li>
 *   <li>suspension — {@code ProviderPrivilegeRevocationStore}, fed by VARAPI revocation events.</li>
 *   <li><b>expiry — UNAVAILABLE.</b> {@link DutyContext} carries no expiry claim, so
 *       {@code expiresAt} is null and {@link AuthorityBinding#isExpired} can never fire. The duty
 *       token's own lifetime is enforced at introspection ({@code usable()}), so an expired token
 *       is already rejected; what is missing is the expiry of the underlying <em>appointment or
 *       licence</em>. That is a real gap and is recorded rather than faked with a placeholder.</li>
 * </ul>
 */
public final class AuthorityResolver {

    /** No validated authority evidence at all — the common case for an unauthenticated request. */
    public static final AuthorityBinding NONE =
            AuthorityBinding.of("NONE", null, List.of(), null, null, null, null, false);

    private AuthorityResolver() {
    }

    /**
     * @param request      the request under evaluation
     * @param suspended    whether the provider's privilege is revoked (VARAPI), resolved by the
     *                     caller because the store is a Spring bean and this class stays pure
     */
    public static AuthorityBinding resolve(AuthzInternalRequest request, boolean suspended) {
        if (request == null) {
            return NONE;
        }
        DutyContext duty = request.dutyContext();
        if (duty == null || !duty.usable()) {
            // No validated context ⇒ no validated authority. Roles on the request are a claim,
            // not an activated right, and must not become one here.
            return NONE;
        }

        List<String> roles = new ArrayList<>();
        if (duty.role() != null && !duty.role().isBlank()) {
            roles.add(duty.role().trim());
        }

        return AuthorityBinding.of(
                duty.workMode() != null && !duty.workMode().isBlank() ? duty.workMode() : "WORK_CONTEXT",
                duty.assignmentId(),
                List.copyOf(roles),
                duty.providerId(),
                duty.assignmentId(),
                null,
                // See the class note: no appointment/licence expiry is available on the token.
                null,
                suspended);
    }

    /**
     * Whether this binding actually confers the right to act.
     *
     * <p>Deliberately stricter than "a binding exists". A context with no appointment, or an
     * appointment held by a suspended provider, is evidence of presence — not of permission.</p>
     */
    public static boolean isActionable(AuthorityBinding binding, Instant now) {
        return binding != null
                && binding.authorityId() != null && !binding.authorityId().isBlank()
                && !binding.suspended()
                && !binding.isExpired(now);
    }

    /** Closed-vocabulary label for metrics; never request-derived text. */
    public static String metricLabel(AuthorityBinding binding, Instant now) {
        if (binding == null || binding.authorityId() == null || binding.authorityId().isBlank()) {
            return "no_appointment";
        }
        if (binding.suspended()) {
            return "suspended";
        }
        if (binding.isExpired(now)) {
            return "expired";
        }
        return "actionable";
    }
}
