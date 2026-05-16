package zw.gov.mohcc.impilo.nhume.integration.trust;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

/**
 * Lightweight Trust Layer / TSHEPO guard.
 *
 * <p>Real Nhume deployments forward sensitive actions to TSHEPO's policy
 * decision point. This guard is the in-process surface every Nhume action
 * passes through, ensuring:
 *
 * <ul>
 *   <li>v1.2 mandatory headers are present (delegated to the V11HeaderFilter)</li>
 *   <li>Purpose-of-use is set for sensitive reads/writes</li>
 *   <li>Facility / workspace / shift context is captured when required</li>
 *   <li>Audit records have actor + correlation provenance</li>
 * </ul>
 */
@Component
public class TrustLayerGuard {

    @Value("${impilo.nhume.trust.enforce:true}")
    private boolean enforcePurposeOfUse;

    public ActorContext capture(HttpServletRequest request) {
        RequestContext base = RequestContextHolder.require();
        String actorId = request.getHeader(CompanionHeaders.ACTOR_ID);
        String actorType = request.getHeader(CompanionHeaders.ACTOR_TYPE);
        String purposeOfUse = request.getHeader(CompanionHeaders.PURPOSE_OF_USE);
        String facilityId = request.getHeader(CompanionHeaders.FACILITY_ID);
        String workspaceId = request.getHeader(CompanionHeaders.WORKSPACE_ID);
        String shiftId = request.getHeader(CompanionHeaders.SHIFT_ID);
        String deviceFingerprint = request.getHeader(CompanionHeaders.DEVICE_FINGERPRINT);
        return new ActorContext(base, actorId, actorType, purposeOfUse, facilityId, workspaceId,
                shiftId, deviceFingerprint);
    }

    /**
     * Require purpose-of-use for sensitive actions. Throws if missing and enforcement is enabled.
     */
    public void requirePurposeOfUse(ActorContext ctx, String action) {
        if (!enforcePurposeOfUse) return;
        if (ctx.purposeOfUse() == null || ctx.purposeOfUse().isBlank()) {
            throw new TrustDeniedException("PURPOSE_OF_USE_REQUIRED",
                    "Action requires X-Purpose-Of-Use header: " + action);
        }
    }

    /**
     * Require an authenticated actor (Actor ID + Actor Type). Throws if missing.
     */
    public void requireActor(ActorContext ctx, String action) {
        if (!enforcePurposeOfUse) return;
        if (ctx.actorId() == null || ctx.actorId().isBlank()
                || ctx.actorType() == null || ctx.actorType().isBlank()) {
            throw new TrustDeniedException("ACTOR_REQUIRED",
                    "Action requires X-Actor-ID and X-Actor-Type headers: " + action);
        }
    }

    public record ActorContext(
            RequestContext base,
            String actorId,
            String actorType,
            String purposeOfUse,
            String facilityId,
            String workspaceId,
            String shiftId,
            String deviceFingerprint
    ) {
    }

    public static class TrustDeniedException extends RuntimeException {
        private final String code;

        public TrustDeniedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}
