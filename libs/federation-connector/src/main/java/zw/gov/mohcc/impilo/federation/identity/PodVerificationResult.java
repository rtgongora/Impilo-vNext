package zw.gov.mohcc.impilo.federation.identity;

/**
 * Result of pod identity verification.
 *
 * @param verified  true if the pod identity was confirmed
 * @param podId     the verified pod identifier (from certificate CN or JWT sub)
 * @param reason    human-readable reason (for logging/audit)
 */
public record PodVerificationResult(
        boolean verified,
        String podId,
        String reason
) {
    public static PodVerificationResult success(String podId) {
        return new PodVerificationResult(true, podId, "Identity verified");
    }

    public static PodVerificationResult failure(String reason) {
        return new PodVerificationResult(false, null, reason);
    }
}
