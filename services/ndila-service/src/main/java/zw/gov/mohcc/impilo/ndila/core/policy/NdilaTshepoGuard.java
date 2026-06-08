package zw.gov.mohcc.impilo.ndila.core.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Single seam for Tshepo authorization inside Ndila.
 *
 * <p>Today this guard enforces tenant + actor-type + purpose-of-use +
 * sensitivity in process. The intent — captured here so a follow-on wave can
 * replace it without changing any caller — is for this guard to defer to the
 * Tshepo PolicyEngine through ext_authz for fine-grained policy decisions.
 *
 * <p>Sensitive Ndila operations always pass through this guard:
 * <ul>
 *   <li>live asset / staff / courier / inspector tracking</li>
 *   <li>client home address access</li>
 *   <li>outbreak-sensitive locations</li>
 *   <li>protected sites, controlled medicine delivery routes</li>
 *   <li>AI context packets that touch any of the above</li>
 *   <li>external commercial-provider calls for any sensitive workflow</li>
 * </ul>
 */
@Service
public class NdilaTshepoGuard {

    private static final Logger log = LoggerFactory.getLogger(NdilaTshepoGuard.class);

    private static final Set<String> EMERGENCY_PURPOSES = Set.of(
            "EMERGENCY_RESPONSE", "EMERGENCY_TREATMENT", "BREAK_GLASS");

    private static final Set<String> OPS_PURPOSES = Set.of(
            "OPERATIONS_MANAGEMENT", "DISPATCH", "INSPECTION",
            "PUBLIC_HEALTH_OPERATIONS", "OUTREACH", "DELIVERY",
            "IDENTITY_REGISTRATION", "REGISTRY_INTAKE");

    /**
     * Authorize a sensitivity-aware Ndila operation. Returns a {@link Decision}
     * that callers must respect.
     */
    public Decision authorize(NdilaOperationContext ctx, String operation) {
        if (ctx == null || ctx.tenantId() == null) {
            return Decision.deny("MISSING_TRUST_CONTEXT");
        }

        NdilaSensitivity sens = ctx.sensitivity();
        String purpose = ctx.purposeOfUse() == null ? "OPERATIONS_MANAGEMENT" : ctx.purposeOfUse();

        // Emergency-mode break-glass: always allowed (audited heavily).
        if (ctx.emergencyMode() && EMERGENCY_PURPOSES.contains(purpose)) {
            log.warn("Ndila Tshepo guard: break-glass authorization for op={} tenant={} actor={} purpose={}",
                    operation, ctx.tenantId(), ctx.actorId(), purpose);
            return Decision.allow("BREAK_GLASS", true);
        }

        // PUBLIC: always allowed.
        if (sens == NdilaSensitivity.PUBLIC) {
            return Decision.allow("PUBLIC", false);
        }

        // INTERNAL: requires an authenticated actor.
        if (sens == NdilaSensitivity.INTERNAL) {
            if (ctx.actorId() == null || ctx.actorType() == null) {
                return Decision.deny("UNAUTHENTICATED_ACTOR");
            }
            return Decision.allow("INTERNAL_AUTHENTICATED", false);
        }

        // RESTRICTED: requires a stated operational purpose.
        if (sens == NdilaSensitivity.RESTRICTED) {
            if (!OPS_PURPOSES.contains(purpose) && !EMERGENCY_PURPOSES.contains(purpose)) {
                return Decision.deny("PURPOSE_NOT_ALLOWED_FOR_RESTRICTED");
            }
            return Decision.allow("RESTRICTED_OPS_PURPOSE", false);
        }

        // HIGHLY_RESTRICTED: requires emergency context, explicit purpose and an authenticated actor.
        if (sens == NdilaSensitivity.HIGHLY_RESTRICTED) {
            if (!EMERGENCY_PURPOSES.contains(purpose)) {
                return Decision.deny("PURPOSE_NOT_ALLOWED_FOR_HIGHLY_RESTRICTED");
            }
            if (ctx.actorId() == null) {
                return Decision.deny("UNAUTHENTICATED_ACTOR");
            }
            return Decision.allow("HIGHLY_RESTRICTED_EMERGENCY", true);
        }

        return Decision.deny("UNKNOWN_SENSITIVITY");
    }

    public record Decision(boolean allowed, String reason, boolean breakGlass) {
        public static Decision allow(String reason, boolean breakGlass) {
            return new Decision(true, reason, breakGlass);
        }
        public static Decision deny(String reason) {
            return new Decision(false, reason, false);
        }
    }
}
