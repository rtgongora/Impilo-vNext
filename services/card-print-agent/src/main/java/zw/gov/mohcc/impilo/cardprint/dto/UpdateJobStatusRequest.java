package zw.gov.mohcc.impilo.cardprint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating a print job status.
 *
 * @param status        the new status (RENDERING, RENDERED, PRINTING, PRINTED, COLLECTED, FAILED)
 * @param failureReason optional reason when status is FAILED
 * @param collectedBy   optional identifier of who collected the printed card
 */
public record UpdateJobStatusRequest(
        @NotBlank(message = "status is required")
        @Size(max = 30)
        String status,

        @Size(max = 1000)
        String failureReason,

        @Size(max = 255)
        String collectedBy
) {
}
