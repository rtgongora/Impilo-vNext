package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to update the status of a queue item.
 *
 * <p>Transitions the queue item through its lifecycle: WAITING, CALLED,
 * IN_TRIAGE, IN_SERVICE, PAUSED, COMPLETED, NO_SHOW, LEFT, or TRANSFERRED.</p>
 *
 * @param status the new status for the queue item
 */
public record QueueItemStatusRequest(
        @NotBlank String status
) {}
