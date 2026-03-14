package zw.gov.mohcc.impilo.offline.sdk.entitlement;

/**
 * Result of an offline entitlement verification attempt.
 *
 * @param valid       whether the token is valid
 * @param entitlement parsed entitlement (null if invalid)
 * @param reason      human-readable reason if invalid
 */
public record EntitlementVerificationResult(
        boolean valid,
        OfflineEntitlement entitlement,
        String reason
) {
    public static EntitlementVerificationResult success(OfflineEntitlement entitlement) {
        return new EntitlementVerificationResult(true, entitlement, null);
    }

    public static EntitlementVerificationResult denied(String reason) {
        return new EntitlementVerificationResult(false, null, reason);
    }
}
