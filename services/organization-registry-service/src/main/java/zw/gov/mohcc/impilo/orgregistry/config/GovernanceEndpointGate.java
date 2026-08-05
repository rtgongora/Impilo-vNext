package zw.gov.mohcc.impilo.orgregistry.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Server-side gate for the trust-domain governance endpoints (Wave 1B).
 *
 * <p><strong>Why this exists.</strong> The governance API derives its actor and scope from
 * {@code TrustContextHolder}, which {@code TrustContextFilter} populates from request
 * <em>headers</em>. Those headers are only trustworthy when Envoy's ext_authz has
 * stripped and re-injected them after a PDP decision. In this estate
 * {@code extAuthz.enabled: false} — measured in
 * {@code deploy/helm/impilo-vnext/values-full-preview.yaml} — so a merely authenticated
 * caller could present any {@code X-Actor-ID} they liked and the service would believe it.
 *
 * <p>Shipping governance <em>writes</em> under those conditions would mean an authenticated
 * user could accredit a trust domain, activate a responsibility profile or approve a
 * membership as somebody else. So the endpoints are off unless two independent conditions
 * are both explicitly true — one saying the capability is wanted, the other asserting that
 * the enforcement path it depends on is actually live.
 *
 * <p>Two flags rather than one, deliberately. A single {@code enabled=true} is the kind of
 * thing that gets set in a values file to make a smoke test pass. Requiring a separate,
 * differently-named assertion that ext_authz is enforcing makes switching this on a
 * deliberate statement about the estate rather than a convenience.
 *
 * <p>When the gate is shut the endpoints answer <strong>503 with a reason</strong>. Not 404,
 * which would imply they do not exist; not 200 with an empty body, which would imply the
 * operation succeeded and did nothing.
 */
@Component
public class GovernanceEndpointGate {

    private static final Logger log = LoggerFactory.getLogger(GovernanceEndpointGate.class);

    private final boolean enabled;
    private final boolean extAuthzVerified;

    public GovernanceEndpointGate(
            @Value("${impilo.governance.trust-domain.enabled:false}") boolean enabled,
            @Value("${impilo.governance.trust-domain.ext-authz-verified:false}") boolean extAuthzVerified) {
        this.enabled = enabled;
        this.extAuthzVerified = extAuthzVerified;
        if (enabled && !extAuthzVerified) {
            // Loud, because this is somebody switching the capability on without
            // asserting the control it depends on.
            log.warn("Trust-domain governance endpoints are enabled but ext-authz-verified is "
                    + "false. They remain CLOSED: actor and scope arrive on headers, and without "
                    + "ext_authz nothing verifies them.");
        }
        if (open()) {
            log.info("Trust-domain governance endpoints are OPEN; ext_authz is asserted to be "
                    + "enforcing. Actor and scope are taken from the verified trust context only.");
        }
    }

    /** Both conditions, or the endpoints stay shut. */
    public boolean open() {
        return enabled && extAuthzVerified;
    }

    /** Why they are shut, in terms an operator can act on. */
    public String closedReason() {
        if (!enabled) {
            return "Trust-domain governance is not enabled on this deployment "
                    + "(impilo.governance.trust-domain.enabled=false).";
        }
        return "Trust-domain governance is enabled but the ext_authz enforcement path is not "
                + "asserted (impilo.governance.trust-domain.ext-authz-verified=false). Actor and "
                + "scope arrive on request headers; without ext_authz stripping and re-injecting "
                + "them after a PDP decision, a merely authenticated caller could assert any actor.";
    }

    public String requiredAction() {
        return "Enable Envoy ext_authz for this route, confirm the PDP is deciding, then set both "
                + "impilo.governance.trust-domain.enabled and "
                + "impilo.governance.trust-domain.ext-authz-verified to true.";
    }
}
