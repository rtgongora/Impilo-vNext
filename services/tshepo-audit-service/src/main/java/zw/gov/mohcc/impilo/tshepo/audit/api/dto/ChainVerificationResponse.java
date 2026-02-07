package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import java.util.UUID;

/**
 * Response from hash chain integrity verification.
 */
public record ChainVerificationResponse(
        UUID tenantId,
        boolean intact,
        long eventsVerified,
        Long brokenAtSequence,
        String expectedHash,
        String actualHash,
        String message
) {
    public static ChainVerificationResponse success(UUID tenantId, long eventsVerified) {
        return new ChainVerificationResponse(
                tenantId, true, eventsVerified, null, null, null,
                "Hash chain integrity verified: " + eventsVerified + " events checked"
        );
    }

    public static ChainVerificationResponse failure(UUID tenantId, long eventsVerified,
                                                      long brokenAtSequence,
                                                      String expectedHash, String actualHash) {
        return new ChainVerificationResponse(
                tenantId, false, eventsVerified, brokenAtSequence, expectedHash, actualHash,
                "Hash chain broken at sequence " + brokenAtSequence
        );
    }

    public static ChainVerificationResponse empty(UUID tenantId) {
        return new ChainVerificationResponse(
                tenantId, true, 0, null, null, null,
                "No audit events found for tenant"
        );
    }
}
