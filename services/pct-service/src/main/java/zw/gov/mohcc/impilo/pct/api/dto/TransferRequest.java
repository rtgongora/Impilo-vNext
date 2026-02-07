package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to transfer a patient between wards or facilities.
 *
 * <p>Supports both internal transfers (ward-to-ward within the same
 * facility) and external transfers (facility-to-facility). The
 * transfer type defaults to INTERNAL if not specified.</p>
 *
 * @param toWardId     the destination ward
 * @param toBedId      the destination bed; may be {@code null} for deferred assignment
 * @param transferType the type of transfer: INTERNAL or EXTERNAL (defaults to INTERNAL)
 */
public record TransferRequest(
        @NotNull UUID toWardId,
        UUID toBedId,
        String transferType
) {
    /**
     * Constructs a TransferRequest with a default transfer type of INTERNAL.
     */
    public TransferRequest {
        if (transferType == null || transferType.isBlank()) {
            transferType = "INTERNAL";
        }
    }
}
