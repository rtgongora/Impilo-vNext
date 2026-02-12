package zw.gov.mohcc.impilo.federation.error;

import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthorityViolationException;

import java.util.Map;

/**
 * Standard conflict handling for federation authority violations.
 *
 * Returns structured error envelopes with code FEDERATION_AUTHORITY_VIOLATION.
 *
 * Usage in service controllers:
 * <pre>
 *   if (!FederationAuthority.isNational(ctx.podId())) {
 *       return FederationConflictHandler.deny(ctx.podId(), "Patient merge", reqId, corrId);
 *   }
 * </pre>
 */
public final class FederationConflictHandler {

    private FederationConflictHandler() {
    }

    /**
     * Create a 403 error envelope for a federation authority violation.
     */
    public static ErrorEnvelope deny(String podId, String operation,
                                     String requestId, String correlationId) {
        return ErrorEnvelope.of(
                ErrorCodes.FEDERATION_AUTHORITY_VIOLATION,
                "Operation '" + operation + "' restricted to national pod; received pod=" + podId,
                Map.of("pod_id", podId, "operation", operation),
                requestId, correlationId);
    }

    /**
     * Assert national authority; throws if violated.
     *
     * @throws FederationAuthorityViolationException if pod is not national
     */
    public static void assertNational(String podId, String operation) {
        if (!FederationAuthority.isNational(podId)) {
            throw new FederationAuthorityViolationException(podId,
                    "Operation '" + operation + "' restricted to national pod; received pod=" + podId);
        }
    }
}
