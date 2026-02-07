package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import java.util.UUID;

/**
 * Response from verifying a signed audit export bundle.
 */
public record ExportVerificationResponse(
        UUID exportId,
        boolean intact,
        boolean signatureValid,
        int expectedEntryCount,
        long actualEntryCount,
        String message
) {
    public static ExportVerificationResponse success(UUID exportId, int entryCount) {
        return new ExportVerificationResponse(
                exportId, true, true, entryCount, entryCount,
                "Export bundle integrity and signature verified"
        );
    }

    public static ExportVerificationResponse hashMismatch(UUID exportId, int expectedCount, long actualCount) {
        return new ExportVerificationResponse(
                exportId, false, false, expectedCount, actualCount,
                "Export bundle hash mismatch or entry count discrepancy"
        );
    }

    public static ExportVerificationResponse notFound(UUID exportId) {
        return new ExportVerificationResponse(
                exportId, false, false, 0, 0,
                "Export bundle not found"
        );
    }
}
