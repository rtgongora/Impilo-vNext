package zw.gov.mohcc.impilo.companion.federation;

import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;

/**
 * Federation authority helper utilities.
 *
 * Asserts that "national authoritative only" operations
 * are only performed by the national pod.
 *
 * Usage:
 * <pre>
 *   FederationAuthority.requireNational();
 *   // ... proceed with national-only operation
 * </pre>
 */
public final class FederationAuthority {

    private FederationAuthority() {
    }

    public static final String NATIONAL_POD_ID = "national";

    /**
     * Assert that the current request originates from the national pod.
     *
     * @throws FederationAuthorityViolationException if the pod is not national
     */
    public static void requireNational() {
        RequestContext ctx = RequestContextHolder.require();
        requireNational(ctx.podId());
    }

    /**
     * Assert that the given pod ID is the national pod.
     *
     * @throws FederationAuthorityViolationException if the pod is not national
     */
    public static void requireNational(String podId) {
        if (!isNational(podId)) {
            throw new FederationAuthorityViolationException(podId,
                    "Operation restricted to national pod; received pod=" + podId);
        }
    }

    /**
     * Check if a pod is the national pod.
     */
    public static boolean isNational(String podId) {
        return NATIONAL_POD_ID.equalsIgnoreCase(podId);
    }
}
