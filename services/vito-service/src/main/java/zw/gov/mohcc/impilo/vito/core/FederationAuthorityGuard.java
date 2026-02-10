package zw.gov.mohcc.impilo.vito.core;

import org.springframework.stereotype.Component;

/**
 * Federation authority guard for NATIONAL_SPINE_ONLY actions.
 *
 * Per v1.1 Companion Spec:
 *   Patient merge operations MUST only be executed by the "national" pod.
 *   Any non-national pod attempting a merge => 403 FEDERATION_NOT_AUTHORIZED.
 *
 * Usage:
 *   Inject into MergeService and call {@link #requireNationalPodForMerge(String)}
 *   before executing any merge operation under v1.1 routes.
 */
@Component
public class FederationAuthorityGuard {

    private static final String NATIONAL_POD = "national";

    /**
     * Assert that the given pod is authorized for federation-sensitive operations.
     *
     * @param podId the originating pod identifier from X-Pod-ID header
     * @throws FederationNotAuthorizedException if podId is not "national"
     */
    public void requireNationalPodForMerge(String podId) {
        if (!NATIONAL_POD.equals(podId)) {
            throw new FederationNotAuthorizedException(podId);
        }
    }

    /**
     * Exception thrown when a non-national pod attempts a national-only operation.
     */
    public static class FederationNotAuthorizedException extends RuntimeException {
        private final String podId;

        public FederationNotAuthorizedException(String podId) {
            super("FEDERATION_NOT_AUTHORIZED");
            this.podId = podId;
        }

        public String getPodId() {
            return podId;
        }
    }
}
