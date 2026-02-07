package zw.gov.mohcc.impilo.shareslip.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of a share link claim attempt.
 *
 * @param success      whether the claim was successful
 * @param documentIds  the document IDs that were shared (only on success)
 * @param signedUrls   signed download URLs from landela-adapter (only on success)
 * @param message      human-readable status message
 */
public record ClaimResult(
        boolean success,
        List<UUID> documentIds,
        List<String> signedUrls,
        String message
) {
    public static ClaimResult success(List<UUID> documentIds, List<String> signedUrls) {
        return new ClaimResult(true, documentIds, signedUrls, "Share link claimed successfully");
    }

    public static ClaimResult failure(String message) {
        return new ClaimResult(false, null, null, message);
    }
}
